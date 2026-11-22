package com.kabigonis.sorairo

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import com.kabigonis.sorairo.MainActivity.WallhavenWallpaper
import com.kabigonis.sorairo.MainActivity.WallhavenPage

// Wallhaven online wallpaper source.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.wallhavenAllowedUrl(raw: String): java.net.URL {
    val url = java.net.URL(raw)
    val host = url.host.lowercase(Locale.ROOT)
    require(url.protocol.equals("https", true) && (host == "wallhaven.cc" || host.endsWith(".wallhaven.cc"))) { "untrusted Wallhaven URL" }
    return url
}

internal fun MainActivity.downloadWallhavenBytes(raw: String, destination: File): String {
    val initial = wallhavenAllowedUrl(raw)
    var current = initial
    var redirects = 0
    while (true) {
        val connection = (current.openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("User-Agent", "Sorairo/1.0")
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                require(redirects < 3) { "too many image redirects" }
                val location = connection.getHeaderField("Location") ?: error("invalid image redirect")
                val next = java.net.URL(current, location)
                val checked = wallhavenAllowedUrl(next.toString())
                require(checked.host.equals(initial.host, true)) { "cross-host image redirect blocked" }
                current = checked
                redirects++
                continue
            }
            require(code == 200) { "image download failed (HTTP " + code + ")" }
            val type = connection.contentType?.substringBefore(";")?.trim()?.lowercase(Locale.ROOT).orEmpty()
            require(type.startsWith("image/")) { "Wallhaven did not return an image" }
            require(connection.contentLengthLong <= 25L * 1024L * 1024L) { "image exceeds 25 MB" }
            destination.parentFile?.mkdirs()
            var total = 0L
            connection.inputStream.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 25L * 1024L * 1024L) { "image exceeds 25 MB" }
                    output.write(buffer, 0, count)
                }
            } }
            require(total > 0L) { "empty image" }
            return type
        } finally { connection.disconnect() }
    }
}

internal fun MainActivity.decodeWallhavenImage(file: File, maxDimension: Int): android.graphics.Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "invalid image dimensions" }
    require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 40_000_000L) { "image exceeds 40 megapixels" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("couldn't decode image")
}

internal fun MainActivity.wallhavenAccentColor(): String {
    val accent = pal.cAccent
    return wallhavenColors.minBy { hex ->
        val candidate = Color.parseColor("#" + hex)
        val dr = Color.red(accent) - Color.red(candidate)
        val dg = Color.green(accent) - Color.green(candidate)
        val db = Color.blue(accent) - Color.blue(candidate)
        dr * dr + dg * dg + db * db
    }
}

internal fun MainActivity.fetchWallhavenPage(query: String, sorting: String, color: String?, categories: String, page: Int): WallhavenPage {
    val key = listOf(query, sorting, color.orEmpty(), categories, page.toString()).joinToString("|")
    synchronized(wallhavenPageCache) { wallhavenPageCache[key]?.takeIf { System.currentTimeMillis() - it.fetchedAt < 30L * 60L * 1000L }?.let { return it } }
    synchronized(wallhavenThrottleLock) {
        val wait = 700L - (SystemClock.elapsedRealtime() - wallhavenLastApiCall)
        if (wait > 0L) Thread.sleep(wait)
        wallhavenLastApiCall = SystemClock.elapsedRealtime()
    }
    val params = linkedMapOf("q" to query, "categories" to categories, "purity" to "100", "sorting" to sorting, "atleast" to "1920x1080", "ratios" to "16x9,16x10", "page" to page.toString())
    if (sorting == "toplist") params["topRange"] = "1M"
    if (!color.isNullOrBlank()) params["colors"] = color
    val queryString = params.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
    val connection = (java.net.URL("https://wallhaven.cc/api/v1/search?" + queryString).openConnection() as java.net.HttpURLConnection).apply {
        instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 20000
        requestMethod = "GET"; setRequestProperty("Accept", "application/json"); setRequestProperty("User-Agent", "Sorairo/1.0")
    }
    return try {
        val code = connection.responseCode
        if (code == 429) throw IllegalStateException("slow down, try again in a minute")
        require(code == 200) { "Wallhaven search failed (HTTP " + code + ")" }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val data = json.optJSONArray("data") ?: JSONArray()
        val items = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id"); val pageUrl = item.optString("url"); val path = item.optString("path")
            val resolution = item.optString("resolution"); val thumbs = item.optJSONObject("thumbs") ?: JSONObject()
            if (id.isBlank() || pageUrl.isBlank() || path.isBlank() || resolution.isBlank()) null else runCatching {
                val safePath = wallhavenAllowedUrl(path); val safePage = wallhavenAllowedUrl(pageUrl)
                require(safePage.host.equals("wallhaven.cc", true)) { "invalid Wallhaven page URL" }
                WallhavenWallpaper(id, safePage.toString(), resolution, item.optLong("file_size", 0L), thumbs, safePath.toString())
            }.getOrNull()
        }
        val meta = json.optJSONObject("meta")
        WallhavenPage(items, (meta?.optInt("last_page", page) ?: page).coerceAtLeast(page), System.currentTimeMillis()).also {
            synchronized(wallhavenPageCache) { wallhavenPageCache[key] = it }
        }
    } finally { connection.disconnect() }
}

