package com.kabigonis.sorairo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** User-consented, no-audio screen recording through Android MediaProjection. */
class ScreenRecordingService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var legacyOutput: File? = null
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            startCapture()
        } catch (_: Exception) {
            stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(0)
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val width = (metrics.widthPixels / 2) * 2
        val height = (metrics.heightPixels / 2) * 2
        val dpi = metrics.densityDpi

        val name = "Sorairo-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.mp4"
        prepareOutput(name)
        val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        recorder = media
        media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        media.setVideoSize(width, height)
        media.setVideoFrameRate(30)
        media.setVideoEncodingBitRate(5_000_000)
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = outputUri ?: throw IllegalStateException("Output URI missing")
            retainedPfd = contentResolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("Could not open recording file")
            media.setOutputFile(retainedPfd!!.fileDescriptor)
        } else {
            media.setOutputFile(legacyOutput?.absolutePath ?: throw IllegalStateException("Output path missing"))
        }
        media.prepare()

        val surface: Surface = media.surface
        val mp = projection ?: throw IllegalStateException("Projection permission missing")
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopRecording() }
        }, null)
        virtualDisplay = mp.createVirtualDisplay(
            "Sorairo Screen Recording", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
        media.start()
        updateNotification("Recording screen · tap Stop to finish")
    }

    private fun prepareOutput(name: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Sorairo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create recording file")
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            dir.mkdirs()
            legacyOutput = File(dir, name)
        }
    }

    private var retainedPfd: android.os.ParcelFileDescriptor? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String = "Screen recording is ready"): Notification {
        val stop = Intent(this, ScreenRecordingService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Sorairo screen recorder")
                .setContentText(text)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", pending).build())
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Sorairo screen recorder")
                .setContentText(text)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", pending)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun stopRecording() {
        if (stopped) return
        stopped = true
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) { }
        recorder = null
        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
        try { retainedPfd?.close() } catch (_: Exception) { }
        retainedPfd = null
        if (Build.VERSION.SDK_INT >= 29) {
            outputUri?.let { uri ->
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                try { contentResolver.update(uri, values, null, null) } catch (_: Exception) { }
            }
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val ACTION_STOP = "com.kabigonis.sorairo.STOP_SCREEN_RECORDING"
        private const val CHANNEL_ID = "sorairo_screen_recording"
        private const val NOTIFICATION_ID = 701
    }
}
