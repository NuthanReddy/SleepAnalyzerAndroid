package tech.future.sleepanalyzer.sleep

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Reference-counted shared sensor listener that pipes fused motion samples into a singleton [MotionBuffer].
 *
 * Why: both SleepTrackingService (long-running, full session) and SmartWakeService (short, during wake window)
 * need motion data. Re-registering the listener twice is wasteful and can desync gravity removal.
 * MotionMonitor.start()/stop() is refcounted so the listeners are only registered while at least one
 * consumer is active.
 *
 * The accelerometer (linear-acceleration preferred) drives the sample cadence; the optional
 * gyroscope / ambient-light / step-counter sensors are latched and folded into each emitted
 * [MotionSample], giving the motion-only estimator a more robust movement signal. Every secondary
 * sensor is guarded for absence so low-end devices still work with accelerometer only.
 */
object MotionMonitor {

    val buffer: MotionBuffer = MotionBuffer(capacity = 8 * 60 * 60)  // up to ~8h at 1Hz aggregated

    private val refs = AtomicInteger(0)
    val refCount: Int get() = refs.get()
    @Volatile private var sensorManager: SensorManager? = null
    @Volatile private var listener: Listener? = null

    /** Increment refcount; first caller registers the listeners. Returns true if the sensor is now active. */
    @Synchronized
    fun start(context: Context): Boolean {
        if (refs.getAndIncrement() == 0) {
            val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager = sm
            val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            // The accelerometer drives the sample cadence; without it there is no channel to fold the
            // other sensors into, so we cannot start. Undo the refcount bump so a later capable start works.
            val motion = linear ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (motion == null) {
                refs.decrementAndGet()
                sensorManager = null
                return false
            }
            val l = Listener(usesLinearAcceleration = linear != null)
            listener = l
            sm.registerListener(l, motion, SensorManager.SENSOR_DELAY_NORMAL)
            // Optional secondary sensors — each guarded for absence.
            sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
                sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
                sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        return listener != null
    }

    /** Decrement refcount; last caller unregisters. */
    @Synchronized
    fun stop() {
        if (refs.decrementAndGet() <= 0) {
            refs.set(0)
            listener?.let { sensorManager?.unregisterListener(it) }
            listener = null
            sensorManager = null
        }
    }

    /** Manual write path - used by SleepTrackingService when it already has the sensor open. */
    fun publish(sample: MotionSample) = buffer.add(sample)

    private class Listener(private val usesLinearAcceleration: Boolean) : SensorEventListener {
        private val gravity = FloatArray(3)
        // Latest secondary-sensor readings, latched so each accelerometer sample carries the most
        // recent gyro/light values. A single listener's callbacks are serialized on one thread, so
        // plain fields are safe here.
        private var latestGyro = 0f
        private var latestLux: Float? = null
        private var lastStepCount = -1f
        private var pendingSteps = 0

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                    latestGyro = sqrt(x * x + y * y + z * z)
                }
                Sensor.TYPE_LIGHT -> latestLux = event.values[0]
                Sensor.TYPE_STEP_COUNTER -> {
                    // Cumulative steps since boot; accumulate the delta until the next motion sample.
                    val total = event.values[0]
                    if (lastStepCount >= 0f && total >= lastStepCount) {
                        pendingSteps += (total - lastStepCount).toInt()
                    }
                    lastStepCount = total
                }
                else -> emitMotion(event)  // linear-acceleration / accelerometer
            }
        }

        private fun emitMotion(event: SensorEvent) {
            val mag = if (usesLinearAcceleration) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                sqrt(x * x + y * y + z * z)
            } else {
                val alpha = 0.8f
                for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
                val lx = event.values[0] - gravity[0]
                val ly = event.values[1] - gravity[1]
                val lz = event.values[2] - gravity[2]
                sqrt(lx * lx + ly * ly + lz * lz)
            }
            val steps = pendingSteps
            pendingSteps = 0
            buffer.add(
                MotionSample(
                    timestampMs = System.currentTimeMillis(),
                    magnitude = mag,
                    gyroMagnitude = latestGyro,
                    lightLux = latestLux,
                    stepDelta = steps
                )
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
