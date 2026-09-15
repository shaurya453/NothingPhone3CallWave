package com.shaurya.glyphcallwave

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

/**
 * Captures the caller's own mic uplink during an active call (VOICE_COMMUNICATION source,
 * not VOICE_CALL/CAPTURE_AUDIO_OUTPUT) and reports a smoothed, auto-gained signed level
 * ~15 times a second via [onLevel].
 */
class VoiceWaveformCapture(private val onLevel: (Float) -> Unit) {

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    private val sampleRate = 8000

    @SuppressLint("MissingPermission") // caller must verify RECORD_AUDIO before calling start()
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        audioRecord = record
        record.startRecording()
        running = true

        thread = Thread {
            val chunk = ShortArray(sampleRate / 15)
            var runningMax = 500f
            while (running) {
                val n = record.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                var peakSigned = 0
                for (i in 0 until n) {
                    val v = chunk[i].toInt()
                    if (abs(v) > abs(peakSigned)) peakSigned = v
                }
                runningMax = maxOf(abs(peakSigned).toFloat(), runningMax * 0.98f, 500f)
                onLevel((peakSigned / runningMax).coerceIn(-1f, 1f))
            }
        }.apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(200)
        thread = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
    }
}
