package com.kabigonis.sorairo

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

// Community theme browser UI: catalog, grid, cards, install and remove.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.communityPageSize(): Int {
    val columns = ((resources.displayMetrics.widthPixels - dp(112)) / dp(170)).coerceIn(2, 8)
    return columns * 4
}

// Start with the normal preview size. Once Android has measured the actual
// fixed-size window, fit the first four rows into its remaining space.
internal fun MainActivity.communityThumbHeight(): Int = dp(112)

internal fun MainActivity.fitInitialCommunityPage(grid: LinearLayout) {
    if (communityShown > communityPageSize()) return
    setScroll.post {
        if (!setScrollInitialized || setScroll.height <= 0) return@post
        val rows = (0 until minOf(4, grid.childCount))
            .mapNotNull { grid.getChildAt(it) as? LinearLayout }
            .filter { it.childCount > 0 && it.getChildAt(0) is LinearLayout }
        if (rows.isEmpty()) return@post

        // The ScrollView measures the complete content. Reduce only preview
        // images by the measured overflow; keep titles, labels and controls intact.
        val overflow = setBox.measuredHeight - setScroll.height
        if (overflow <= 0) return@post
        val reductionPerRow = (overflow + dp(8) + rows.size - 1) / rows.size
        rows.forEach { row ->
            for (i in 0 until row.childCount) {
                val card = row.getChildAt(i) as? LinearLayout ?: continue
                val image = card.getChildAt(0) as? ImageView ?: continue
                val lp = image.layoutParams
                lp.height = (lp.height - reductionPerRow).coerceAtLeast(dp(20))
                image.layoutParams = lp
            }
        }
        setBox.requestLayout()
        setScroll.post {
            // A second small correction accounts for font metrics and rounding.
            val remaining = setBox.measuredHeight - setScroll.height
            if (remaining > 0) {
                val correction = (remaining + rows.size - 1) / rows.size
                rows.forEach { row ->
                    for (i in 0 until row.childCount) {
                        val card = row.getChildAt(i) as? LinearLayout ?: continue
                        val image = card.getChildAt(0) as? ImageView ?: continue
                        val lp = image.layoutParams
                        lp.height = (lp.height - correction).coerceAtLeast(dp(12))
                        image.layoutParams = lp
                    }
                }
                setBox.requestLayout()
            }
        }
    }
}

internal fun MainActivity.fillCommunityThemes() {
    setSettingsWide(true)
    communityShown = communityPageSize()
    setScroll.clipChildren = true
    setScroll.clipToPadding = true
    setScroll.isFillViewport = true
    setScroll.isVerticalScrollBarEnabled = false
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/config/community-themes"))
    setBox.addView(
        txt(11f, p.cDim, "made by the community, listed on omarchy.org/themes. tap one to install and apply it. hold an installed one to remove it.").apply {
            setPadding(0, dp(2), 0, dp(6))
        }
    )
    setBox.addView(txt(14f, p.cAccent, "← back").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener {
            switchSettingsContent {
                fillStyleSettings()
                setScroll.scrollTo(0, 0)
            }
        }
    })
    val search = EditText(this).apply {
        hint = "search themes"
        setHintTextColor(p.cDim)
        setTextColor(p.cFg)
        typeface = font
        textSize = 14f
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        background = box(Color.TRANSPARENT, p.cDim)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setText(communityQuery)
    }
    val tabs = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(2))
    }
    val status = txt(11f, p.cDim, communityStatus).apply { setPadding(0, dp(6), 0, dp(2)) }
    val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    setBox.addView(tabs)
    setBox.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    setBox.addView(status)
    setBox.addView(grid)
    search.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            communityQuery = s?.toString().orEmpty()
            communityShown = communityPageSize()
            renderCommunityGrid(grid, status, tabs)
        }
    })
    renderCommunityGrid(grid, status, tabs)
    // Keep the established full-height outer window; the ScrollView
    // handles overflow so the header, search and remaining themes scroll inside it.
    setScroll.post {
        setScroll.scrollTo(0, 0)
        setBox.alpha = 0f
        setBox.animate().alpha(1f).setDuration(120).start()
    }
    loadCommunityCatalog { renderCommunityGrid(grid, status, tabs) }
}

