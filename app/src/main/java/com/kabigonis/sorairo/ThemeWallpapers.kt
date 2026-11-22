package com.kabigonis.sorairo

import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import com.kabigonis.sorairo.MainActivity.WallpaperEntry
import com.kabigonis.sorairo.MainActivity.ThemeWallpaperSource
import com.kabigonis.sorairo.MainActivity.ThemeWallpaperRemoteFile

// Theme wallpaper source: remote lists, caches, previews, downloads and apply-on-theme-change.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.currentThemeWallpaperSource(): ThemeWallpaperSource? {
    val index = prefs.getInt("theme", 0).coerceIn(0, pals.size - 1)
    val themeName = pal.name
    if (index >= builtInCount) {
        val installed = CommunityThemes.installedCatalog(this, communityCatalog)
        val theme = installed.getOrNull(index - builtInCount) ?: return null
        return ThemeWallpaperSource(theme.id, theme.title, theme.owner, theme.repo, "backgrounds")
    }
    if (index >= basePals.size) {
        val have = basePals.map { it.name.lowercase().replace(" ", "") }.toSet()
        val bundled = OmarchyThemes.loadBundled(this).filter {
            !it.isLight && it.name.lowercase().replace(" ", "") !in have
        }
        val theme = bundled.getOrNull(index - basePals.size) ?: return null
        return ThemeWallpaperSource(theme.id, themeName, "basecamp", "omarchy", "themes/${theme.id}/backgrounds", true)
    }
    return null
}

internal fun MainActivity.themeWallpaperCacheFile(source: ThemeWallpaperSource): File {
    val key = "${source.owner}_${source.repo}_${source.path}".replace(Regex("[^A-Za-z0-9_.-]"), "_")
    return File(cacheDir, "theme-wallpaper-list-$key.json")
}

internal fun MainActivity.readThemeWallpaperCache(source: ThemeWallpaperSource): List<ThemeWallpaperRemoteFile>? {
    val file = themeWallpaperCacheFile(source)
    if (!file.isFile || System.currentTimeMillis() - file.lastModified() >= 24L * 60L * 60L * 1000L) return null
    return runCatching {
        val array = org.json.JSONArray(file.readText())
        (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            ThemeWallpaperRemoteFile(
                item.getString("name"), item.getLong("size"),
                item.getString("download"), item.getString("html")
            )
        }.sortedBy { it.name.lowercase() }.take(12)
    }.getOrNull()
}

internal fun MainActivity.writeThemeWallpaperCache(source: ThemeWallpaperSource, files: List<ThemeWallpaperRemoteFile>) {
    val array = org.json.JSONArray()
    files.forEach { item ->
        array.put(JSONObject().put("name", item.name).put("size", item.size)
            .put("download", item.downloadUrl).put("html", item.htmlUrl))
    }
    val file = themeWallpaperCacheFile(source)
    file.parentFile?.mkdirs()
    file.writeText(array.toString())
}

