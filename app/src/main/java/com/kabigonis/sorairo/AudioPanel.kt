package com.kabigonis.sorairo

import android.content.Context
import android.graphics.Typeface
import android.media.AudioManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

// Omarchy-style audio panel: mute switch and output volume (media stream, so no special permission).

internal fun MainActivity.showAudioPanel() {
    val p = pal
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var dialog: AlertDialog? = null
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(16))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }

    val icon = AudioIconView(this, p.cFg)
    val stateText = txt(10f, p.cDim, "")
    val toggle = SwitchBoxView(this, p.cFg, p.cDim)
    val head = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    head.addView(icon, LinearLayout.LayoutParams(dp(32), dp(28)).apply { marginEnd = dp(14) })
    val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    titles.addView(txt(17f, p.cFg, "Audio").apply { setTypeface(typeface, Typeface.BOLD) })
    titles.addView(stateText)
    head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
    head.addView(toggle, LinearLayout.LayoutParams(dp(58), dp(32)))
    root.addView(head)
    root.addView(View(this).apply { setBackgroundColor(p.cDim and 0x00FFFFFF or (0x44 shl 24)) },
        LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(14) })

    val pctText = txt(11f, p.cFg, "")
    root.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(txt(11f, p.cDim, "OUTPUT"), LinearLayout.LayoutParams(0, -2, 1f))
        addView(pctText)
    })
    val slider = SliderView(this, p.cFg, p.cDim and 0x00FFFFFF or (0x55 shl 24))
    root.addView(slider, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(6) })

    fun muted() = am.isStreamMute(AudioManager.STREAM_MUSIC)
    fun maxVol() = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun render(animate: Boolean) {
        val silenced = muted() || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        icon.set(silenced)
        stateText.text = if (silenced) "SILENCED" else "LOUD AND CLEAR"
        toggle.set(!silenced, animate)
        if (!slider.dragging) {
            val v = if (muted()) 0f else am.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol().toFloat()
            slider.value = v
            pctText.text = "${(v * 100).roundToInt()}%"
        }
    }

    slider.onChange = { v, _ ->
        if (muted() && v > 0f) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol()).roundToInt(), 0)
        pctText.text = "${(v * 100).roundToInt()}%"
        icon.set(v == 0f)
        stateText.text = if (v == 0f) "SILENCED" else "LOUD AND CLEAR"
        toggle.set(v > 0f, true)
    }
    toggle.setOnClickListener {
        val silenced = muted() || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        if (silenced) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol() / 3).coerceAtLeast(1), 0)
            }
        } else {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        }
        render(true)
    }

    render(false)
    val tick = object : Runnable {
        override fun run() {
            if (dialog?.isShowing == true) {
                render(true)
                handler.postDelayed(this, 1000)
            }
        }
    }
    dialog = showBarDialog(BarKind.AUDIO, root, 420) { handler.removeCallbacks(tick) }
    handler.postDelayed(tick, 1000)
}