// Shows the saved list straight away, then refreshes it from omarchy.org when it is stale.
internal fun MainActivity.loadCommunityCatalog(onChange: () -> Unit) {
    if (communityCatalog.isEmpty()) {
        communityStatus = "loading..."
        onChange()
    }
    netPool.execute {
        var have = communityCatalog
        if (have.isEmpty()) {
            have = CommunityThemes.cachedCatalog(this)
            if (have.isNotEmpty()) ui { communityCatalog = have; communityStatus = ""; onChange() }
        }
        if (have.isEmpty() || !CommunityThemes.catalogIsFresh(this)) {
            try {
                val fresh = CommunityThemes.fetchCatalog(this)
                ui { communityCatalog = fresh; communityStatus = ""; onChange() }
            } catch (e: Exception) {
                ui {
                    communityStatus = if (communityCatalog.isEmpty()) {
                        "couldn't load the list (${e.message}). check your connection."
                    } else {
                        "offline, showing the saved list"
                    }
                    onChange()
                }
            }
        }
    }
}

internal fun MainActivity.renderCommunityGrid(
    grid: LinearLayout,
    status: TextView,
    tabs: LinearLayout,
    animateOut: Boolean = true
) {
    if (animateOut && grid.childCount > 0) {
        val generation = ++communityRenderGeneration
        grid.animate().cancel()
        grid.animate().alpha(0f).setDuration(90).withEndAction {
            if (generation == communityRenderGeneration) {
                renderCommunityGrid(grid, status, tabs, animateOut = false)
            }
        }.start()
        return
    }
    val p = pal
    grid.animate().cancel()
    grid.alpha = 0f
    grid.removeAllViews()
    tabs.removeAllViews()
    val installedIds = CommunityThemes.installedIds(this)
    fun addTab(label: String, id: String, count: Int) {
        val selected = communityTab == id
        tabs.addView(txt(13f, if (selected) p.cAccent else p.cDim, "${label} (${count})").apply {
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = if (selected) box(Color.TRANSPARENT, p.cAccent) else null
            setOnClickListener {
                communityTab = id
                communityShown = communityPageSize()
                renderCommunityGrid(grid, status, tabs)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    val installedThemes = CommunityThemes.installedCatalog(this, communityCatalog)
    addTab("Browse", "browse", communityCatalog.size)
    addTab("Installed", "installed", installedThemes.size)
    val source = if (communityTab == "installed") installedThemes else communityCatalog
    val q = communityQuery.trim().lowercase()
    val matches = source.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.label.lowercase().contains(q)
    }
    status.text = when {
        communityStatus.isNotEmpty() -> communityStatus
        communityCatalog.isEmpty() -> ""
        matches.isEmpty() -> "nothing matches \"$communityQuery\""
        else -> if (communityTab == "installed") "${matches.size} installed themes" else "${matches.size} themes"
    }
    val installed = installedIds
    val activeName = pal.name
    val shown = matches.take(communityShown)
    val columns = ((resources.displayMetrics.widthPixels - dp(112)) / dp(170)).coerceIn(2, 8)
    var animationIndex = 0
    shown.chunked(columns).forEach { cards ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        cards.forEachIndexed { i, t ->
            val isInstalled = t.id in installed
            val card = communityCard(t, isInstalled, isInstalled && t.title == activeName)
            row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i > 0) marginStart = dp(4)
                if (i < cards.lastIndex) marginEnd = dp(4)
            })
        }
        row.alpha = 0f
        grid.addView(row)
        row.animate().alpha(1f).setStartDelay((animationIndex++ * 18L).coerceAtMost(180L))
            .setDuration(120).start()
    }
    if (matches.size > shown.size) {
        val more = txt(14f, p.cAccent, "show more (${matches.size - shown.size} left) ›").apply {
            setPadding(0, dp(12), 0, dp(8))
            alpha = 0f
            setOnClickListener {
                communityShown += communityPageSize()
                renderCommunityGrid(grid, status, tabs)
            }
        }
        grid.addView(more)
        more.animate().alpha(1f).setStartDelay((animationIndex * 18L).coerceAtMost(180L))
            .setDuration(120).start()
    }
    grid.animate().alpha(1f).setDuration(120).start()
    // The initial page must fit completely, including the Show More row.
    // After the user loads more, normal scrolling can reveal added rows.
    fitInitialCommunityPage(grid)
}

