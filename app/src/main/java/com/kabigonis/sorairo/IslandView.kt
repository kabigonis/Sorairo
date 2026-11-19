package com.kabigonis.sorairo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Fixed transparent hit-test host; only the pill is resized. */
class IslandView(
    context: Context,
    private var maxWidthPx: Int,
    private var collapsedHeightPx: Int,
    private var expandedHeightPx: Int,
    private var dimColor: Int,
    private var fillColor: Int,
    private var textColor: Int,
    private var islandFont: Typeface,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit
) : FrameLayout(context) {
    private val pill = FrameLayout(context)
    private val collapsedText = TextView(context)
    private val expandedContent = LinearLayout(context)
    private val timeText = TextView(context)
    private val dateText = TextView(context)
    private val titleText = TextView(context)
    private val subtitleText = TextView(context)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val animator = ValueAnimator()
    private var collapsedWidthPx = dp(160)
    private var targetExpanded = false
    private var trackingPill = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        pill.background = pillBackground()
        pill.clipToOutline = true
        collapsedText.apply {
            gravity = Gravity.CENTER
            textSize = 13f
            maxLines = 1
            setTextColor(textColor)
            typeface = islandFont
            setPadding(dp(12), 0, dp(12), 0)
        }
        expandedContent.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            alpha = 0f
            visibility = View.GONE
        }
        timeText.apply { textSize = 24f; maxLines = 1; setTextColor(textColor); typeface = islandFont }
        dateText.apply { textSize = 10f; maxLines = 1; setTextColor(dimColor); typeface = islandFont }
        titleText.apply { textSize = 13f; maxLines = 1; setTextColor(textColor); typeface = islandFont }
        subtitleText.apply { textSize = 11f; maxLines = 1; setTextColor(dimColor); typeface = islandFont }
        progress.max = 100
        progress.visibility = View.GONE
        expandedContent.addView(timeText, LinearLayout.LayoutParams(-1, -2))
        expandedContent.addView(dateText, LinearLayout.LayoutParams(-1, -2))
        expandedContent.addView(titleText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        expandedContent.addView(subtitleText, LinearLayout.LayoutParams(-1, -2))
        expandedContent.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(5) })
        pill.addView(collapsedText, FrameLayout.LayoutParams(-1, -1))
        pill.addView(expandedContent, FrameLayout.LayoutParams(-1, -1))
        addView(pill, LayoutParams(collapsedWidthPx, collapsedHeightPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { onTap(); return true }
            override fun onLongPress(e: MotionEvent) { onLongPress() }
        })
        pill.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            gestures.onTouchEvent(event)
            true
        }
        animator.duration = 220L
        animator.interpolator = DecelerateInterpolator()
    }

    fun updateStyle(maxWidth: Int, collapsedHeight: Int, expandedHeight: Int,
                    dim: Int, fill: Int, foreground: Int, font: Typeface) {
        maxWidthPx = maxWidth.coerceAtLeast(dp(120))
        collapsedHeightPx = collapsedHeight
        expandedHeightPx = expandedHeight
        dimColor = dim
        fillColor = fill
        textColor = foreground
        islandFont = font
        pill.background = pillBackground()
        collapsedText.setTextColor(foreground); collapsedText.typeface = font
        timeText.setTextColor(foreground); timeText.typeface = font
        dateText.setTextColor(dim); dateText.typeface = font
        titleText.setTextColor(foreground); titleText.typeface = font
        subtitleText.setTextColor(dim); subtitleText.typeface = font
        val lp = pill.layoutParams as? LayoutParams
        if (lp != null) {
            lp.width = if (targetExpanded) maxWidthPx else collapsedWidthPx
            lp.height = if (targetExpanded) expandedHeightPx else collapsedHeightPx
            pill.layoutParams = lp
        }
    }

    fun render(snapshot: IslandSnapshot, clock: String, date: String, emptyExpanded: Boolean) {
        collapsedText.text = clock
        collapsedWidthPx = minOf(
            maxWidthPx,
            maxOf(dp(160), collapsedText.paint.measureText(clock).toInt() + dp(24))
        )
        if (!targetExpanded) {
            val lp = pill.layoutParams as LayoutParams
            if (lp.width != collapsedWidthPx) {
                lp.width = collapsedWidthPx
                pill.layoutParams = lp
            }
        }
        timeText.text = clock.substringAfter(' ').replaceFirstChar { it.uppercase() }
        dateText.text = date
        val activity = snapshot.current
        titleText.text = activity?.title ?: "nothing going on"
        subtitleText.text = activity?.subtitle.orEmpty()
        subtitleText.visibility = if (activity?.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        progress.visibility = if (activity?.progress != null) View.VISIBLE else View.GONE
        if (activity?.progress != null) progress.progress = (activity.progress * 100f).toInt()
        contentDescription = buildString {
            append("Dynamic Island. ")
            append(if (snapshot.state == IslandState.COLLAPSED && !emptyExpanded) "Collapsed. " else "Expanded. ")
            append(activity?.title ?: "Nothing going on")
            if (!activity?.subtitle.isNullOrBlank()) append(". ").append(activity?.subtitle)
            append(". Tap to expand or collapse. Long press for settings.")
        }
        setExpanded(snapshot.state != IslandState.COLLAPSED || emptyExpanded)
    }

    private fun setExpanded(expanded: Boolean) {
        if (targetExpanded == expanded) return
        targetExpanded = expanded
        animator.cancel()
        val startWidth = (pill.layoutParams as LayoutParams).width
        val startHeight = (pill.layoutParams as LayoutParams).height
        val endWidth = if (expanded) maxWidthPx else collapsedWidthPx
        val endHeight = if (expanded) expandedHeightPx else collapsedHeightPx
        if (!ValueAnimator.areAnimatorsEnabled()) {
            val lp = pill.layoutParams as LayoutParams
            lp.width = endWidth; lp.height = endHeight; pill.layoutParams = lp
            expandedContent.alpha = if (expanded) 1f else 0f
            collapsedText.alpha = if (expanded) 0f else 1f
            expandedContent.visibility = if (expanded) View.VISIBLE else View.GONE
            collapsedText.visibility = if (expanded) View.GONE else View.VISIBLE
            return
        }
        expandedContent.visibility = View.VISIBLE
        collapsedText.visibility = View.VISIBLE
        animator.removeAllUpdateListeners()
        animator.addUpdateListener { value ->
            val fraction = value.animatedValue as Float
            val lp = pill.layoutParams as LayoutParams
            lp.width = (startWidth + (endWidth - startWidth) * fraction).toInt()
            lp.height = (startHeight + (endHeight - startHeight) * fraction).toInt()
            pill.layoutParams = lp
            expandedContent.alpha = if (expanded) fraction else 1f - fraction
            collapsedText.alpha = if (expanded) 1f - fraction else fraction
        }
        animator.removeAllListeners()
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                expandedContent.visibility = if (expanded) View.VISIBLE else View.GONE
                collapsedText.visibility = if (expanded) View.GONE else View.VISIBLE
            }
        })
        animator.setFloatValues(0f, 1f)
        animator.duration = 220L
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingPill = event.x >= pill.left && event.x < pill.right &&
                    event.y >= pill.top && event.y < pill.bottom
                if (!trackingPill) return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!trackingPill) return false
            else -> if (!trackingPill) return false
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) trackingPill = false
        return handled
    }

    private fun pillBackground() = GradientDrawable().apply {
        setColor(fillColor)
        setStroke(dp(1), dimColor)
        cornerRadius = dp(22).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
