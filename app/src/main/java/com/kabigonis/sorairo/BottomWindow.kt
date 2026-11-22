package com.kabigonis.sorairo

import android.media.session.PlaybackState
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_PACKAGE
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_EXTRA_SHUFFLE
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_EXTRA_REPEAT

// Bottom window (monitor / player) construction and switching.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.updateBottomWindowRuntime() {
    handler.removeCallbacks(monitorTick)
    if (!activityResumed) {
        stopPlayerCallbacks()
        return
    }
    if (bottomWindowFor(curWs) == "monitor") {
        stopPlayerCallbacks()
        updateMonitorSummary(curWs)
        monitorLastSummaryT = SystemClock.elapsedRealtime()
        monitorGraphs.forEach { it?.setColors(pal.cDim, pal.cBlue, pal.cGreen) }
    } else {
        updatePlayerWindow(curWs)
        startPlayerCallbacks()
    }
    // Keep collecting network history while the player panel is visible too.
    handler.post(monitorTick)
}

internal fun MainActivity.buildBottomWindow(ws: Int): LinearLayout {
    val p = pal
    val win = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = box(panelColor(), p.cDim)
        setOnLongClickListener { showSettings(); true }
    }
    val titleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val title = txt(12.5f, p.cBlue, "~/monitor")
    title.setOnLongClickListener { if (moveSource[ws] >= 0) moveTap(ws, "bottom") else startMove(ws, "bottom"); true }
    bottomWindowTitles[ws] = title
    titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

    fun addChoice(label: String, choice: String) {
        val tab = txt(11f, if (bottomWindowFor(ws) == choice) p.cAccent else p.cDim, label).apply {
            setPadding(dp(4), dp(3), dp(4), dp(3))
            setOnClickListener {
                val terminal = terminalWindowForFocus[ws]
                val pinned = pinWindowForFocus[ws]
                if (terminal != null && pinned != null) {
                    setWindowFocus(ws, 2, terminal, pinned)
                }
                if (bottomWindowFor(ws) != choice) {
                    setBottomWindowForWorkspace(ws, choice)
                    updateBottomWindowDisplay(ws, true)
                    if (ws == curWs) updateBottomWindowRuntime()
                }
            }
        }
        titleRow.addView(tab, LinearLayout.LayoutParams(-2, -2))
    }
    addChoice("monitor", "monitor")
    addChoice("player", "player")
    win.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

    val playerColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP
        setPadding(0, dp(8), 0, 0)
    }
    val playerHeader = txt(10.5f, p.cBlue).apply {
        typeface = Typeface.MONOSPACE
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        text = "Not playing"
    }
    playerHeaders[ws] = playerHeader
    playerColumn.addView(playerHeader, LinearLayout.LayoutParams(-1, -2))
    val playerTime = txt(10.5f, p.cDim).apply {
        typeface = Typeface.MONOSPACE
        maxLines = 1
        text = "00:00 / 00:00"
    }
    playerTimes[ws] = playerTime
    playerColumn.addView(playerTime, LinearLayout.LayoutParams(-1, -2))

    val playerTabs = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(4))
    }
    val queueTab = txt(10.5f, p.cDim, "[Queue]").apply {
        typeface = Typeface.MONOSPACE
        setPadding(dp(6), dp(3), dp(6), dp(3))
    }
    queueTab.setTextColor(p.cAccent)
    playerTabs.addView(queueTab, LinearLayout.LayoutParams(-2, -2))


    val playerTitle = txt(12f, p.cAccent).apply {
        typeface = Typeface.MONOSPACE
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        text = "No active media"
    }
    val playerArtist = txt(10.5f, p.cDim).apply {
        typeface = Typeface.MONOSPACE
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    val playerStatus = txt(10.5f, p.cBlue).apply {
        typeface = Typeface.MONOSPACE
        maxLines = 1
    }
    playerTitles[ws] = playerTitle
    playerArtists[ws] = playerArtist
    playerStatuses[ws] = playerStatus
    // Track details are shown in the two-line header above.
    playerTitle.visibility = View.GONE
    playerArtist.visibility = View.GONE
    playerStatus.visibility = View.GONE

    val playerProgress = android.widget.ProgressBar(
        this, null, android.R.attr.progressBarStyleHorizontal
    ).apply {
        max = 1000
        progress = 0
        visibility = View.GONE
    }
    playerProgressBars[ws] = playerProgress
    playerColumn.addView(
        playerProgress,
        LinearLayout.LayoutParams(-1, dp(3)).apply {
            topMargin = dp(4)
            bottomMargin = dp(2)
        }
    )

    val controls = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun controlButton(label: String, action: () -> Unit): TextView =
        txt(11f, p.cFg, label).apply {
            gravity = Gravity.CENTER
            minWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    controls.addView(
        controlButton("<<") {
            val c = ctrl ?: return@controlButton
            val actions = c.playbackState?.actions ?: 0L
            if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) {
                c.transportControls.skipToPrevious()
            }
        },
        LinearLayout.LayoutParams(dp(48), dp(48))
    )

    val playButton = controlButton("play") {
        val c = ctrl
        val actions = c?.playbackState?.actions ?: 0L
        val playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        val supported = if (playing) {
            actions and PlaybackState.ACTION_PAUSE != 0L
        } else {
            actions and PlaybackState.ACTION_PLAY != 0L
        }
        if (supported) togglePlay()
    }
    playerPlayButtons[ws] = playButton
    controls.addView(playButton, LinearLayout.LayoutParams(dp(48), dp(48)))

    controls.addView(
        controlButton(">>") {
            val c = ctrl ?: return@controlButton
            val actions = c.playbackState?.actions ?: 0L
            if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) {
                c.transportControls.skipToNext()
            }
        },
        LinearLayout.LayoutParams(dp(48), dp(48))
    )

    val shuffleButton = txt(9.5f, p.cDim, "shuffle: none").apply {
        gravity = Gravity.CENTER
        minWidth = dp(48)
        minHeight = dp(48)
        maxLines = 2
        setPadding(dp(2), 0, dp(2), 0)
        visibility = View.GONE
        setOnClickListener {
            try {
                if (ctrl?.packageName != POWERAMP_PACKAGE) return@setOnClickListener
                val next = if (powerampShuffleMode == 0) 2 else 0
                sendPowerampModeCommand(9, POWERAMP_EXTRA_SHUFFLE, next, "shuffle")
            } catch (_: Exception) {
                Toast.makeText(this@buildBottomWindow, "Poweramp shuffle failed", Toast.LENGTH_SHORT).show()
            }
        }
        setOnLongClickListener {
            try {
                if (ctrl?.packageName != POWERAMP_PACKAGE) return@setOnLongClickListener true
                val next = (powerampShuffleMode + 1) % 5
                sendPowerampModeCommand(9, POWERAMP_EXTRA_SHUFFLE, next, "shuffle")
                Toast.makeText(this@buildBottomWindow, "Shuffle: ${powerampShuffleLabel(next)}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@buildBottomWindow, "Poweramp shuffle failed", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
    powerampShuffleButtons[ws] = shuffleButton
    controls.addView(shuffleButton, LinearLayout.LayoutParams(0, dp(48), 1f))

    val repeatButton = txt(9.5f, p.cDim, "repeat: none").apply {
        gravity = Gravity.CENTER
        minWidth = dp(48)
        minHeight = dp(48)
        maxLines = 2
        setPadding(dp(2), 0, dp(2), 0)
        visibility = View.GONE
        setOnClickListener {
            try {
                if (ctrl?.packageName != POWERAMP_PACKAGE) return@setOnClickListener
                val next = when (powerampRepeatMode) {
                    0 -> 1
                    1 -> 3
                    3 -> 0
                    else -> 0
                }
                sendPowerampModeCommand(8, POWERAMP_EXTRA_REPEAT, next, "repeat")
            } catch (_: Exception) {
                Toast.makeText(this@buildBottomWindow, "Poweramp repeat failed", Toast.LENGTH_SHORT).show()
            }
        }
        setOnLongClickListener {
            try {
                if (ctrl?.packageName != POWERAMP_PACKAGE) return@setOnLongClickListener true
                val next = (powerampRepeatMode + 1) % 6
                sendPowerampModeCommand(8, POWERAMP_EXTRA_REPEAT, next, "repeat")
                Toast.makeText(this@buildBottomWindow, "Repeat: ${powerampRepeatLabel(next)}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@buildBottomWindow, "Poweramp repeat failed", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
    powerampRepeatButtons[ws] = repeatButton
    controls.addView(repeatButton, LinearLayout.LayoutParams(0, dp(48), 1f))

    playerColumn.addView(
        controls,
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        }
    )

    val queueLabel = txt(10.5f, p.cAccent, "── QUEUE ──").apply {
        typeface = Typeface.MONOSPACE
        setPadding(dp(6), dp(4), dp(6), dp(2))
    }
    playerColumn.addView(queueLabel, LinearLayout.LayoutParams(-1, -2))

    val queueScroll = ScrollView(this).apply {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    val queueList = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    queueScroll.addView(queueList)
    playerQueueScrolls[ws] = queueScroll
    playerQueueViews[ws] = queueList
    openSettingsOnLongPress(queueScroll)
    playerColumn.addView(
        queueScroll,
        LinearLayout.LayoutParams(-1, dp(112)).apply {
            topMargin = dp(2)
        }
    )
    val playerScroll = ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(playerColumn, ViewGroup.LayoutParams(-1, -2))
    }
    openSettingsOnLongPress(playerScroll)
    bottomWindowContents[ws] = playerScroll

    val monitorColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
    }
    val summary = txt(10.5f, p.cFg).apply {
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
    }
    monitorSummaries[ws] = summary
    monitorColumn.addView(summary, LinearLayout.LayoutParams(-1, -2))
    bottomMonitorColumns[ws] = monitorColumn

    val graph = NetGraphView(this, sharedNetHistory).also {
        it.setColors(p.cDim, p.cBlue, p.cGreen)
    }
    monitorGraphs[ws] = graph
    monitorColumn.addView(graph, LinearLayout.LayoutParams(-1, 0, 1f))

    if (bottomWindowFor(ws) == "monitor") {
        win.addView(monitorColumn, LinearLayout.LayoutParams(-1, 0, 1f))
    } else {
        win.addView(playerScroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    bottomWindowViews[ws] = win
    updateBottomWindowHeader(ws)
    win.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        updateBottomWindowHeader(ws)
        val availableHeight = view.height - titleRow.height -
            view.paddingTop - view.paddingBottom
        val showQueue = availableHeight >= dp(200)
        queueLabel.visibility = if (showQueue) View.VISIBLE else View.GONE
        queueScroll.visibility = if (showQueue) View.VISIBLE else View.GONE
    }
    return win
}

internal fun MainActivity.updateBottomWindowHeader(ws: Int) {
    val choice = bottomWindowFor(ws)
    bottomWindowTitles[ws]?.text = if (choice == "player") "~/player" else "~/monitor"
    val win = bottomWindowViews[ws] ?: return
    (win.getChildAt(0) as? LinearLayout)?.let { row ->
        for (i in 1 until row.childCount) {
            val tab = row.getChildAt(i) as? TextView ?: continue
            tab.setTextColor(if (tab.text.toString() == choice) pal.cAccent else pal.cDim)
        }
    }
}

internal fun MainActivity.updateBottomWindowDisplay(ws: Int, animate: Boolean) {
    val win = bottomWindowViews[ws] ?: return
    val choice = bottomWindowFor(ws)
    val playerContent = bottomWindowContents[ws]
    val monitorContent = bottomMonitorColumns[ws]

    fun applyChoice() {
        updateBottomWindowHeader(ws)

        // Keep the title row, then replace the content slot with exactly
        // one known view. Do not infer which view is active from parent links.
        while (win.childCount > 1) {
            win.removeViewAt(1)
        }
        val selected = if (choice == "player") playerContent else monitorContent
        if (selected != null) {
            (selected.parent as? ViewGroup)?.removeView(selected)
            win.addView(selected, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        if (choice == "player") {
            updatePlayerWindow(ws)
            if (ws == curWs && activityResumed) startPlayerCallbacks()
        } else {
            updateMonitorSummary(ws)
            monitorGraphs[ws]?.setColors(pal.cDim, pal.cBlue, pal.cGreen)
        }
        win.alpha = 1f
        win.requestLayout()
        selected?.requestLayout()
    }

    if (animate) {
        win.animate().cancel()
        win.animate().alpha(0f).setDuration(90).withEndAction {
            applyChoice()
            win.animate().alpha(1f).setDuration(120).start()
        }.start()
    } else {
        applyChoice()
    }
}
