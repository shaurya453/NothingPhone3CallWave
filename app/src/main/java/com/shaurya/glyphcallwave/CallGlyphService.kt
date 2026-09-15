package com.shaurya.glyphcallwave

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Foreground service that watches call state and drives the Glyph Matrix accordingly:
 * a procedural "breathing" wave while ringing (no audio yet), then a real scrolling
 * waveform driven by the caller's own mic uplink once the call connects.
 */
class CallGlyphService : Service() {

    private lateinit var glyphMatrixManager: GlyphMatrixManager
    private var glyphReady = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ringRenderer = RingWaveformRenderer()
    private val waveRenderer = ScrollingWaveformRenderer()
    private var voiceCapture: VoiceWaveformCapture? = null

    private var ringing = false
    private val ringTick = object : Runnable {
        override fun run() {
            if (!ringing) return
            pushFrame(ringRenderer.nextFrame())
            mainHandler.postDelayed(this, 66L)
        }
    }

    private lateinit var telephonyManager: TelephonyManager
    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> onRinging()
                TelephonyManager.CALL_STATE_OFFHOOK -> onConnected()
                TelephonyManager.CALL_STATE_IDLE -> onIdle()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: ComponentName?) {
                glyphMatrixManager.register(Glyph.DEVICE_23112)
                glyphReady = true
            }

            override fun onServiceDisconnected(componentName: ComponentName?) {
                glyphReady = false
            }
        })

        telephonyManager = getSystemService(TelephonyManager::class.java)
        telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
    }

    private fun onRinging() {
        voiceCapture?.stop()
        voiceCapture = null
        ringing = true
        mainHandler.post(ringTick)
    }

    private fun onConnected() {
        ringing = false
        mainHandler.removeCallbacks(ringTick)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // no mic access granted; matrix simply stays on the last ring frame
        }

        val capture = VoiceWaveformCapture { level ->
            waveRenderer.pushLevel(level)
            val frame = waveRenderer.render()
            mainHandler.post { pushFrame(frame) }
        }
        voiceCapture = capture
        capture.start()
    }

    private fun onIdle() {
        ringing = false
        mainHandler.removeCallbacks(ringTick)
        voiceCapture?.stop()
        voiceCapture = null
        if (glyphReady) glyphMatrixManager.closeAppMatrix()
    }

    private fun pushFrame(frame: IntArray) {
        if (glyphReady) glyphMatrixManager.setAppMatrixFrame(frame)
    }

    private fun buildNotification(): Notification {
        val channelId = "call_glyph_wave"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
        mainHandler.removeCallbacks(ringTick)
        voiceCapture?.stop()
        if (glyphReady) glyphMatrixManager.closeAppMatrix()
        glyphMatrixManager.unInit()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
