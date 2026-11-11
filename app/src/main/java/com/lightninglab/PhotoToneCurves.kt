package com.lightninglab

/**
 * Three independent 5-point RGB tone curves. X coordinates are fixed at 0, .25, .5, .75, 1;
 * each stored value is the output Y in [0,1]. A 256-entry LUT keeps pixel processing inexpensive.
 */
object PhotoToneCurves {
    const val CHANNELS = 3
    const val POINTS = 5

    fun identity(): List<Float> = List(CHANNELS * POINTS) { (it % POINTS) / 4f }

    fun isIdentity(values: List<Float>): Boolean {
        if (values.size != CHANNELS * POINTS) return false
        return values.indices.all { kotlin.math.abs(values[it] - (it % POINTS) / 4f) < 0.0001f }
    }

    fun lut(values: List<Float>, channel: Int): IntArray {
        require(channel in 0 until CHANNELS)
        require(values.size == CHANNELS * POINTS)
        val points = (0 until POINTS).map { values[channel * POINTS + it].coerceIn(0f, 1f) }
        return IntArray(256) { input ->
            val x = input / 255f
            val segment = minOf(POINTS - 2, (x * (POINTS - 1)).toInt())
            val local = (x * (POINTS - 1) - segment).coerceIn(0f, 1f)
            (points[segment] + (points[segment + 1] - points[segment]) * local)
                .times(255f).toInt().coerceIn(0, 255)
        }
    }
}
