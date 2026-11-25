package com.baca.app.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow

internal object ReadAloudController {
    val paused = MutableStateFlow(false)
    var title: String = "Reading aloud"
    var onExternalStop: (() -> Unit)? = null
    var onInterruptForPause: (() -> Unit)? = null
}

class ReadAloudService : Service() {
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ReadAloudController.paused.value = false
                ReadAloudController.onExternalStop?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                val nowPaused = !ReadAloudController.paused.value
                ReadAloudController.paused.value = nowPaused
                if (nowPaused) ReadAloudController.onInterruptForPause?.invoke()
                showNotification()
                return START_NOT_STICKY
            }
            else -> {
                intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    ReadAloudController.title = it
                }
                ReadAloudController.paused.value = false
                requestAudioFocus()
                showNotification()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
        super.onDestroy()
    }

    private fun showNotification() {
        ensureChannel()
        val paused = ReadAloudController.paused.value
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ReadAloudService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ReadAloudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle(ReadAloudController.title)
            .setContentText(if (paused) "Paused" else "Reading aloud")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(launchIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (paused) "Resume" else "Pause",
                    toggleIntent,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop",
                    stopIntent,
                ).build(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun requestAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        ReadAloudController.onExternalStop?.invoke()
                        stopSelf()
                    } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        ReadAloudController.paused.value = true
                        ReadAloudController.onInterruptForPause?.invoke()
                        showNotification()
                    }
                }
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun abandonAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
    }

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 4102
        const val EXTRA_TITLE = "title"
        private const val ACTION_TOGGLE_PAUSE = "com.baca.app.reader.READ_ALOUD_TOGGLE_PAUSE"
        private const val ACTION_STOP = "com.baca.app.reader.READ_ALOUD_STOP"

        fun start(context: Context, title: String) {
            val intent = Intent(context, ReadAloudService::class.java).putExtra(EXTRA_TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReadAloudService::class.java))
        }
    }
}
