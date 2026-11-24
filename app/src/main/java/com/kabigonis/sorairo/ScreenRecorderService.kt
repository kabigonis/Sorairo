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
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while Sorairo is recording the screen"
                setSound(null, null)
                enableVibration(false)
            })
        }
        val stopIntent = Intent(this, ScreenRecorderService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, NOTIFICATION_ID, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Sorairo is recording")
                .setContentText("Tap Stop to finish and save the MP4.")
                .setOngoing(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Sorairo is recording")
                .setContentText("Tap Stop to finish and save the MP4.")
                .setOngoing(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 34 && audioMode == ScreenRecordingActivity.AUDIO_DESKTOP_AND_MIC) {
            // Android 14 requires a declared microphone FGS type for long-running mic capture.
            // The consent Activity has just obtained RECORD_AUDIO while visible.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun runRecorder(resultCode: Int, resultData: Intent) {
        var completed = false
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("Android did not provide screen-capture permission")
            projection = mediaProjection
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() { stopRequested = true }
            }.also { mediaProjection.registerCallback(it, Handler(Looper.getMainLooper())) }

            createOutput()
            configureVideoEncoder()
            if (audioMode != ScreenRecordingActivity.AUDIO_NONE) configureAudioEncoder(mediaProjection)
            // Surface video timestamps use the monotonic clock. Normalize video and audio
            // to one session origin so the MP4 tracks stay synchronized.
            recordingStartTimeUs = System.nanoTime() / 1_000L
            val metrics = resources.displayMetrics
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "Sorairo Screen Recording", videoWidth, videoHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, videoSurface, null, null
            ) ?: throw IllegalStateException("Android couldn't create the recording display")

            while (!stopRequested) {
                if (audioEncoder != null) {
                    val desktop = desktopQueue.poll(15, TimeUnit.MILLISECONDS)
                    val microphone = if (audioMode == ScreenRecordingActivity.AUDIO_DESKTOP_AND_MIC)
                        microphoneQueue.poll(15, TimeUnit.MILLISECONDS) else null
                    val pcm = mixStereoWithMono(desktop, microphone, AUDIO_CHUNK_FRAMES)
                    queueAudioPcm(pcm)
                    drainEncoder(audioEncoder!!, false)
                } else {
                    Thread.sleep(8)
                }
                drainEncoder(videoEncoder!!, true)
            }

            stopAudioReaders()
            virtualDisplay?.release()
            virtualDisplay = null
            videoEncoder?.signalEndOfInputStream()
            if (audioEncoder != null) queueAudioEos()
            val deadline = SystemClock.elapsedRealtime() + 3000L
            while (SystemClock.elapsedRealtime() < deadline &&
                (!videoEos || (audioEncoder != null && !audioEos))
            ) {
                if (audioEncoder != null && !audioEos) {
                    audioEos = drainEncoder(audioEncoder!!, false) || audioEos
                }
                if (!videoEos) {
                    videoEos = drainEncoder(videoEncoder!!, true) || videoEos
                }
                Thread.sleep(4)
            }
            completed = muxerStarted && videoTrack >= 0 && videoEos &&
                (audioEncoder == null || (audioTrack >= 0 && audioEos))
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Screen recording failed", t)
            postFailureNotification(t.message ?: "Unknown recording error")
        } finally {
            stopAudioReaders()
            releaseEncoders()
            try { projection?.stop() } catch (_: Exception) { }
            projection = null
            try { muxer?.stopIfStarted(muxerStarted) } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            muxer = null
            try { outputFd?.close() } catch (_: Exception) { }
            outputFd = null
            if (completed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    outputUri?.let { uri ->
                        try {
                            contentResolver.update(uri, ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            }, null, null)
                        } catch (_: Exception) { }
                    }
                } else {
                    outputFile?.let { file ->
                        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                    }
                }
            } else {
                outputUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) { } }
                outputFile?.let { try { it.delete() } catch (_: Exception) { } }
            }
            outputUri = null
            outputFile = null
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RECORDING, false).apply()
            recording = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private var videoWidth = 0
    private var videoHeight = 0

    private fun createOutput() {
        val metrics = resources.displayMetrics
        val rawWidth = metrics.widthPixels.coerceAtLeast(2)
        val rawHeight = metrics.heightPixels.coerceAtLeast(2)
        val landscape = rawWidth >= rawHeight
        val maxWidth = if (landscape) 1920f else 1080f
        val maxHeight = if (landscape) 1080f else 1920f
        val scale = minOf(1f, maxWidth / rawWidth, maxHeight / rawHeight)
        videoWidth = ((rawWidth * scale).toInt() / 2 * 2).coerceAtLeast(2)
        videoHeight = ((rawHeight * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val filename = "Sorairo_" + System.currentTimeMillis() + ".mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Sorairo")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Couldn't create a video in Movies/Sorairo")
            outputFd = contentResolver.openFileDescriptor(outputUri!!, "w")
                ?: throw IllegalStateException("Couldn't open the recording output")
            muxer = MediaMuxer(outputFd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "Sorairo")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Couldn't create Sorairo's Movies folder")
            }
            outputFile = File(directory, filename)
            muxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
    }

    private fun configureVideoEncoder() {
        val bitrate = (videoWidth.toLong() * videoHeight * 5L).coerceIn(5_000_000L, 24_000_000L).toInt()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoSurface = createInputSurface()
            start()
        }
    }

    private fun configureAudioEncoder(mediaProjection: MediaProjection) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_CHUNK_FRAMES * 2 * 2 * 4)
        }
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        expectedTracks = 2
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IllegalStateException("Device audio capture requires Android 10 or newer")
        }
        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val desktopFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AUDIO_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val desktopMin = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(AUDIO_CHUNK_FRAMES * 4)
        desktopRecord = AudioRecord.Builder()
            .setAudioFormat(desktopFormat)
            .setBufferSizeInBytes(desktopMin * 2)
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .build()
        if (desktopRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Couldn't initialise device-audio capture")
        }

        if (audioMode == ScreenRecordingActivity.AUDIO_DESKTOP_AND_MIC) {
            val micFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val micMin = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(AUDIO_CHUNK_FRAMES * 2)
            microphoneRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(micFormat)
                .setBufferSizeInBytes(micMin * 2)
                .build()
            if (microphoneRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Couldn't initialise microphone capture")
            }
        }
        startAudioReader(desktopRecord!!, desktopQueue, AUDIO_CHUNK_FRAMES * 2, "Sorairo-Playback-Audio")
        microphoneRecord?.let {
            startAudioReader(it, microphoneQueue, AUDIO_CHUNK_FRAMES, "Sorairo-Microphone-Audio")
        }
    }

    private fun startAudioReader(
        record: AudioRecord, queue: ArrayBlockingQueue<ShortArray>, samplesPerRead: Int, name: String
    ) {
        record.startRecording()
        val thread = Thread({
            val buffer = ShortArray(samplesPerRead)
            while (!stopRequested) {
                val count = try { record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) }
                catch (_: Exception) { break }
                if (count > 0) {
                    val chunk = buffer.copyOf(count)
                    if (!queue.offer(chunk)) {
                        queue.poll()
                        queue.offer(chunk)
                    }
                } else if (count < 0) break
            }
        }, name).apply { isDaemon = true; start() }
        if (name.contains("Playback")) desktopReader = thread else microphoneReader = thread
    }

    private fun queueAudioPcm(pcm: ShortArray) {
        val codec = audioEncoder ?: return
        val frames = pcm.size / 2
        val startOffset = audioStartOffsetUs ?: (System.nanoTime() / 1_000L - recordingStartTimeUs)
            .coerceAtLeast(0L).also { audioStartOffsetUs = it }
        val pts = startOffset + audioFramesSubmitted * 1_000_000L / AUDIO_SAMPLE_RATE
        // Advance the media timeline even if an encoder input slot is temporarily unavailable;
        // otherwise dropped PCM chunks make later audio drift behind the video.
        audioFramesSubmitted += frames
        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex < 0) return
        val input = codec.getInputBuffer(inputIndex) ?: return
        input.clear()
        input.order(ByteOrder.nativeOrder())
        val maxSamples = minOf(pcm.size, input.remaining() / 2)
        for (i in 0 until maxSamples) input.putShort(pcm[i])
        codec.queueInputBuffer(inputIndex, 0, maxSamples * 2, pts, 0)
    }

    private fun queueAudioEos() {
        val codec = audioEncoder ?: return
        // Drain output while waiting for an input slot, then always signal end-of-stream.
        val deadline = SystemClock.elapsedRealtime() + 1_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val startOffset = audioStartOffsetUs ?: (System.nanoTime() / 1_000L - recordingStartTimeUs)
                    .coerceAtLeast(0L).also { audioStartOffsetUs = it }
                val pts = startOffset + audioFramesSubmitted * 1_000_000L / AUDIO_SAMPLE_RATE
                codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            audioEos = drainEncoder(codec, false) || audioEos
            if (audioEos) return
        }
        throw IllegalStateException("Couldn't finish the audio encoder")
    }

    private fun drainEncoder(codec: MediaCodec, isVideo: Boolean): Boolean {
        val info = MediaCodec.BufferInfo()
        var sawEos = false
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return sawEos
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val track = muxer!!.addTrack(codec.outputFormat)
                    if (isVideo) videoTrack = track else audioTrack = track
                    startMuxerWhenReady()
                }
                index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                index >= 0 -> {
                    val output = codec.getOutputBuffer(index)
                    if (info.size > 0 && output != null &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        val duplicate = output.duplicate()
                        duplicate.position(info.offset)
                        duplicate.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        duplicate.get(bytes)
                        val track = if (isVideo) videoTrack else audioTrack
                        val presentationTimeUs = if (isVideo)
                            (info.presentationTimeUs - recordingStartTimeUs).coerceAtLeast(0L)
                        else info.presentationTimeUs
                        val sample = PendingSample(track, bytes, presentationTimeUs, info.flags)
                        if (muxerStarted) writeSample(sample) else pendingSamples.add(sample)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawEos = true
                    codec.releaseOutputBuffer(index, false)
                    if (sawEos) return true
                }
                else -> return sawEos
            }
        }
    }

    private fun startMuxerWhenReady() {
        if (muxerStarted || videoTrack < 0 || (expectedTracks == 2 && audioTrack < 0)) return
        muxer!!.start()
        muxerStarted = true
        pendingSamples.sortedBy { it.presentationTimeUs }.forEach(::writeSample)
        pendingSamples.clear()
    }

    private fun writeSample(sample: PendingSample) {
        if (sample.track < 0 || !muxerStarted) return
        val buffer = ByteBuffer.wrap(sample.data)
        val info = MediaCodec.BufferInfo().apply {
            set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
        }
        muxer?.writeSampleData(sample.track, buffer, info)
    }

    @Synchronized
    private fun stopAudioReaders() {
        stopRequested = true
        try { desktopRecord?.stop() } catch (_: Exception) { }
        try { microphoneRecord?.stop() } catch (_: Exception) { }
        try { desktopReader?.join(500) } catch (_: Exception) { }
        try { microphoneReader?.join(500) } catch (_: Exception) { }
        desktopReader = null
        microphoneReader = null
        desktopRecord?.release()
        microphoneRecord?.release()
        desktopRecord = null
        microphoneRecord = null
    }

    private fun releaseEncoders() {
        virtualDisplay?.release()
        virtualDisplay = null
        try { videoSurface?.release() } catch (_: Exception) { }
        videoSurface = null
        try { videoEncoder?.stop() } catch (_: Exception) { }
        try { videoEncoder?.release() } catch (_: Exception) { }
        videoEncoder = null
        try { audioEncoder?.stop() } catch (_: Exception) { }
        try { audioEncoder?.release() } catch (_: Exception) { }
        audioEncoder = null
        try { projectionCallback?.let { projection?.unregisterCallback(it) } } catch (_: Exception) { }
        projectionCallback = null
    }

    private fun postFailureNotification(message: String) {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Sorairo recording failed")
                .setContentText(message.take(120))
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID + 1, notification)
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        stopRequested = true
        stopAudioReaders()
        super.onDestroy()
    }

    private data class PendingSample(
        val track: Int, val data: ByteArray, val presentationTimeUs: Long, val flags: Int
    )

    private object ActivityResult {
        const val RESULT_CANCELED = 0
        const val RESULT_OK = -1
    }

    companion object {
        const val ACTION_START = "com.kabigonis.sorairo.SCREEN_RECORD_START"
        const val ACTION_STOP = "com.kabigonis.sorairo.SCREEN_RECORD_STOP"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        private const val PREFS = "launcher"
        private const val KEY_RECORDING = "screen_recording_active"
        private const val CHANNEL_ID = "sorairo_screen_recording"
        private const val NOTIFICATION_ID = 6203
        private const val TAG = "SorairoRecorder"
        private const val AUDIO_SAMPLE_RATE = 48_000
        private const val AUDIO_CHUNK_FRAMES = 480
        private const val AUDIO_BIT_RATE = 160_000
        private const val VIDEO_FRAME_RATE = 30
    }
}

private fun MediaMuxer.stopIfStarted(started: Boolean) {
    if (started) stop()
}