internal fun MainActivity.communityCard(t: CatalogTheme, installed: Boolean, active: Boolean): View {
    val p = pal
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(if (active) 2 else 1), if (active) p.cAccent else p.cDim)
        }
    }
    val img = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor((p.cDim and 0x00FFFFFF) or (0x33 shl 24))
    }
    card.addView(img, LinearLayout.LayoutParams(-1, communityThumbHeight()))
    card.addView(txt(13f, p.cFg, t.title).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(5), 0, 0)
    })
    card.addView(txt(10f, p.cDim, t.label).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    })
    val state = txt(
        11f,
        if (active) p.cAccent else if (installed) p.cGreen else p.cDim,
        if (active) "active" else if (installed) "installed" else "tap to install"
    ).apply { setPadding(0, dp(3), 0, 0) }
    card.addView(state)
    card.setOnClickListener {
        if (installed) {
            installCommunityTheme(t, state)
        } else {
            showSorairoConfirmDialog(
                "Install theme?",
                "Install and apply ${t.title}? This also downloads its wallpapers on Wi-Fi.",
                "Install"
            ) { installCommunityTheme(t, state) }
        }
    }
    card.setOnLongClickListener {
        if (installed) {
            showSorairoConfirmDialog(
                "Uninstall theme?",
                "Remove ${t.title} from your installed community themes?",
                "Uninstall"
            ) { removeCommunityTheme(t) }
            true
        } else false
    }
    loadThumb(t, img)
    return card
}

internal fun MainActivity.loadThumb(t: CatalogTheme, img: ImageView) {
    img.tag = t.key
    themeThumbs.get(t.key)?.let { img.setImageBitmap(it); return }
    netPool.execute {
        val file = CommunityThemes.imageFile(this, t) ?: return@execute
        val bmp = decodeThumb(file) ?: return@execute
        themeThumbs.put(t.key, bmp)
        ui { if (img.tag == t.key) img.setImageBitmap(bmp) }
    }
}

internal fun MainActivity.decodeThumb(file: File): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 560) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

internal fun MainActivity.installCommunityTheme(t: CatalogTheme, state: TextView) {
    if (!communityBusy.add(t.key)) return
    state.text = "downloading..."
    netPool.execute {
        val result = runCatching { CommunityThemes.install(this, t) }
        ui {
            communityBusy.remove(t.key)
            result.onSuccess { palette ->
                invalidatePals()
                val index = pals.indexOfLast { it.name == palette.name }.coerceAtLeast(0)
                prefs.edit().putInt("theme", index).apply()
                rebuild(false)
                onThemeApplied(index, communityInstalled = true)
                fillCommunityThemes()
                showOverlay()
                Toast.makeText(this, "applied ${palette.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                state.text = "failed, tap to retry"
                Toast.makeText(this, "couldn't install ${t.title}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

internal fun MainActivity.removeCommunityTheme(t: CatalogTheme) {
    val activeIndex = prefs.getInt("theme", 0).coerceIn(0, pals.size - 1)
    val activeName = pals[activeIndex].name
    CommunityThemes.remove(this, t.id)
    invalidatePals()
    val newIndex = when {
        activeIndex < builtInCount -> activeIndex
        activeName == t.title -> 0
        else -> pals.indexOfLast { it.name == activeName }.takeIf { it >= builtInCount } ?: 0
    }
    val activeChanged = pals.getOrNull(newIndex)?.name != activeName
    prefs.edit().putInt("theme", newIndex).apply()
    if (activeChanged) rebuild(false)
    fillCommunityThemes()
    showOverlay()
    Toast.makeText(this, "removed ${t.title}", Toast.LENGTH_SHORT).show()
}
