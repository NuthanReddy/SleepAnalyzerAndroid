package tech.future.sleepanalyzer.audio.isolation

import tech.future.sleepanalyzer.audio.FeatureVector
import tech.future.sleepanalyzer.audio.processing.AudioFeatures
import tech.future.sleepanalyzer.data.db.entity.VoiceProfile
import kotlin.math.sqrt

/**
 * Aggregates feature vectors from a short enrollment session into a [VoiceProfile].
 * Discards silence frames automatically.
 */
class VoiceProfileBuilder(
    private val sampleRate: Int,
    private val fftSize: Int = 1024,
    private val bands: Int = 13
) {
    private val features = AudioFeatures(sampleRate, fftSize, bands)
    private val scratch = FeatureVector(bandEnergies = FloatArray(bands))
    private val bandSum = DoubleArray(bands)
    private val bandSumSq = DoubleArray(bands)
    private val pitchSum = mutableListOf<Float>()
    private val centroidSum = mutableListOf<Float>()
    private var zcrSum = 0.0
    private var rmsSum = 0.0
    private var count = 0

    fun feed(samples: ShortArray, sampleCountPerWindow: Int = fftSize / 2) {
        var offset = 0
        while (offset + sampleCountPerWindow <= samples.size) {
            features.extract(samples, offset, sampleCountPerWindow, scratch)
            if (scratch.rms < 200f) { offset += sampleCountPerWindow; continue }
            for (b in bandSum.indices) {
                val v = scratch.bandEnergies[b]
                bandSum[b] += v
                bandSumSq[b] += v.toDouble() * v
            }
            if (scratch.pitchHz > 30f) pitchSum += scratch.pitchHz
            if (scratch.spectralCentroid > 0f) centroidSum += scratch.spectralCentroid
            zcrSum += scratch.zeroCrossingRate
            rmsSum += scratch.rms
            count++
            offset += sampleCountPerWindow
        }
    }

    fun build(filePath: String? = null, label: String = "me"): VoiceProfile {
        if (count == 0) {
            return VoiceProfile(label = label, sampleFilePath = filePath)
        }
        val meanBands = FloatArray(bandSum.size) { (bandSum[it] / count).toFloat() }
        val pitchMean = if (pitchSum.isNotEmpty()) pitchSum.average().toFloat() else 0f
        val pitchStd = if (pitchSum.size > 1) {
            val m = pitchMean
            sqrt(pitchSum.sumOf { val d = it - m; (d * d).toDouble() } / pitchSum.size).toFloat()
        } else 25f
        val centroidMean = if (centroidSum.isNotEmpty()) centroidSum.average().toFloat() else 0f
        val centroidStd = if (centroidSum.size > 1) {
            val m = centroidMean
            sqrt(centroidSum.sumOf { val d = it - m; (d * d).toDouble() } / centroidSum.size).toFloat()
        } else 200f
        val zcrMean = (zcrSum / count).toFloat()
        val rmsMean = (rmsSum / count).toFloat()
        return VoiceProfile(
            label = label,
            sampleFilePath = filePath,
            pitchMeanHz = pitchMean,
            pitchStdHz = pitchStd.coerceAtLeast(10f),
            spectralCentroidMean = centroidMean,
            spectralCentroidStd = centroidStd,
            zeroCrossingRate = zcrMean,
            rmsMean = rmsMean,
            bandEnergyMeans = meanBands.joinToString("|") { "%.4f".format(it) }
        )
    }

    fun reset() {
        for (i in bandSum.indices) { bandSum[i] = 0.0; bandSumSq[i] = 0.0 }
        pitchSum.clear(); centroidSum.clear()
        zcrSum = 0.0; rmsSum = 0.0; count = 0
    }
}
