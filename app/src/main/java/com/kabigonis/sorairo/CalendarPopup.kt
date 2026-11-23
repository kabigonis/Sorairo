package com.kabigonis.sorairo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// Omarchy-style calendar popup. Opens when the normal top-bar clock is tapped.
// It is deliberately separate from the Dynamic Island (the island replaces the clock when enabled).

/** First cell of a 6-row month grid: the start of the week that contains the 1st. */
internal fun calendarGridStart(month: YearMonth, firstDow: DayOfWeek): LocalDate {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDow.value + 7) % 7
    return first.minusDays(offset.toLong())
}

/** How far through the year [d] is, as a whole percent (Nov 22 of a non-leap year is 89). */
internal fun yearProgressPercent(d: LocalDate): Int =
    (d.dayOfYear * 100f / d.lengthOfYear()).toInt().coerceIn(0, 100)

/**
 * Pins a dialog under the top bar like the Omarchy popups: [horizontal] is Gravity.CENTER_HORIZONTAL
 * (calendar, under the clock) or Gravity.END (battery, under the status icons). No screen dimming.
 */
internal fun MainActivity.anchorDialogToTop(
    dialog: AlertDialog?,
    horizontal: Int,
    sideMarginPx: Int = 0,
    panelWidth: Int = 0,
    anchor: View? = null
) {
    if (dialog == null) return
    val w = dialog.window ?: return
    // Lay the popup out against the physical screen, not the inset-adjusted frame. Otherwise it rides
    // down with the system status bar when the user pulls the Android quick-settings shade.
    WindowCompat.setDecorFitsSystemWindows(w, false)
    w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    val lp = w.attributes
    lp.y = barBottomY()
    if (anchor != null && panelWidth > 0) {
        // centred under the icon that opened it, kept on screen
        w.setGravity(Gravity.TOP or Gravity.START)
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenW = resources.displayMetrics.widthPixels
        val cx = loc[0] + anchor.width / 2
        lp.x = (cx - panelWidth / 2).coerceIn(dp(8), maxOf(dp(8), screenW - panelWidth - dp(8)))
    } else {
        w.setGravity(Gravity.TOP or horizontal)
        if (horizontal == Gravity.END) lp.x = sideMarginPx
    }
    lp.dimAmount = 0f
    w.attributes = lp
    // the shade (or anything else) taking focus closes the popup instead of leaving it floating
    w.decorView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
        if (!hasFocus && dialog.isShowing) dialog.dismiss()
    }
    // short slide-and-fade in, like the Omarchy popups
    w.decorView.apply {
        alpha = 0f
        translationY = -dp(10).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(170).start()
    }
}

internal fun MainActivity.showCalendarPopup() {
    dismissActivePopup()
    val p = pal
    val loc = Locale.getDefault()
    val today = LocalDate.now()
    val wf = WeekFields.of(loc)
    var shown = YearMonth.from(today)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(14))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }

    // Big "November 22" header with the calendar icon (display only).
    val head = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    head.addView(CalendarIconView(this, p.cBlue), LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
    head.addView(
        txt(30f, p.cBlue, today.month.getDisplayName(TextStyle.FULL, loc) + " " + today.dayOfMonth)
            .apply { setTypeface(typeface, Typeface.BOLD) }
    )
    root.addView(head, LinearLayout.LayoutParams(-1, -2))

    // Year progress bar.
    val pct = yearProgressPercent(today)
    val prog = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(10))
    }
    prog.addView(txt(11f, p.cDim, today.year.toString()).apply { setPadding(0, 0, dp(12), 0) })
    val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    bar.addView(View(this).apply { setBackgroundColor(p.cBlue) }, LinearLayout.LayoutParams(0, dp(5), pct.toFloat()))
    bar.addView(
        View(this).apply { setBackgroundColor(p.cDim and 0x00FFFFFF or (0x55 shl 24)) },
        LinearLayout.LayoutParams(0, dp(5), (100 - pct).toFloat())
    )
    prog.addView(bar, LinearLayout.LayoutParams(0, dp(5), 1f))
    prog.addView(txt(11f, p.cDim, "$pct%").apply { setPadding(dp(12), 0, 0, 0) })
    root.addView(prog, LinearLayout.LayoutParams(-1, -2))

    // Month grid.
    val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(grid, LinearLayout.LayoutParams(-1, -2))
    val monthLabel = txt(11f, p.cDim, "").apply { gravity = Gravity.CENTER }

    fun cell(text: String, color: Int, size: Float, weight: Float, h: Int = 34): TextView =
        txt(size, color, text).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(h), weight)
        }

    fun render(dir: Int = 0) {
        grid.removeAllViews()
        monthLabel.text = (shown.month.getDisplayName(TextStyle.FULL, loc) + " " + shown.year).uppercase(loc)
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hdr.addView(cell("W", p.cDim, 10f, 0.8f, 26))
        for (i in 0..6) {
            val name = wf.firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, loc).uppercase(loc).take(3)
            hdr.addView(cell(name, p.cDim, 10f, 1f, 26))
        }
        grid.addView(hdr, LinearLayout.LayoutParams(-1, -2))
        val start = calendarGridStart(shown, wf.firstDayOfWeek)
        for (r in 0 until 6) {
            val rowStart = start.plusDays(r * 7L)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(cell(rowStart.get(wf.weekOfYear()).toString(), p.cDim and 0x00FFFFFF or (0x99 shl 24), 10f, 0.8f))
            for (c in 0 until 7) {
                val d = rowStart.plusDays(c.toLong())
                val inMonth = YearMonth.from(d) == shown
                val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                val color = when {
                    !inMonth -> p.cDim and 0x00FFFFFF or (0x66 shl 24)
                    weekend -> p.cDim
                    else -> p.cFg
                }
                row.addView(cell(d.dayOfMonth.toString(), color, 13f, 1f).apply {
                    if (d == today) background = box(Color.TRANSPARENT, p.cDim)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        if (dir != 0) {
            grid.alpha = 0f
            grid.translationX = dir * dp(24).toFloat()
            grid.animate().alpha(1f).translationX(0f).setDuration(180).start()
        }
    }

    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, 0)
    }
    val prev = txt(20f, p.cDim, "\u2039").apply {
        setPadding(dp(14), dp(4), dp(14), dp(4))
        setOnClickListener { shown = shown.minusMonths(1); render(-1) }
    }
    val next = txt(20f, p.cDim, "\u203A").apply {
        setPadding(dp(14), dp(4), dp(14), dp(4))
        setOnClickListener { shown = shown.plusMonths(1); render(1) }
    }
    monthLabel.setOnClickListener { shown = YearMonth.from(today); render(0) }
    footer.addView(prev)
    footer.addView(monthLabel, LinearLayout.LayoutParams(0, -2, 1f))
    footer.addView(next)
    root.addView(footer, LinearLayout.LayoutParams(-1, -2))
    render()

    val dialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        setOnDismissListener {
            (clock as? ClockView)?.setActive(false)
            if (activePopup === this) activePopup = null
        }
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(480)), -2)
    }
    activePopup = dialog
    (clock as? ClockView)?.setActive(true)
    anchorDialogToTop(dialog, Gravity.CENTER_HORIZONTAL)
    installOutsideTapRouter(dialog, null)
}