internal fun MainActivity.wallhavenThemeQuery(): String {
    val index = prefs.getInt("theme", 0).coerceIn(0, pals.size - 1)
    val name = pal.name
    val id = when {
        index < basePals.size -> ""
        index < builtInCount -> {
            val plain = basePals.map { it.name.lowercase().replace(" ", "") }.toSet()
            OmarchyThemes.loadBundled(this).filter { !it.isLight && it.name.lowercase().replace(" ", "") !in plain }
                .getOrNull(index - basePals.size)?.id.orEmpty()
        }
        else -> CommunityThemes.installedCatalog(this, communityCatalog).getOrNull(index - builtInCount)?.id.orEmpty()
    }
    val ignored = setOf("theme", "dark", "light", "night", "the", "of")
    val words = (name + " " + id).lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in ignored }
    return (ThemeKeywords.forId(id) + " " + words.joinToString(" ")).trim().split(Regex("\\s+")).distinct().joinToString(" ")
}

internal fun MainActivity.effectiveWallhavenQuery(): String {
    val typed = wallhavenQuery.trim()
    return if (typed.isNotBlank()) typed else if (wallhavenMatchTheme) wallhavenThemeQuery() else ""
}

internal fun MainActivity.cancelWallhavenThumbs() {
    wallhavenThumbJobs.forEach { it.cancel(true) }
    wallhavenThumbJobs.clear()
}

