package com.shaurya.glyphcallwave

import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Procedural "breathing" wave shown while the phone is ringing, before there's any
 * mic signal to visualize (call isn't connected yet).
 */
class RingWaveformRenderer(private val size: Int = 25) {

    private var phase = 0.0
    private val amplitude = 5.0
    private val speed = 0.25

    fun nextFrame(): IntArray {
        val frame = IntArray(size * size)
        phase += speed
        val half = size / 2
        for (x in 0 until size) {
            val y = (half + amplitude * sin((x * 0.6) + phase)).roundToInt().coerceIn(0, size - 1)
            for (row in 0 until size) {
                val dist = kotlin.math.abs(row - y)
                val brightness = (255 - dist * 70).coerceIn(0, 255)
                if (brightness > 0) frame[row * size + x] = brightness
            }
        }
        return frame
    }
}
