package com.shaurya.glyphcallwave

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scrolling oscilloscope-style trace: each pushed level becomes one column, columns shift
 * left as new ones arrive, adjacent columns are connected so it reads as a continuous stroke.
 */
class ScrollingWaveformRenderer(private val size: Int = 25) {

    private val columns = IntArray(size) { size / 2 }
    private var smoothed = 0f

    fun pushLevel(level: Float) {
        smoothed += (level - smoothed) * 0.6f
        val half = size / 2
        val y = (half + smoothed * (half - 1)).roundToInt().coerceIn(0, size - 1)
        System.arraycopy(columns, 1, columns, 0, size - 1)
        columns[size - 1] = y
    }

    fun render(): IntArray {
        val frame = IntArray(size * size)
        for (x in 0 until size) {
            val y1 = columns[x]
            val y0 = if (x > 0) columns[x - 1] else y1
            val steps = maxOf(abs(y1 - y0), 1)
            for (s in 0..steps) {
                val yy = y0 + (y1 - y0) * s / steps
                for (row in 0 until size) {
                    val dist = abs(row - yy)
                    val brightness = (255 - dist * 90).coerceIn(0, 255)
                    val idx = row * size + x
                    if (brightness > frame[idx]) frame[idx] = brightness
                }
            }
        }
        return frame
    }
}
