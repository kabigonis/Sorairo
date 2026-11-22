package com.kabigonis.sorairo

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_PACKAGE
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_API_COMMAND
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_API_RECEIVER
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_MODE_CHANGED
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_EXTRA_SHUFFLE
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_EXTRA_REPEAT

// Media session, Poweramp helpers, player window and now-playing updates.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.activeController(): MediaController? {
    if (!mediaAccess()) return null
    return try {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = manager.getActiveSessions(
            ComponentName(this, NowPlayingService::class.java)
        )
        sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull()
    } catch (_: Exception) {
        null
    }
}

internal fun MainActivity.stopPlayerCallbacks() {
    unregisterPowerampModeReceiver()
    val controller = listeningController
    val callback = playerCallback
    if (controller != null && callback != null) {
        try {
            controller.unregisterCallback(callback)
        } catch (_: Exception) {
        }
    }
    listeningController = null
    playerCallback = null
    handler.removeCallbacks(playerRefresh)
}

internal fun MainActivity.unregisterPowerampModeReceiver() {
    val receiver = powerampModeReceiver ?: return
    try {
        unregisterReceiver(receiver)
    } catch (_: Exception) {
    } finally {
        powerampModeReceiver = null
    }
}

internal fun MainActivity.registerPowerampModeReceiverIfNeeded(controller: MediaController?) {
    if (!activityResumed || bottomWindowFor(curWs) != "player" ||
        controller?.packageName != POWERAMP_PACKAGE
    ) {
        unregisterPowerampModeReceiver()
        return
    }
    if (powerampModeReceiver != null) return
    try {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    if (intent?.action != POWERAMP_MODE_CHANGED) return
                    if (intent.hasExtra(POWERAMP_EXTRA_SHUFFLE)) {
                        val mode = intent.getIntExtra(POWERAMP_EXTRA_SHUFFLE, powerampShuffleMode)
                        if (mode in 0..4) powerampShuffleMode = mode
                    }
                    if (intent.hasExtra(POWERAMP_EXTRA_REPEAT)) {
                        powerampRepeatMode = intent.getIntExtra(POWERAMP_EXTRA_REPEAT, powerampRepeatMode)
                            .coerceIn(0, 5)
                    }
                    updatePowerampButtons(curWs)
                } catch (_: Exception) {
                    Toast.makeText(this@registerPowerampModeReceiverIfNeeded, "Poweramp state unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val sticky = ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(POWERAMP_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        powerampModeReceiver = receiver
        if (sticky != null) {
            if (sticky.hasExtra(POWERAMP_EXTRA_SHUFFLE)) {
                val mode = sticky.getIntExtra(POWERAMP_EXTRA_SHUFFLE, powerampShuffleMode)
                if (mode in 0..4) powerampShuffleMode = mode
            }
            if (sticky.hasExtra(POWERAMP_EXTRA_REPEAT)) {
                powerampRepeatMode = sticky.getIntExtra(POWERAMP_EXTRA_REPEAT, powerampRepeatMode).coerceIn(0, 5)
            }
        }
        updatePowerampButtons(curWs)
    } catch (_: Exception) {
        powerampModeReceiver = null
        Toast.makeText(this, "Poweramp state unavailable", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.powerampShuffleLabel(mode: Int): String = when (mode) {
    0 -> "none"
    1 -> "all"
    2 -> "songs"
    3 -> "cats"
    4 -> "songs+cats"
    else -> "none"
}

internal fun MainActivity.powerampRepeatLabel(mode: Int): String = when (mode) {
    0 -> "none"
    1 -> "on"
    2 -> "advance"
    3 -> "song"
    4 -> "single"
    5 -> "advance+stop"
    else -> "none"
}

internal fun MainActivity.updatePowerampButtons(ws: Int) {
    val visible = ctrl?.packageName == POWERAMP_PACKAGE &&
        activityResumed && bottomWindowFor(curWs) == "player"
    powerampShuffleButtons[ws]?.apply {
        visibility = if (visible) View.VISIBLE else View.GONE
        text = "shuffle: ${powerampShuffleLabel(powerampShuffleMode)}"
        setTextColor(if (powerampShuffleMode != 0) pal.cAccent else pal.cDim)
    }
    powerampRepeatButtons[ws]?.apply {
        visibility = if (visible) View.VISIBLE else View.GONE
        text = "repeat: ${powerampRepeatLabel(powerampRepeatMode)}"
        setTextColor(if (powerampRepeatMode != 0) pal.cAccent else pal.cDim)
    }
}

internal fun MainActivity.sendPowerampModeCommand(command: Int, extra: String, mode: Int, label: String) {
    try {
        val intent = Intent(POWERAMP_API_COMMAND).setComponent(
            ComponentName(POWERAMP_PACKAGE, POWERAMP_API_RECEIVER)
        ).putExtra("cmd", command).putExtra(extra, mode)
        sendBroadcast(intent)
        if (extra == POWERAMP_EXTRA_SHUFFLE) powerampShuffleMode = mode
        else powerampRepeatMode = mode
        updatePowerampButtons(curWs)
    } catch (_: Exception) {
        Toast.makeText(this, "Poweramp $label failed", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.updatePlayerWindow(ws: Int) {
    val controller = ctrl
    val metadata = controller?.metadata
    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    val state = controller?.playbackState?.state
    val playback = when (state) {
        PlaybackState.STATE_PLAYING -> "Playing"
        PlaybackState.STATE_PAUSED -> "Paused"
        PlaybackState.STATE_BUFFERING -> "Buffering"
        else -> "Not playing"
    }

    if (!title.isNullOrBlank()) {
        lastSongDetails = if (artist.isNullOrBlank()) title else "$title\n$artist"
        missingSessionReads = 0
    } else if (controller == null) {
        missingSessionReads++
        if (missingSessionReads >= 2) lastSongDetails = "No active media"
    }

    val details = lastSongDetails.split("\n", limit = 2)
    playerStatuses[ws]?.text = playback
    updatePowerampButtons(ws)
    playerPlayButtons[ws]?.text =
        if (state == PlaybackState.STATE_PLAYING) "pause" else "play"

    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val playbackState = controller?.playbackState
    val position = playbackState?.position ?: -1L
    fun timeLabel(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }
    val elapsed = if (position >= 0L) timeLabel(position) else "00:00"
    val total = if (duration > 0L) timeLabel(duration) else "00:00"
    val trackLabel = details.filter { it.isNotBlank() }.joinToString(" - ")
    val headerTrack = if (trackLabel.isBlank()) "" else " — $trackLabel"
    playerHeaders[ws]?.text = "$playback$headerTrack"
    playerTimes[ws]?.text = "$elapsed / $total"
    val queueView = playerQueueViews[ws]
    if (queueView != null) {
        val queue = controller?.queue.orEmpty().take(100)
        val activeQueueId = playbackState?.activeQueueItemId
        val signature = queue.joinToString("|") {
            "${it.queueId}:${it.description.title}:${it.description.subtitle}"
        }
        val rows = playerQueueRows[ws]

        if (queue.isEmpty()) {
            if (playerQueueSignatures[ws] != "empty") {
                queueView.removeAllViews()
                rows.clear()
                val empty = txt(10f, pal.cDim, "this app doesn't share its queue")
                queueView.addView(empty, LinearLayout.LayoutParams(-1, -2))
                playerQueueEmptyLabels[ws] = empty
                playerQueueSignatures[ws] = "empty"
            }
        } else {
            if (playerQueueSignatures[ws] != signature) {
                queueView.removeAllViews()
                rows.clear()
                playerQueueEmptyLabels[ws] = null

                queue.forEachIndexed { index, item ->
                    val itemTitle = item.description.title?.toString()
                        ?.takeIf { it.isNotBlank() } ?: "Unknown track"
                    val itemArtist = item.description.subtitle?.toString().orEmpty()
                    val label = if (itemArtist.isBlank()) itemTitle else "$itemTitle - $itemArtist"
                    val row = txt(10.5f, pal.cFg, "${index + 1}. $label").apply {
                        typeface = Typeface.MONOSPACE
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(6), dp(5), dp(6), dp(5))
                        setOnLongClickListener { showSettings(); true }
                        setOnClickListener {
                            val c = ctrl ?: return@setOnClickListener
                            val stateNow = c.playbackState ?: return@setOnClickListener
                            if ((stateNow.actions and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L) {
                                c.transportControls.skipToQueueItem(item.queueId)
                            }
                        }
                    }
                    rows.add(row)
                    queueView.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
                playerQueueSignatures[ws] = signature
            }

            queue.forEachIndexed { index, item ->
                rows.getOrNull(index)?.setTextColor(
                    if (activeQueueId != null && item.queueId == activeQueueId) {
                        pal.cAccent
                    } else {
                        pal.cFg
                    }
                )
            }
        }
    }

    val progressBar = playerProgressBars[ws]
    if (progressBar != null) {
        if (duration > 0L && position >= 0L && position <= duration) {
            progressBar.max = 1000
            progressBar.progress =
                ((position.toDouble() / duration.toDouble()) * 1000.0).toInt()
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.progress = 0
            progressBar.visibility = View.GONE
        }
    }

    playerTitles[ws]?.text = details.firstOrNull().orEmpty()
    playerArtists[ws]?.apply {
        text = details.getOrNull(1).orEmpty()
        visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }
}

internal fun MainActivity.startPlayerCallbacks() {
    if (!activityResumed || bottomWindowFor(curWs) != "player") return

    val controller = activeController()
    if (controller == null) {
        stopPlayerCallbacks()
        ctrl = null
        updatePlayerWindow(curWs)
        handler.removeCallbacks(playerRefresh)
        handler.postDelayed(playerRefresh, 1000)
        return
    }

    if (listeningController !== controller) {
        stopPlayerCallbacks()
        val callback = object : MediaController.Callback() {
            override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) {
                updatePlayerWindow(curWs)
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateNowPlaying()
                updatePlayerWindow(curWs)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateNowPlaying()
                updatePlayerWindow(curWs)
            }

            override fun onSessionDestroyed() {
                stopPlayerCallbacks()
                ctrl = null
                updateNowPlaying()
                updatePlayerWindow(curWs)
                if (activityResumed && bottomWindowFor(curWs) == "player") {
                    handler.postDelayed(playerRefresh, 1000)
                }
            }
        }
        listeningController = controller
        playerCallback = callback
        try {
            controller.registerCallback(callback)
        } catch (_: Exception) {
            listeningController = null
            playerCallback = null
        }
    }

    ctrl = controller
    registerPowerampModeReceiverIfNeeded(controller)
    updatePlayerWindow(curWs)
    handler.removeCallbacks(playerRefresh)
    handler.postDelayed(playerRefresh, 1000)
}

internal fun MainActivity.touchDownStartedInCurrentQueue(): Boolean {
    if (bottomWindowFor(curWs) != "player") return false
    val queueScroll = playerQueueScrolls.getOrNull(curWs) ?: return false
    if (!queueScroll.isShown || queueScroll.width <= 0 || queueScroll.height <= 0) return false
    return try {
        val location = IntArray(2)
        queueScroll.getLocationOnScreen(location)
        touchDownScreenX >= location[0] && touchDownScreenX < location[0] + queueScroll.width &&
            touchDownScreenY >= location[1] && touchDownScreenY < location[1] + queueScroll.height
    } catch (_: Exception) {
        false
    }
}

internal fun MainActivity.registerActiveSessionsListener() {
    if (!mediaAccess() || activeSessionsListener != null) return
    try {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            val active = sessions?.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions?.firstOrNull()
            ctrl = active
            updateNowPlaying()
            updatePlayerWindow(curWs)
            if (activityResumed && bottomWindowFor(curWs) == "player") {
                if (active != null) startPlayerCallbacks()
                else handler.postDelayed(playerRefresh, 700L)
            }
        }
        manager.addOnActiveSessionsChangedListener(
            listener, ComponentName(this, NowPlayingService::class.java), handler
        )
        activeSessionsListener = listener
    } catch (_: Exception) {
        activeSessionsListener = null
    }
}

internal fun MainActivity.unregisterActiveSessionsListener() {
    val listener = activeSessionsListener ?: return
    try {
        (getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager)
            .removeOnActiveSessionsChangedListener(listener)
    } catch (_: Exception) {
    }
    activeSessionsListener = null
}

// ================= now playing =================
internal fun MainActivity.togglePlay() {
    val c = ctrl ?: return
    if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
    else c.transportControls.play()
    handler.postDelayed({ updateNowPlaying() }, 400)
}

internal fun MainActivity.updateNowPlaying() {
    var title: String? = null
    var playing = false
    val c = activeController()
    ctrl = c
    val md = c?.metadata
    val rawTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
    val playback = c?.playbackState
    playing = playback?.state == PlaybackState.STATE_PLAYING
    if (!rawTitle.isNullOrBlank()) {
        title = if (artist.isNullOrBlank()) rawTitle else "$rawTitle — $artist"
    }

    // Keep the island's MEDIA activity in sync with the same session used by
    // Sorairo's player window. A sticky entry remains visible while paused;
    // losing the session or its track metadata removes it.
    if (!rawTitle.isNullOrBlank()) {
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playback?.position ?: -1L
        val progress = if (duration > 0L && position >= 0L) {
            (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
        val playbackLabel = when (playback?.state) {
            PlaybackState.STATE_PLAYING -> "Playing"
            PlaybackState.STATE_PAUSED -> "Paused"
            PlaybackState.STATE_BUFFERING -> "Buffering"
            PlaybackState.STATE_STOPPED -> "Stopped"
            else -> "Not playing"
        }
        val subtitle = listOfNotNull(
            artist?.takeIf { it.isNotBlank() },
            playbackLabel
        ).joinToString(" · ").take(80)
        islandCore.submit(
            IslandActivity(
                id = "media:now-playing",
                kind = IslandKind.MEDIA,
                title = rawTitle.take(60),
                subtitle = subtitle,
                progress = progress,
                ttlMs = 0L
            ),
            SystemClock.elapsedRealtime()
        )
    } else {
        islandCore.remove("media:now-playing")
    }

    if (title == null) {
        song.visibility = View.GONE
        vis.visibility = View.GONE
        vis.playing = false
    } else {
        song.text = title
        song.setTextColor(if (playing) pal.cFg else pal.cDim)
        song.visibility = View.VISIBLE
        vis.visibility = View.VISIBLE
        vis.color = if (playing) pal.cGreen else pal.cDim
        vis.playing = playing
    }
    refreshIsland()
}
