package com.kabigonis.sorairo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.IntentCompat
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real screen recording for the tray's record icon.
 *
 * Android makes every app ask for permission each time (MediaProjection consent), so the flow is:
 * tray tap -> system consent dialog -> this foreground service records video to Movies/Sorairo.
 * Tapping the icon again (or the notification) stops it. Video only; no audio capture.
 */
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outUri: Uri? = null
    private var outFile: File? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
            else -> if (!recording) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (recording) stopRecording()
        super.onDestroy()
    }

    private fun enterForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Sorairo is recording the screen")
            .setContentText("Tap to stop")
            .setOngoing(true)
            .setContentIntent(stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(intent: Intent) {
        if (recording) return
        // Must be foreground (with the mediaProjection type) before the projection token is used.
        enterForeground()
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
        if (data == null) {
            fail("Screen recording was not allowed")
            return
        }
        val w = intent.getIntExtra(EXTRA_W, 1280)
        val h = intent.getIntExtra(EXTRA_H, 720)
        val dpi = intent.getIntExtra(EXTRA_DPI, 320)
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("no projection")
            projection = proj
            // Android 14+ requires a callback to be registered before the virtual display exists.
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { if (recording) stopRecording() }
                }
            }
            projectionCallback = callback
            proj.registerCallback(callback, main)

            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = rec
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoEncodingBitRate(8_000_000)
            rec.setVideoFrameRate(30)
            rec.setVideoSize(w, h)
            openOutput(rec)
            rec.prepare()
            display = proj.createVirtualDisplay(
                "sorairo-record", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface, null, null
            )
            rec.start()
            recording = true
            notifyChange()
        } catch (_: Exception) {
            fail("Couldn't start screen recording")
        }
    }

    private fun openOutput(rec: MediaRecorder) {
        val name = "Sorairo-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Sorairo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("no uri")
            outUri = uri
            val fd = contentResolver.openFileDescriptor(uri, "w") ?: throw IllegalStateException("no fd")
            pfd = fd
            rec.setOutputFile(fd.fileDescriptor)
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val file = File(dir, name)
            outFile = file
            rec.setOutputFile(file.absolutePath)
        }
    }

    private fun stopRecording() {
        val wasRecording = recording
        var saved = false
        if (wasRecording) {
            try {
                recorder?.stop()
                saved = true
            } catch (_: RuntimeException) {
                // stop() throws if almost nothing was recorded; the file is discarded below
            }
        }
        recording = false
        release(saved)
        notifyChange()
        if (wasRecording) toast(if (saved) "Screen recording saved to Movies/Sorairo" else "Recording was too short to save")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        recording = false
        release(false)
        notifyChange()
        toast(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Tears everything down and either publishes or deletes the output file. */
    private fun release(saved: Boolean) {
        try { display?.release() } catch (_: Exception) { }
        display = null
        try { recorder?.reset() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        try { pfd?.close() } catch (_: Exception) { }
        pfd = null
        projection?.let { proj ->
            projectionCallback?.let { try { proj.unregisterCallback(it) } catch (_: Exception) { } }
            try { proj.stop() } catch (_: Exception) { }
        }
        projection = null
        projectionCallback = null
        val uri = outUri
        if (uri != null) {
            try {
                if (saved) {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } catch (_: Exception) { }
        }
        outUri = null
        val file = outFile
        if (file != null && !saved) file.delete()
        outFile = null
    }

    private fun toast(message: String) {
        main.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    private fun notifyChange() {
        val activity = listener?.get() ?: return
        activity.handler.post { activity.refreshOmarchyActionTray() }
    }

    companion object {
        const val ACTION_START = "com.kabigonis.sorairo.RECORD_START"
        const val ACTION_STOP = "com.kabigonis.sorairo.RECORD_STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_W = "w"
        const val EXTRA_H = "h"
        const val EXTRA_DPI = "dpi"
        private const val CHANNEL_ID = "sorairo_screen_record"
        private const val NOTIFICATION_ID = 8802

        @Volatile
        var recording = false

        /** The launcher activity to repaint the tray when recording starts or stops. */
        @Volatile
        var listener: WeakReference<MainActivity>? = null
    }
}
