package com.kabigonis.sorairo

import android.graphics.Canvas
import android.graphics.Movie
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * System live-wallpaper renderer for media stored in Sorairo's wallpaper library.
 * The selected file is read from the app's private wallpaper directory.
 */
class SorairoLiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MediaEngine()

    private inner class MediaEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var movie: Movie? = null
        private var player: MediaPlayer? = null
        private var running = false
        private var visible = false
        private var frameTime = 0L
        private val drawFrame = object : Runnable {
            override fun run() {
                if (!running || !visible) return
                drawGifFrame()
                handler.postDelayed(this, 33L)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startMedia()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (movie != null) drawGifFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                if (movie != null) {
                    running = true
                    handler.removeCallbacks(drawFrame)
                    handler.post(drawFrame)
                } else if (player?.isPlaying == false) {
                    runCatching { player?.start() }
                }
            } else {
                running = false
                handler.removeCallbacks(drawFrame)
                runCatching { if (player?.isPlaying == true) player?.pause() }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            running = false
            handler.removeCallbacks(drawFrame)
            releaseMedia()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            running = false
            handler.removeCallbacks(drawFrame)
            releaseMedia()
            super.onDestroy()
        }

        private fun selectedFile(): File? {
            val prefs = getSharedPreferences("sorairo", MODE_PRIVATE)
            val name = prefs.getString("live_wallpaper_file", null) ?: return null
            if (name.contains('/') || name.contains('\\')) return null
            return File(File(filesDir, "wallpapers"), name).takeIf { it.isFile }
        }

        private fun startMedia() {
            releaseMedia()
            val file = selectedFile() ?: return
            when (file.extension.lowercase()) {
                "gif" -> {
                    movie = runCatching { file.inputStream().use { Movie.decodeStream(it) } }.getOrNull()
                    running = movie != null && visible
                    if (running) {
                        frameTime = 0L
                        handler.removeCallbacks(drawFrame)
                        handler.post(drawFrame)
                    }
                }
                "mp4", "webm", "mkv", "mov", "3gp" -> {
                    player = runCatching {
                        MediaPlayer().apply {
                            isLooping = true
                            setDataSource(file.absolutePath)
                            setDisplay(surfaceHolder)
                            setOnPreparedListener { mp -> if (visible) mp.start() }
                            setOnErrorListener { _, _, _ -> true }
                            prepareAsync()
                        }
                    }.getOrNull()
                }
            }
        }

        private fun drawGifFrame() {
            val current = movie ?: return
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val canvas: Canvas = try { holder.lockCanvas() ?: return } catch (_: Exception) { return }
            try {
                val duration = current.duration().takeIf { it > 0 } ?: 1000
                val now = android.os.SystemClock.uptimeMillis()
                if (frameTime == 0L) frameTime = now
                val elapsed = ((now - frameTime) % duration).toInt()
                current.setTime(elapsed)
                val scale = minOf(
                    canvas.width.toFloat() / current.width().coerceAtLeast(1),
                    canvas.height.toFloat() / current.height().coerceAtLeast(1)
                )
                val left = (canvas.width - current.width() * scale) / 2f
                val top = (canvas.height - current.height() * scale) / 2f
                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.save()
                canvas.translate(left, top)
                canvas.scale(scale, scale)
                current.draw(canvas, 0f, 0f)
                canvas.restore()
            } catch (_: Exception) {
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun releaseMedia() {
            running = false
            movie = null
            player?.runCatching { setOnPreparedListener(null); stop(); release() }
            player = null
        }
    }
}