internal fun MainActivity.buildThemeWallpaperSection(): View {
    val p = pal
    val source = currentThemeWallpaperSource()
    val sourceKey = source?.let { "${it.owner}/${it.repo}/${it.path}" }
    if (sourceKey != themeWallpaperLoadedFor) themeWallpaperShown = 8
    val section = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(14))
    }
    section.addView(txt(13f, p.cBlue, "from ${pal.name}"))
    val status = txt(11f, p.cDim, themeWallpaperStatus).apply {
        setPadding(0, dp(3), 0, dp(5))
        setOnClickListener {
            if (themeWallpaperStatus == "wallpapers not downloaded (mobile data)" && source != null) {
                beginThemeWallpaperDownloads(source, firstImmediately = false, allowMetered = true)
            }
        }
    }
    section.addView(status)
    val grid = RecyclerView(this).apply {
        layoutManager = GridLayoutManager(this@buildThemeWallpaperSection, 3)
        isNestedScrollingEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    val more = txt(12f, p.cAccent, "show more").apply {
        setPadding(0, dp(6), 0, dp(4))
        visibility = View.GONE
        setOnClickListener {
            themeWallpaperShown += 8
            themeWallpaperAllowPreviewLoad = true
            fillWallpaperSettings()
        }
    }
    if (source == null) {
        status.text = "no wallpapers listed for this theme"
    } else {
        val cached = if (themeWallpaperLoadedFor == "${source.owner}/${source.repo}/${source.path}") {
            themeWallpaperFiles
        } else readThemeWallpaperCache(source)
        if (cached != null) {
            themeWallpaperFiles = cached
            themeWallpaperLoadedFor = "${source.owner}/${source.repo}/${source.path}"
            status.text = if (cached.isEmpty()) "no wallpapers listed for this theme" else "${cached.size} wallpapers"
            renderThemeWallpaperCards(cached, grid, more, source, allowDownloads = themeWallpaperAllowPreviewLoad)
            themeWallpaperAllowPreviewLoad = false
        } else if (themeWallpaperLoadedFor == "${source.owner}/${source.repo}/${source.path}" &&
            themeWallpaperFiles.isNotEmpty()) {
            status.text = "${themeWallpaperFiles.size} wallpapers"
            renderThemeWallpaperCards(themeWallpaperFiles, grid, more, source, allowDownloads = themeWallpaperAllowPreviewLoad)
            themeWallpaperAllowPreviewLoad = false
        }
    }
    section.addView(grid)
    section.addView(more)
    if (source != null) {
        val key = source.owner + "/" + source.repo + "/" + source.path
        val cached = if (themeWallpaperLoadedFor == key) themeWallpaperFiles else readThemeWallpaperCache(source)
        if (cached != null) {
            themeWallpaperFiles = cached
            themeWallpaperLoadedFor = key
            status.text = if (cached.isEmpty()) "no wallpapers listed for this theme" else cached.size.toString() + " wallpapers"
            renderThemeWallpaperCards(cached, grid, more, source, allowDownloads = true)
        } else {
            loadThemeWallpapers(source, status, grid, more)
        }
    }
    return section
}

internal fun MainActivity.loadThemeWallpapers(
    source: ThemeWallpaperSource, status: TextView, grid: RecyclerView, more: TextView
) {
    val key = "${source.owner}/${source.repo}/${source.path}"
    val cached = readThemeWallpaperCache(source)
    if (cached != null) {
        themeWallpaperFiles = cached
        themeWallpaperLoadedFor = key
        themeWallpaperStatus = if (cached.isEmpty()) "no wallpapers listed for this theme" else "${cached.size} wallpapers"
        status.text = themeWallpaperStatus
        renderThemeWallpaperCards(cached, grid, more, source)
        return
    }
    status.text = "loading theme wallpapers..."
    val generation = themeWallpaperGeneration
    submitThemeWallpaperPreview(generation) {
        if (!isThemeWallpaperGenerationCurrent(generation)) return@submitThemeWallpaperPreview
        val result = runCatching {
            fetchThemeWallpaperFiles(source)
        }
        ui {
            if (generation == themeWallpaperGeneration &&
                wallpaperPageOpen && settings.visibility == View.VISIBLE) {
                result.onSuccess { files ->
                    themeWallpaperFiles = files
                    themeWallpaperLoadedFor = key
                    writeThemeWallpaperCache(source, files)
                    themeWallpaperStatus = if (files.isEmpty()) "no wallpapers listed for this theme" else "${files.size} wallpapers"
                    status.text = themeWallpaperStatus
                    renderThemeWallpaperCards(files, grid, more, source)
                }.onFailure { error ->
                    val message = error.message.orEmpty()
                    status.text = when {
                        message.contains("403") || message.contains("rate limit", true) -> "GitHub rate limit reached (HTTP 403). Try again later."
                        message.contains("UnknownHost") || message.contains("Unable to resolve") ||
                            message.contains("failed to connect", true) || message.contains("offline", true) ||
                            message.contains("timeout", true) || error is java.net.UnknownHostException ||
                            error is java.net.ConnectException || error is java.net.SocketTimeoutException ->
                            "offline — connect to the internet and try again"
                        else -> "couldn't load theme wallpapers: ${message}"
                    }
                }
            }
        }
    }
}

