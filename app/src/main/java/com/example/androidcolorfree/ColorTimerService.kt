package com.jzhuang.colorfree

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ColorTimerService : Service() {

    private var timer: CountDownTimer? = null
    private lateinit var settingsObserver: ContentObserver
    private var ignoreNextChange = false 

    sealed class TimerState {
        object Idle : TimerState()
        data class Running(val timeLeftMs: Long, val totalDurationMs: Long) : TimerState()
    }

    companion object {
        const val ACTION_START_TIMER = "ACTION_START_TIMER"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val NOTIFICATION_CHANNEL_ID = "color_timer_enforcement_channel"
        const val NOTIFICATION_ID = 100 // Must be a unique integer > 0

        private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
        val timerState = _timerState.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val notification = createForegroundNotification()
        // CORRECTED: Simplified startForeground call. Flags should be in manifest.
        startForeground(NOTIFICATION_ID, notification)

        settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (ignoreNextChange) {
                    ignoreNextChange = false
                    return
                }
                if (_timerState.value is TimerState.Idle) {
                    val isDaltonizerEnabled = Settings.Secure.getInt(contentResolver, "accessibility_display_daltonizer_enabled", 0) == 1
                    val daltonizerMode = Settings.Secure.getInt(contentResolver, "accessibility_display_daltonizer", -1)

                    if (!isDaltonizerEnabled || daltonizerMode != 0) {
                        setGrayscale(true)
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("accessibility_display_daltonizer_enabled"),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("accessibility_display_daltonizer"),
            false,
            settingsObserver
        )

        setGrayscale(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TIMER) {
            val duration = intent.getLongExtra(EXTRA_DURATION_MS, 300000L)
            startTimer(duration)
        } else if (_timerState.value is TimerState.Idle) {
            setGrayscale(true)
        }
        return START_STICKY
    }

    private fun startTimer(durationMs: Long) {
        timer?.cancel()
        setGrayscale(false)
        timer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerState.value = TimerState.Running(millisUntilFinished, durationMs)
            }

            override fun onFinish() {
                stopTimer()
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        setGrayscale(true)
    }

    private fun setGrayscale(enabled: Boolean) {
        ignoreNextChange = true 
        GrayscaleHelper.setGrayscale(this, enabled)
        if (enabled) {
            _timerState.value = TimerState.Idle
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(settingsObserver)
        setGrayscale(true)
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        val channelName = "Grayscale Enforcement"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance)
        channel.setSound(null, null)
        channel.enableVibration(false)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ColorFreedom Active")
            .setContentText("Enforcing grayscale mode.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setSilent(true) 
            .setOngoing(true) 
            .build()
    }
}