internal fun MainActivity.buildWallhavenSection(): View {
    val p = pal
    val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(12)) }
    val query = EditText(this).apply {
        setSingleLine(true); textSize = 13f; setTextColor(p.cFg); setHintTextColor(p.cDim)
        hint = "search Wallhaven"; setText(wallhavenQuery); imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = InputType.TYPE_CLASS_TEXT
    }
    section.addView(query, LinearLayout.LayoutParams(-1, dp(44)))
    val options = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
    fun toggle(label: String, enabled: Boolean, action: () -> Unit) = txt(11f, if (enabled) p.cAccent else p.cDim, "[ " + label + if (enabled) " ✓ ]" else " ]").apply {
        setPadding(dp(4), dp(8), dp(9), dp(8)); setOnClickListener { action(); wallhavenPage = 1; wallhavenResults = emptyList(); wallhavenStatus = ""; fillWallpaperSettings() }
    }
    options.addView(toggle("match theme", wallhavenMatchTheme) { wallhavenMatchTheme = !wallhavenMatchTheme })
    options.addView(toggle("colors", wallhavenColorsEnabled) { wallhavenColorsEnabled = !wallhavenColorsEnabled })
    options.addView(toggle("people", wallhavenPeopleEnabled) { wallhavenPeopleEnabled = !wallhavenPeopleEnabled })
    options.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    options.addView(txt(11f, p.cBlue, "[ " + wallhavenSort + " ]").apply {
        setPadding(dp(8), dp(8), dp(4), dp(8))
        setOnClickListener { wallhavenSort = when (wallhavenSort) { "toplist" -> "date_added"; "date_added" -> "relevance"; else -> "toplist" }; wallhavenPage = 1; wallhavenResults = emptyList(); wallhavenStatus = ""; fillWallpaperSettings() }
    })
    section.addView(options)
    section.addView(txt(10f, p.cDim, "searching: " + effectiveWallhavenQuery().ifBlank { "all wallpapers" } + " · colors " + if (wallhavenColorsEnabled) "on" else "off").apply { setPadding(dp(2), 0, 0, dp(4)) })
    val status = txt(10f, p.cDim, wallhavenStatus.ifBlank { "SFW wallpapers from Wallhaven" }).apply { setPadding(dp(2), 0, 0, dp(6)) }
    section.addView(status)
    val grid = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@buildWallhavenSection, 3); isNestedScrollingEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
    section.addView(grid); renderWallhavenCards(wallhavenResults, grid)
    val more = txt(12f, p.cAccent, "[ more ]").apply {
        gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10))
        visibility = if (wallhavenPage < wallhavenLastPage && wallhavenResults.isNotEmpty()) View.VISIBLE else View.GONE
        setOnClickListener { if (wallhavenPage < wallhavenLastPage) { wallhavenPage++; loadWallhavenPage(wallhavenPage, true, status, grid, this) } }
    }
    query.setOnEditorActionListener { view, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_SEARCH || (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP)) {
            wallhavenQuery = view.text.toString().trim(); wallhavenPage = 1; wallhavenResults = emptyList()
            loadWallhavenPage(1, false, status, grid, more)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0); true
        } else false
    }
    section.addView(more)
    if (wallhavenResults.isEmpty() && wallhavenStatus.isBlank()) section.post { if (wallpaperPageOpen) loadWallhavenPage(1, false, status, grid, more) }
    return section
}

internal fun MainActivity.loadWallhavenPage(page: Int, append: Boolean, status: TextView, grid: RecyclerView, more: TextView) {
    if (!append) { wallhavenGeneration++; cancelWallhavenThumbs() }
    val generation = wallhavenGeneration
    val query = effectiveWallhavenQuery()
    val color = if (wallhavenColorsEnabled) wallhavenAccentColor() else null
    val categories = if (wallhavenPeopleEnabled) "111" else "110"
    status.text = "searching Wallhaven..."
    wallhavenApiExecutor.execute {
        val result = runCatching {
            val first = fetchWallhavenPage(query, wallhavenSort, color, categories, page)
            if (!append && first.items.size < 8 && !color.isNullOrBlank()) {
                val qOnly = fetchWallhavenPage(query, wallhavenSort, null, categories, page)
                if (qOnly.items.size >= 8) qOnly else {
                    val cOnly = fetchWallhavenPage("", wallhavenSort, color, categories, page)
                    if (cOnly.items.size > qOnly.items.size) cOnly else qOnly
                }
            } else first
        }
        ui {
            if (generation != wallhavenGeneration || !wallpaperPageOpen || wallpaperTab != "online") return@ui
            result.onSuccess { data ->
                wallhavenLastPage = data.lastPage; wallhavenResults = if (append) wallhavenResults + data.items else data.items
                wallhavenPage = page; wallhavenStatus = wallhavenResults.size.toString() + " results · page " + page + " of " + data.lastPage
                status.text = wallhavenStatus; renderWallhavenCards(wallhavenResults, grid)
                more.visibility = if (page < data.lastPage) View.VISIBLE else View.GONE
            }.onFailure { error -> wallhavenStatus = error.message ?: "couldn't search Wallhaven"; status.text = wallhavenStatus; more.visibility = View.GONE }
        }
    }
}