internal fun MainActivity.renderThemeWallpaperCards(
    files: List<ThemeWallpaperRemoteFile>, grid: RecyclerView, more: TextView,
    source: ThemeWallpaperSource, allowDownloads: Boolean = true
) {
    val shown = files.take(themeWallpaperShown)
    val generation = ++themeWallpaperGeneration
    cancelThemeWallpaperJobs(keepGeneration = generation)
    grid.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = shown.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(3), dp(3), dp(3), dp(3))
                layoutParams = RecyclerView.LayoutParams(-1, dp(118)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            }
            val previewFrame = FrameLayout(parent.context)
            val image = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = box(pal.cBg, pal.cDim)
            }
            val placeholder = txt(10f, pal.cDim, "").apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setPadding(dp(6), dp(4), dp(6), dp(4))
                isClickable = false
            }
            previewFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
            previewFrame.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
            card.addView(previewFrame, LinearLayout.LayoutParams(-1, dp(92)))
            card.addView(txt(9f, pal.cDim, "").apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(2), dp(2), 0)
            })
            return object : RecyclerView.ViewHolder(card) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = shown[position]
            val card = holder.itemView as LinearLayout
            val previewFrame = card.getChildAt(0) as FrameLayout
            val image = previewFrame.getChildAt(0) as ImageView
            val placeholder = previewFrame.getChildAt(1) as TextView
            val downloaded = findThemeWallpaperEntry(source, item)
            (card.getChildAt(1) as TextView).text = (if (downloaded != null) "✓ " else "") + item.name
            loadThemeWallpaperPreview(item, image, placeholder, source, generation, allowDownloads)
            val isCurrent = downloaded != null &&
                prefs.getString("wall_current_id", null) == downloaded.id
            card.background = if (isCurrent) box(pal.cBg, pal.cAccent) else null
            card.setOnClickListener { applyThemeWallpaperFromCard(source, item, card) }
        }
    }
    grid.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
    grid.post {
        val columns = (grid.width / dp(132)).coerceIn(1, 10)
        (grid.layoutManager as? GridLayoutManager)?.spanCount = columns
        val rows = (shown.size + columns - 1) / columns
        grid.layoutParams = grid.layoutParams.apply { height = rows * dp(122) }
    }
    more.visibility = if (files.size > shown.size) View.VISIBLE else View.GONE
    if (files.isEmpty()) more.visibility = View.GONE
}

internal fun MainActivity.themeWallpaperPreviewFile(source: ThemeWallpaperSource, item: ThemeWallpaperRemoteFile): File {
    val key = "${source.owner}_${source.repo}_${source.path}_${item.name}"
        .replace(Regex("[^A-Za-z0-9_.-]"), "_")
    return File(File(cacheDir, "theme-wallpaper-thumbs"), "$key.jpg")
}

internal fun MainActivity.themeWallpaperOriginalFile(source: ThemeWallpaperSource, item: ThemeWallpaperRemoteFile): File {
    val key = "${source.owner}/${source.repo}/${source.path}/${item.name}"
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return File(File(cacheDir, "theme-wallpaper-originals"), "$digest.original")
}

internal fun MainActivity.isThemeWallpaperGenerationCurrent(generation: Int): Boolean =
    generation == themeWallpaperGeneration && wallpaperPageOpen &&
        !Thread.currentThread().isInterrupted

internal fun MainActivity.cancelThemeWallpaperJobs(keepGeneration: Int? = null) {
    val stale = themeWallpaperJobs.keys.filter { keepGeneration == null || it != keepGeneration }
    stale.forEach { generation ->
        themeWallpaperJobs.remove(generation)?.forEach { it.cancel(true) }
    }
}

