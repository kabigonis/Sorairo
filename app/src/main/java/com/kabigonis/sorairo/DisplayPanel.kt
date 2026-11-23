package com.kabigonis.sorairo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

// Omarchy-style display panel: brightness, text size and UI scale.

internal val TEXT_SIZES = intArrayOf(11, 12, 13, 14, 15, 16)
internal val UI_SCALES = floatArrayOf(1f, 1.25f, 1.5f, 2f)

internal fun MainActivity.showDisplayPanel() {
    val p = pal
    var dialog: AlertDialog? = null
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(16))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }
    fun divider() = View(this).apply { setBackgroundColor(p.cDim and 0x00FFFFFF or (0x44 shl 24)) }
    fun sectionRow(label: String, value: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(txt(11f, p.cDim, label), LinearLayout.LayoutParams(0, -2, 1f))
        addView(value)
    }

    val canWrite = Settings.System.canWrite(this)

    // header
    val head = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    head.addView(DisplayIconView(this, p.cFg), LinearLayout.LayoutParams(dp(30), dp(28)).apply { marginEnd = dp(14) })
    val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    titles.addView(txt(17f, p.cFg, "Display").apply { setTypeface(typeface, Typeface.BOLD) })
    titles.addView(txt(10f, p.cDim, if (canWrite) "ADJUSTABLE BRIGHTNESS" else "FIXED BRIGHTNESS"))
    head.addView(titles)
    root.addView(head)
    root.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(14) })

    // brightness
    val brightValue = txt(11f, p.cFg, "")
    if (canWrite) {
        val current = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        } catch (_: Exception) {
            0.5f
        }
        brightValue.text = "${(current * 100).roundToInt()}%"
        root.addView(sectionRow("BRIGHTNESS", brightValue))
        val slider = SliderView(this, p.cFg, p.cDim and 0x00FFFFFF or (0x55 shl 24)).apply {
            value = current
            onChange = { v, _ ->
                brightValue.text = "${(v * 100).roundToInt()}%"
                setBrightness(v)
            }
        }
        root.addView(slider, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(6) })
    } else {
        brightValue.text = "allow ›"
        brightValue.setTextColor(p.cAccent)
        val row = sectionRow("BRIGHTNESS", brightValue)
        row.setPadding(0, dp(4), 0, dp(4))
        row.setOnClickListener {
            Toast.makeText(this, "Allow \"modify system settings\" for brightness", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                openSystem(Settings.ACTION_SETTINGS)
            }
        }
        root.addView(row)
    }
    root.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(14) })

    // text size
    val sizeNow = uiTextSize
    val sizeValue = txt(11f, p.cFg, "${sizeNow}px")
    root.addView(sectionRow("TEXT SIZE", sizeValue))
    val sizeSlider = SliderView(this, p.cFg, p.cDim and 0x00FFFFFF or (0x55 shl 24), TEXT_SIZES.size).apply {
        val idx = TEXT_SIZES.indexOf(sizeNow).let { if (it < 0) 2 else it }
        value = idx / (TEXT_SIZES.size - 1).toFloat()
        onChange = { v, done ->
            val i = (v * (TEXT_SIZES.size - 1)).roundToInt().coerceIn(0, TEXT_SIZES.size - 1)
            sizeValue.text = "${TEXT_SIZES[i]}px"
            if (done && TEXT_SIZES[i] != uiTextSize) {
                setUiTextSize(TEXT_SIZES[i])
                dialog?.dismiss()
                rebuild(false)
            }
        }
    }
    root.addView(sizeSlider, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(6) })
    root.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(14) })

    // scale
    root.addView(txt(11f, p.cDim, "SCALE"))
    val scaleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    for ((i, sc) in UI_SCALES.withIndex()) {
        val active = sc == uiScale
        scaleRow.addView(txt(13f, if (active) onFill(p.cFg, p.cSel) else p.cFg, formatScale(sc)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
            background = box(if (active) p.cSel else Color.TRANSPARENT, if (active) p.cBlue else p.cDim)
            setOnClickListener {
                if (sc != uiScale) {
                    setUiScale(sc)
                    dialog?.dismiss()
                    rebuild(false)
                }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < UI_SCALES.size - 1) marginEnd = dp(8) })
    }
    root.addView(scaleRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

    dialog = showBarDialog(BarKind.DISPLAY, root, 420)
}
