package com.kabigonis.sorairo

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.content.pm.PackageManager

/** Small trampoline that obtains Android's mandatory user consent before starting capture. */
class ScreenRecordingActivity : Activity() {
    private var audioMode = AUDIO_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioMode = intent.getIntExtra(EXTRA_AUDIO_MODE, AUDIO_NONE)
            .coerceIn(AUDIO_NONE, AUDIO_DESKTOP_AND_MIC)

        if (audioMode != AUDIO_NONE && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Device audio capture requires Android 10 or newer.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (audioMode != AUDIO_NONE &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            requestCaptureConsent()
        }
    }

    private fun requestCaptureConsent() {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        } catch (_: Exception) {
            Toast.makeText(this, "Android couldn't start screen capture.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_AUDIO) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestCaptureConsent()
        else {
            Toast.makeText(this, "Microphone permission is needed for the selected audio mode.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility with minSdk 26")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen recording cancelled.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        try {
            val service = Intent(this, ScreenRecorderService::class.java).apply {
                action = ScreenRecorderService.ACTION_START
                putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, data)
                putExtra(ScreenRecorderService.EXTRA_AUDIO_MODE, audioMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service)
            else startService(service)
        } catch (_: Exception) {
            Toast.makeText(this, "Sorairo couldn't start the recorder.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_AUDIO_MODE = "audio_mode"
        const val AUDIO_NONE = 0
        const val AUDIO_DESKTOP = 1
        const val AUDIO_DESKTOP_AND_MIC = 2
        private const val REQUEST_AUDIO = 6201
        private const val REQUEST_CAPTURE = 6202
    }
}