internal fun MainActivity.submitThemeWallpaperPreview(generation: Int, block: () -> Unit) {
    val task = java.util.concurrent.FutureTask<Unit> {
        try {
            if (isThemeWallpaperGenerationCurrent(generation)) block()
        } finally {
            themeWallpaperJobs[generation]?.removeIf { it.isDone || it.isCancelled }
        }
    }
    themeWallpaperJobs.computeIfAbsent(generation) {
        java.util.concurrent.CopyOnWriteArrayList()
    }.add(task)
    if (!isThemeWallpaperGenerationCurrent(generation)) {
        task.cancel(true)
        return
    }
    previewExecutor.execute(task)
}

internal fun MainActivity.trimThemeWallpaperOriginalCache(incoming: Long) {
    val directory = File(cacheDir, "theme-wallpaper-originals")
    directory.mkdirs()
    val cap = 150L * 1024L * 1024L
    require(incoming in 0..cap) { "wallpaper exceeds original cache limit" }
    val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
    var used = files.sumOf { it.length() }
    for (old in files.sortedBy { it.lastModified() }) {
        if (used + incoming <= cap) break
        used -= old.length()
        old.delete()
    }
    require(used + incoming <= cap) { "theme wallpaper cache is full" }
}

internal fun MainActivity.obtainThemeWallpaperOriginal(
    source: ThemeWallpaperSource, item: ThemeWallpaperRemoteFile,
    generation: Int? = null
): File {
    check(generation == null || isThemeWallpaperGenerationCurrent(generation)) { "stale wallpaper preview" }
    require(item.size in 1..(20L * 1024L * 1024L)) { "wallpaper exceeds 20 MB" }
    val original = themeWallpaperOriginalFile(source, item)
    val lock = themeWallpaperOriginalLocks.computeIfAbsent(original.absolutePath) { Any() }
    synchronized(lock) {
        check(generation == null || isThemeWallpaperGenerationCurrent(generation)) { "stale wallpaper preview" }
        if (original.isFile && original.length() == item.size) {
            original.setLastModified(System.currentTimeMillis())
            return original
        }
        original.delete()
        val temp = File(cacheDir, "theme-wallpaper-original-${UUID.randomUUID()}.tmp")
        try {
            CommunityThemes.downloadGithubBinary(item.downloadUrl, temp, 20L * 1024L * 1024L)
            check(generation == null || isThemeWallpaperGenerationCurrent(generation)) { "stale wallpaper preview" }
            require(temp.isFile && temp.length() in 1..(20L * 1024L * 1024L)) { "invalid wallpaper download" }
            require(temp.length() == item.size) { "wallpaper size mismatch" }
            trimThemeWallpaperOriginalCache(temp.length())
            original.parentFile?.mkdirs()
            check(temp.renameTo(original)) { "couldn't cache original wallpaper" }
            original.setLastModified(System.currentTimeMillis())
            return original
        } finally {
            temp.delete()
        }
    }
}