internal fun MainActivity.renderWallhavenCards(items: List<WallhavenWallpaper>, grid: RecyclerView) {
    grid.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(3), dp(3), dp(3)); layoutParams = RecyclerView.LayoutParams(-1, dp(122)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) } }
            card.addView(ImageView(parent.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = box(pal.cBg, pal.cDim) }, LinearLayout.LayoutParams(-1, dp(94)))
            card.addView(txt(9f, pal.cDim, "").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(2), dp(2), dp(2), 0) })
            return object : RecyclerView.ViewHolder(card) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val card = holder.itemView as LinearLayout
            val image = card.getChildAt(0) as ImageView
            (card.getChildAt(1) as TextView).text = item.resolution + " · " + item.id
            val thumbUrl = runCatching {
                wallhavenAllowedUrl(item.thumbs.optString("small").ifBlank { item.thumbs.optString("large") }).toString()
            }.getOrNull().orEmpty()
            image.tag = thumbUrl
            val cacheKey = "wallhaven:" + item.id
            val memory = synchronized(wallpaperThumbCache) { wallpaperThumbCache.get(cacheKey) }
            if (memory != null) image.setImageBitmap(memory) else {
                val cached = File(File(cacheDir, "wallhaven-thumbs"), item.id + ".img")
                val job = wallpaperExecutor.submit {
                    val bitmap = runCatching {
                        require(thumbUrl.isNotBlank()) { "invalid thumbnail URL" }
                        if (!cached.isFile) downloadWallhavenBytes(thumbUrl, cached) else cached.setLastModified(System.currentTimeMillis())
                        decodeWallhavenImage(cached, 480)
                    }.getOrNull()
                    if (bitmap != null && !Thread.currentThread().isInterrupted) {
                        synchronized(wallpaperThumbCache) { wallpaperThumbCache.put(cacheKey, bitmap) }
                        ui { if (image.tag == thumbUrl) image.setImageBitmap(bitmap) }
                    }
                    runCatching {
                        val files = cached.parentFile?.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
                        var bytes = files.sumOf { it.length() }
                        for (file in files) { if (bytes <= 40L * 1024L * 1024L) break; bytes -= file.length(); file.delete() }
                    }
                }
                wallhavenThumbJobs.add(job)
            }
            card.setOnClickListener { applyWallhavenFromCard(item, card, true) }
            card.setOnLongClickListener { applyWallhavenFromCard(item, card, false); true }
        }
    }
    grid.post {
        val columns = (grid.width / dp(132)).coerceIn(1, 10)
        (grid.layoutManager as? GridLayoutManager)?.spanCount = columns
        val rows = (items.size + columns - 1) / columns
        grid.layoutParams = grid.layoutParams.apply { height = rows * dp(126) }
    }
}

internal fun MainActivity.applyWallhavenFromCard(item: WallhavenWallpaper, card: LinearLayout, apply: Boolean) {
    val existing = readWallpaperIndex().firstOrNull { it.source == "wallhaven" && it.remoteName == item.id }
    if (existing != null) { if (apply) applyWallpaper(existing.id, applyToSystem = true); return }
    if (!wallhavenBusy.add(item.id)) return
    val progress = ProgressBar(this).apply { isIndeterminate = true }
    card.addView(progress, LinearLayout.LayoutParams(dp(20), dp(20)).apply { gravity = Gravity.CENTER })
    wallhavenExecutor.execute {
        val result = runCatching {
            if (!isWallpaperDownloadAllowed()) throw IllegalStateException("wallpapers not downloaded (mobile data)")
            require(item.fileSize in 1..(25L * 1024L * 1024L)) { "image exceeds 25 MB" }
            val temp = File(cacheDir, "wallhaven-" + item.id + "-" + UUID.randomUUID() + ".tmp")
            try {
                downloadWallhavenBytes(item.path, temp)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "invalid image dimensions" }
                require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 40_000_000L) { "image exceeds 40 megapixels" }
                val credit = "wallhaven.cc/w/" + item.id + " · " + item.resolution
                importWallpaperFile(temp, "wallhaven", credit, item.url, item.id, item.fileSize)
            } finally { temp.delete() }
        }
        ui {
            wallhavenBusy.remove(item.id)
            card.removeView(progress)
            result.onSuccess { entry -> if (apply) applyWallpaper(entry.id, applyToSystem = true) }
                .onFailure { error -> Toast.makeText(this, error.message ?: "couldn't download wallpaper", Toast.LENGTH_LONG).show() }
        }
    }
}
