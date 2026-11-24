package com.kabigonis.sorairo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.media.MediaScannerConnection
import java.io.File
import android.widget.Toast
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Native MP4 screen recorder using MediaProjection, MediaCodec and MediaMuxer. */
class ScreenRecorderService : Service() {
    @Volatile private var stopRequested = false
    @Volatile private var recording = false
    private var worker: Thread? = null
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var videoSurface: android.view.Surface? = null
    private var muxer: MediaMuxer? = null
    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var outputFd: ParcelFileDescriptor? = null
    private var muxerStarted = false
    private var videoTrack = -1
    private var audioTrack = -1
    private var expectedTracks = 1
    private var videoEos = false
    private var audioEos = false
    private val pendingSamples = ArrayList<PendingSample>()
    private var desktopRecord: AudioRecord? = null
    private var microphoneRecord: AudioRecord? = null
    private var desktopReader: Thread? = null
    private var microphoneReader: Thread? = null
    private val desktopQueue = ArrayBlockingQueue<ShortArray>(12)
    private val microphoneQueue = ArrayBlockingQueue<ShortArray>(12)
    private var audioFramesSubmitted = 0L
    private var recordingStartTimeUs = 0L
    private var audioStartOffsetUs: Long? = null
    private var audioMode = ScreenRecordingActivity.AUDIO_NONE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            if (worker == null) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RECORDING, false).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (worker?.isAlive == true) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, ActivityResult.RESULT_CANCELED)
            ?: ActivityResult.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        audioMode = intent?.getIntExtra(EXTRA_AUDIO_MODE, ScreenRecordingActivity.AUDIO_NONE)
            ?: ScreenRecordingActivity.AUDIO_NONE
        if (resultCode != ActivityResult.RESULT_OK || resultData == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        stopRequested = false
        recording = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RECORDING, true).apply()
        try {
            startRecordingForeground()
        } catch (t: Exception) {
            android.util.Log.e(TAG, "Couldn't start recording foreground service", t)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RECORDING, false).apply()
            recording = false
            Toast.makeText(this, "Android couldn't start the screen recorder service.", Toast.LENGTH_LONG).show()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        worker = Thread({ runRecorder(resultCode, resultData) }, "Sorairo-ScreenRecorder").apply {
            isDaemon = true
            start()
        }
        return START_NOT_STICKY
    }

    private fun startRecordingForeground() {