internal fun MainActivity.loadThemeWallpaperPreview(
    item: ThemeWallpaperRemoteFile, target: ImageView, placeholder: TextView,
    source: ThemeWallpaperSource, generation: Int, allowDownloads: Boolean
) {
    val thumb = themeWallpaperPreviewFile(source, item)
    target.tag = item.downloadUrl
    placeholder.text = item.name
    placeholder.visibility = View.VISIBLE
    target.setImageDrawable(null)
    if (!allowDownloads && !thumb.isFile) return
    submitThemeWallpaperPreview(generation) {
        if (!isThemeWallpaperGenerationCurrent(generation)) return@submitThemeWallpaperPreview
        val bitmap = runCatching {
            if (!thumb.isFile) {
                val original = obtainThemeWallpaperOriginal(source, item, generation)
                if (!isThemeWallpaperGenerationCurrent(generation)) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(original.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "invalid image" }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 480) sample *= 2
                if (!isThemeWallpaperGenerationCurrent(generation)) return@runCatching null
                var decoded = BitmapFactory.decodeFile(
                    original.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: error("couldn't decode preview")
                val scale = minOf(1f, 480f / maxOf(decoded.width, decoded.height))
                if (scale < 1f) {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(
                        decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1), true
                    )
                    if (scaled !== decoded) decoded.recycle()
                    decoded = scaled
                }
                if (!isThemeWallpaperGenerationCurrent(generation)) {
                    decoded.recycle()
                    return@runCatching null
                }
                thumb.parentFile?.mkdirs()
                thumb.outputStream().use { out ->
                    check(decoded.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out))
                }
                decoded
            } else {
                if (!isThemeWallpaperGenerationCurrent(generation)) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(thumb.absolutePath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 480) sample *= 2
                if (!isThemeWallpaperGenerationCurrent(generation)) return@runCatching null
                BitmapFactory.decodeFile(thumb.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
        if (!isThemeWallpaperGenerationCurrent(generation)) {
            bitmap?.recycle()
            return@submitThemeWallpaperPreview
        }
        ui {
            if (isThemeWallpaperGenerationCurrent(generation) &&
                target.tag == item.downloadUrl && bitmap != null) {
                target.setImageBitmap(bitmap)
                placeholder.visibility = View.GONE
            } else {
                bitmap?.recycle()
            }
        }
    }
}

internal fun MainActivity.findThemeWallpaperEntry(source: ThemeWallpaperSource, item: ThemeWallpaperRemoteFile): WallpaperEntry? =
    readWallpaperIndex().firstOrNull {
        it.themeId == source.id && it.remoteName == item.name && it.remoteSize == item.size
    }

internal fun MainActivity.isWallpaperDownloadAllowed(): Boolean {
    if (prefs.getBoolean("wallpaper_any_network", false)) return true
    return try {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.isActiveNetworkMetered.not()
    } catch (_: Exception) { false }
}

internal fun MainActivity.fetchThemeWallpaperFiles(source: ThemeWallpaperSource): List<ThemeWallpaperRemoteFile> {
    readThemeWallpaperCache(source)?.let { return it.sortedBy { file -> file.name.lowercase() }.take(12) }
    var files = CommunityThemes.fetchGithubDirectory(source.owner, source.repo, source.path)
    if (files.isEmpty() && source.omarchy && source.owner == "basecamp") {
        files = CommunityThemes.fetchGithubDirectory("37signals", source.repo, source.path)
    }
    val safe = files.filter { it.size in 1..(20L * 1024L * 1024L) }
        .sortedBy { it.name.lowercase() }.take(12)
        .map { ThemeWallpaperRemoteFile(it.name, it.size, it.downloadUrl, it.htmlUrl) }
    writeThemeWallpaperCache(source, safe)
    return safe
}

internal fun MainActivity.enforceAutoWallpaperCap(incoming: Long) {
    val cap = 300L * 1024L * 1024L
    synchronized(this) {
        val items = readWallpaperIndex().toMutableList()
        var used = items.filter { it.autoDownloaded }.sumOf { File(wallpaperDir(), it.file).length() }
        val protected = protectedWallpaperIds()
        for (entry in items.filter { it.autoDownloaded && it.id !in protected }.sortedBy { it.lastUsed }) {
            if (used + incoming <= cap) break
            used -= File(wallpaperDir(), entry.file).length()
            File(wallpaperDir(), entry.file).delete()
            items.removeAll { it.id == entry.id }
            wallpaperThumbCache.remove(entry.id)
            screenWallpaperCache.remove(entry.id)
        }
        writeWallpaperIndex(items)
        require(used + incoming <= cap) { "automatic wallpaper storage is full" }
    }
}

internal fun MainActivity.downloadThemeWallpaper(source: ThemeWallpaperSource, item: ThemeWallpaperRemoteFile): WallpaperEntry {
    findThemeWallpaperEntry(source, item)?.let { return it }
    require(item.size in 1..(20L * 1024L * 1024L)) { "wallpaper exceeds 20 MB" }
    val existing = readWallpaperIndex().filter { it.autoDownloaded && it.themeId == source.id }
        .sumOf { it.remoteSize }
    require(existing + item.size <= 80L * 1024L * 1024L) { "theme wallpaper limit reached" }
    require(readWallpaperIndex().count { it.autoDownloaded && it.themeId == source.id } < 12) {
        "theme wallpaper limit reached"
    }
    val original = obtainThemeWallpaperOriginal(source, item)
    val temp = File(cacheDir, "theme-wallpaper-${UUID.randomUUID()}.tmp")
    try {
        original.copyTo(temp, overwrite = true)
        enforceAutoWallpaperCap(item.size)
        val actualOwner = Uri.parse(item.downloadUrl).pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: source.owner
        val credit = "${source.theme} by ${actualOwner} · github.com/${actualOwner}/${source.repo}"
        return importWallpaperFile(temp, "theme", credit, item.htmlUrl, item.name, item.size, source.id, true)
    } finally { temp.delete() }
}

internal fun MainActivity.applyThemeWallpaperFromCard(source: ThemeWallpaperSource, item: ThemeWallpaperRemoteFile, card: LinearLayout) {
    val existing = findThemeWallpaperEntry(source, item)
    if (existing != null) {
        applyWallpaper(existing.id, applyToSystem = true)
        return
    }
    val key = "${source.id}/${item.name}"
    if (!themeWallpaperCardBusy.add(key)) return
    val progress = ProgressBar(this).apply { isIndeterminate = true }
    card.addView(progress, LinearLayout.LayoutParams(dp(20), dp(20)).apply { gravity = Gravity.CENTER })
    applyExecutor.execute {
        val result = runCatching {
            if (!isWallpaperDownloadAllowed()) throw IllegalStateException("wallpapers not downloaded (mobile data)")
            downloadThemeWallpaper(source, item)
        }
        ui {
            themeWallpaperCardBusy.remove(key)
            card.removeView(progress)
            result.onSuccess { applyWallpaper(it.id, applyToSystem = true) }
                .onFailure { Toast.makeText(this, it.message ?: "couldn't download wallpaper", Toast.LENGTH_SHORT).show() }
        }
    }
}

internal fun MainActivity.beginThemeWallpaperDownloads(
    source: ThemeWallpaperSource, firstImmediately: Boolean, allowMetered: Boolean = false
) {
    if (!themeWallpaperBusy.add(source.id)) return
    bulkExecutor.execute {
        try {
            val files = runCatching { fetchThemeWallpaperFiles(source) }.getOrElse { emptyList() }
            if (files.isEmpty()) {
                if (firstImmediately && prefs.getBoolean("wallpaper_follows_theme", true)) {
                    val remembered = prefs.getString("theme_wall_${source.id}", null)
                    val hasRememberedThemeWallpaper = remembered != null &&
                        readWallpaperIndex().any { it.id == remembered }
                    if (!hasRememberedThemeWallpaper) {
                        ui { lastPickedLocalWallpaper()?.let { applyThemeWallpaper(it) } }
                    }
                }
                return@execute
            }
            if (!isWallpaperDownloadAllowed() && !allowMetered) {
                ui {
                    themeWallpaperStatus = "wallpapers not downloaded (mobile data)"
                    if (wallpaperPageOpen && settings.visibility == View.VISIBLE) fillWallpaperSettings()
                }
                return@execute
            }
            val remembered = prefs.getString("theme_wall_${source.id}", null)
            val rememberedEntry = readWallpaperIndex().firstOrNull { it.id == remembered }
            if (firstImmediately && rememberedEntry != null && prefs.getBoolean("wallpaper_follows_theme", true)) {
                ui { applyThemeWallpaper(rememberedEntry) }
            }
            var used = readWallpaperIndex().filter { it.autoDownloaded && it.themeId == source.id }
                .sumOf { it.remoteSize }
            var appliedFirst = rememberedEntry != null
            for (item in files) {
                val existing = findThemeWallpaperEntry(source, item)
                if (existing != null) {
                    if (firstImmediately && !appliedFirst && prefs.getBoolean("wallpaper_follows_theme", true)) {
                        appliedFirst = true
                        ui { applyThemeWallpaper(existing) }
                    }
                    continue
                }
                if (used + item.size > 80L * 1024L * 1024L) continue
                val result = runCatching { downloadThemeWallpaper(source, item) }
                result.onSuccess { entry ->
                    used += item.size
                    if (firstImmediately && !appliedFirst && prefs.getBoolean("wallpaper_follows_theme", true)) {
                        appliedFirst = true
                        ui { applyThemeWallpaper(entry) }
                    }
                }
            }
        } finally {
            themeWallpaperBusy.remove(source.id)
            ui { if (wallpaperPageOpen && settings.visibility == View.VISIBLE) fillWallpaperSettings() }
        }
    }
}

/** Returns the most recently selected local wallpaper for themes without their own wallpaper. */
internal fun MainActivity.lastPickedLocalWallpaper(): WallpaperEntry? {
    val entries = readWallpaperIndex().filter { it.source == "device" }
    val rememberedId = prefs.getString("wall_last_local_id", null)
    return entries.firstOrNull { it.id == rememberedId }
        ?: entries.maxByOrNull { it.lastUsed }
}

internal fun MainActivity.themeWallpaperSourceFor(index: Int): ThemeWallpaperSource? {
    val previous = prefs.getInt("theme", 0)
    if (previous != index) prefs.edit().putInt("theme", index).apply()
    return try { currentThemeWallpaperSource() } finally {
        if (previous != index) prefs.edit().putInt("theme", previous).apply()
    }
}

/** Applies a theme-selected wallpaper to Sorairo and Android without opening a chooser. */
internal fun MainActivity.applyThemeWallpaper(entry: WallpaperEntry) {
    applyWallpaper(entry.id, rememberForTheme = false)
    val flags = if (prefs.getBoolean("wallpaper_home_lock_sync", true)) {
        android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
    } else {
        android.app.WallpaperManager.FLAG_SYSTEM
    }
    applySystemWallpaper(entry, flags, "Theme wallpaper updated")
}

internal fun MainActivity.onThemeApplied(index: Int, communityInstalled: Boolean = false) {
    if (!prefs.getBoolean("wallpaper_follows_theme", true)) return
    val source = themeWallpaperSourceFor(index)
    if (source == null) {
        // Some palettes have no wallpaper source at all. Keep the user's
        // most recently selected local wallpaper instead of returning early.
        lastPickedLocalWallpaper()?.let { applyThemeWallpaper(it) }
        return
    }
    val remembered = prefs.getString("theme_wall_${source.id}", null)
    if (remembered != null && readWallpaperIndex().any { it.id == remembered }) {
        applyThemeWallpaper(readWallpaperIndex().first { it.id == remembered })
    }
    beginThemeWallpaperDownloads(source, firstImmediately = true)
}

internal fun MainActivity.clearDownloadedThemeWallpapers() {
    synchronized(this) {
        val items = readWallpaperIndex()
        val protected = protectedWallpaperIds()
        val removed = items.filter { it.autoDownloaded && it.id !in protected }
        removed.forEach {
            File(wallpaperDir(), it.file).delete()
            wallpaperThumbCache.remove(it.id)
            screenWallpaperCache.remove(it.id)
        }
        writeWallpaperIndex(items.filterNot { it in removed })
    }
    if (wallpaperPageOpen) fillWallpaperSettings()
}
