package tech.future.sleepanalyzer.audio.analysis

import kotlin.math.roundToInt
import kotlin.math.sqrt

class BreathingExtractor(private val rmsHz: Float = 10f) {
    fun extract(rmsSeries: FloatArray): Pair<Float?, Float?> {
        if (rmsSeries.isEmpty() || rmsHz <= 0f) return null to null

        val smoothed = movingAverage(rmsSeries, rmsHz.roundToInt().coerceAtLeast(1))
        if (smoothed.size < 4) return null to null

        var mean = 0f
        for (value in smoothed) mean += value
        mean /= smoothed.size

        val centered = FloatArray(smoothed.size)
        var totalEnergy = 0.0
        for (i in smoothed.indices) {
            val sample = smoothed[i] - mean
            centered[i] = sample
            totalEnergy += sample.toDouble() * sample
        }
        if (totalEnergy <= 1e-6) return null to null

        val minLag = (rmsHz * 60f / 30f).roundToInt().coerceAtLeast(1)
        val maxLag = (rmsHz * 60f / 6f).roundToInt().coerceAtMost(centered.lastIndex)
        if (minLag >= maxLag) return null to null

        var bestLag = -1
        var bestCorrelation = 0f
        for (lag in minLag..maxLag) {
            var numerator = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            val limit = centered.size - lag
            for (i in 0 until limit) {
                val left = centered[i].toDouble()
                val right = centered[i + lag].toDouble()
                numerator += left * right
                leftEnergy += left * left
                rightEnergy += right * right
            }
            if (leftEnergy <= 1e-6 || rightEnergy <= 1e-6) continue

            val correlation = (numerator / sqrt(leftEnergy * rightEnergy))
                .toFloat()
                .coerceIn(-1f, 1f)
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }

        val regularity = bestCorrelation.coerceIn(0f, 1f)
        if (bestLag <= 0 || regularity < 0.3f) return null to null

        return (rmsHz * 60f / bestLag) to regularity
    }

    private fun movingAverage(input: FloatArray, windowSize: Int): FloatArray {
        val output = FloatArray(input.size)
        var sum = 0.0
        for (i in input.indices) {
            sum += input[i]
            if (i >= windowSize) sum -= input[i - windowSize]
            val count = minOf(i + 1, windowSize)
            output[i] = (sum / count).toFloat()
        }
        return output
    }
}
