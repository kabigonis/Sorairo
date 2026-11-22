package com.kabigonis.sorairo

import android.app.Dialog
import android.content.ComponentName
import android.graphics.Matrix
import android.media.ExifInterface
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import com.kabigonis.sorairo.MainActivity.WallpaperEntry
import com.kabigonis.sorairo.MainActivity.WallpaperMergePlan

// Wallpaper library: parallax, index, import, apply, system wallpaper, display and settings page.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

// ================= wallpaper / status =================
/**
 * Moves the wallpaper slightly behind workspace swipes instead of making it feel
 * completely fixed. The 8% horizontal overscan keeps edges covered while shifting.
 */
internal fun MainActivity.updateWallpaperParallaxScale() {
    if (!wallInitialized) return
    if (!prefs.getBoolean("wallpaper_parallax", true)) {
        wall.scaleX = 1f
        wall.scaleY = 1f
        wall.translationX = 0f
        wall.scaleType = ImageView.ScaleType.FIT_CENTER
        return
    }
    wall.scaleType = ImageView.ScaleType.CENTER_CROP
    val width = wall.width
    if (width > 0) {
        // 36dp total horizontal overscan covers the full +/-18dp parallax travel.
        // Uniform X/Y scaling preserves the artwork's original proportions.
        val scale = (width + dp(36)).toFloat() / width.toFloat()
        wall.scaleX = scale
        wall.scaleY = scale
    }
}

internal fun MainActivity.updateWallpaperParallax(progress: Float) {
    if (!wallInitialized) return
    if (!prefs.getBoolean("wallpaper_parallax", true)) {
        wall.translationX = 0f
        return
    }
    val fraction = (progress / 4f).coerceIn(0f, 1f)
    wall.translationX = ((0.5f - fraction) * dp(36)).coerceIn(-dp(18).toFloat(), dp(18).toFloat())
}

internal fun MainActivity.wallpaperDir(): File = File(filesDir, "wallpapers").apply { mkdirs() }

internal fun MainActivity.wallpaperIndexFile(): File = File(wallpaperDir(), "index.json")

internal fun MainActivity.readWallpaperIndex(): MutableList<WallpaperEntry> {
    return try {
        val root = JSONObject(wallpaperIndexFile().takeIf { it.exists() }?.readText() ?: """{"wallpapers":[]}""")
        val array = root.optJSONArray("wallpapers") ?: JSONArray()
        MutableList(array.length()) { i ->
            val item = array.getJSONObject(i)
            WallpaperEntry(
                item.getString("id"), item.getString("file"),
                item.optString("source", "unknown"),
                item.optString("credit").takeIf { it.isNotBlank() },
                item.optString("link").takeIf { it.isNotBlank() },
                item.optString("remoteName").takeIf { it.isNotBlank() },
                item.optLong("remoteSize", 0L),
                item.optString("themeId").takeIf { it.isNotBlank() },
                item.optBoolean("autoDownloaded", false),
                item.optLong("lastUsed", 0L),
                item.optString("sha256").takeIf { it.isNotBlank() },
                item.optString("mediaType", "image")
            )
        }.filter { File(wallpaperDir(), it.file).isFile }.toMutableList()
    } catch (_: Exception) { mutableListOf() }
}

internal fun MainActivity.writeWallpaperIndex(items: List<WallpaperEntry>) {
    val array = JSONArray()
    items.forEach { entry ->
        val item = JSONObject()
            .put("id", entry.id)
            .put("file", entry.file)
            .put("source", entry.source)
        entry.credit?.let { item.put("credit", it) }
        entry.link?.let { item.put("link", it) }
        entry.remoteName?.let { item.put("remoteName", it) }
        item.put("remoteSize", entry.remoteSize)
        entry.themeId?.let { item.put("themeId", it) }
        item.put("autoDownloaded", entry.autoDownloaded)
        item.put("lastUsed", entry.lastUsed)
        entry.sha256?.let { item.put("sha256", it) }
        item.put("mediaType", entry.mediaType)
        array.put(item)
    }
    val temp = File(wallpaperDir(), "index.json.tmp")
    temp.writeText(JSONObject().put("wallpapers", array).toString(2))
    val target = wallpaperIndexFile()
    if (target.exists()) target.delete()
    check(temp.renameTo(target)) { "couldn't save wallpaper index" }
}

internal fun MainActivity.sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

// Pure planner. The index is newest-first, so the last duplicate is oldest.
internal fun MainActivity.buildWallpaperMergePlan(
    index: List<WallpaperEntry>, hashes: Map<String, String>
): WallpaperMergePlan {
    val replacements = linkedMapOf<String, String>()
    val removedIds = linkedSetOf<String>()
    index.indices.groupBy { hashes[index[it].id] ?: index[it].sha256.orEmpty() }
        .filterKeys { it.isNotBlank() }.values.filter { it.size > 1 }.forEach { positions ->
            val kept = index[positions.maxOrNull()!!]
            positions.filter { index[it].id != kept.id }.forEach { position ->
                val duplicate = index[position]
                replacements[duplicate.id] = kept.id
                removedIds.add(duplicate.id)
            }
        }
    val kept = index.filterNot { it.id in removedIds }.map { entry ->
        val hash = hashes[entry.id] ?: entry.sha256
        if (entry.sha256 == hash) entry else entry.copy(sha256 = hash)
    }
    return WallpaperMergePlan(kept, index.filter { it.id in removedIds }, replacements)
}

internal fun MainActivity.migrateWallpaperLibraryDuplicates() {
    if (prefs.getBoolean("wallpaper_sha256_migrated_v1", false)) return
    synchronized(this) {
        if (prefs.getBoolean("wallpaper_sha256_migrated_v1", false)) return
        val index = readWallpaperIndex()
        val hashes = index.associate { entry ->
            entry.id to sha256File(File(wallpaperDir(), entry.file))
        }
        val plan = buildWallpaperMergePlan(index, hashes)
        val editor = prefs.edit()
        prefs.getString("wall_current_id", null)?.let { id ->
            editor.putString("wall_current_id", plan.replacements[id] ?: id)
        }
        prefs.all.keys.filter { it.startsWith("theme_wall_") }.forEach { key ->
            val id = prefs.getString(key, null) ?: return@forEach
            plan.replacements[id]?.let { editor.putString(key, it) }
        }
        plan.removed.forEach { entry ->
            File(wallpaperDir(), entry.file).delete()
            wallpaperThumbCache.remove(entry.id)
            screenWallpaperCache.remove(entry.id)
        }
        writeWallpaperIndex(plan.kept)
        editor.putBoolean("wallpaper_sha256_migrated_v1", true).apply()
    }
}

internal fun MainActivity.findWallpaperByHash(hash: String): WallpaperEntry? =
    readWallpaperIndex().firstOrNull { it.sha256 == hash }

internal fun MainActivity.protectedWallpaperIds(): Set<String> = buildSet {
    prefs.getString("wall_current_id", null)?.let(::add)
    prefs.all.keys.filter { it.startsWith("theme_wall_") }.forEach { key ->
        prefs.getString(key, null)?.let(::add)
    }
}

internal fun MainActivity.importWallpaper(uri: Uri, source: String): WallpaperEntry {
    val sourceFile = File(cacheDir, "wallpaper-import-${UUID.randomUUID()}.tmp")
    try {
        contentResolver.openInputStream(uri)?.use { input ->
            sourceFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Unable to read image")
        migrateWallpaperLibraryDuplicates()
        val contentHash = sha256File(sourceFile)
        findWallpaperByHash(contentHash)?.let { return it }
        val mime = contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        val hintedExt = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        val isVideo = mime.startsWith("video/") || hintedExt in setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
        val isGif = mime == "image/gif" || hintedExt == "gif"
        if (isVideo || isGif) {
            require(sourceFile.length() in 1..(100L * 1024L * 1024L)) { "animated wallpaper must be under 100 MB" }
            val extension = if (isGif) "gif" else when (mime.substringAfter('/', "")) {
                "webm" -> "webm"; "quicktime" -> "mov"; "3gpp" -> "3gp"; "x-matroska" -> "mkv"; else -> "mp4"
            }
            if (isGif) {
                require(sourceFile.inputStream().use { input -> input.read() == 'G'.code && input.read() == 'I'.code && input.read() == 'F'.code }) { "invalid GIF file" }
            } else {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(sourceFile.absolutePath)
                    require(retriever.getFrameAtTime() != null) { "unsupported or invalid video" }
                } finally { retriever.release() }
            }
            val id = UUID.randomUUID().toString()
            val filename = "$id.$extension"
            val destination = File(wallpaperDir(), filename)
            sourceFile.copyTo(destination)
            val entry = WallpaperEntry(id, filename, source, sha256 = contentHash,
                mediaType = if (isGif) "gif" else "video")
            synchronized(this) {
                readWallpaperIndex().firstOrNull { it.sha256 == contentHash }?.let { destination.delete(); return it }
                val items = readWallpaperIndex()
                items.add(0, entry)
                writeWallpaperIndex(items)
            }
            return entry
        }
        require(mime.isEmpty() || mime.startsWith("image/")) { "choose an image, GIF, or video" }
        val orientation = ExifInterface(sourceFile.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalArgumentException("Unable to decode image")

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
    }
    val oriented = if (!matrix.isIdentity) {
        android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    } else decoded
    val scale = minOf(1f, 2560f / maxOf(oriented.width, oriented.height))
    val finalBitmap = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            oriented, (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1), true
        ).also { if (it !== oriented) oriented.recycle() }
    } else oriented

    val id = UUID.randomUUID().toString()
    val filename = "$id.jpg"
    val destination = File(wallpaperDir(), filename)
    destination.outputStream().use { out ->
        check(finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out))
    }
        finalBitmap.recycle()
        val entry = WallpaperEntry(id, filename, source, sha256 = contentHash)
        synchronized(this) {
            readWallpaperIndex().firstOrNull { it.sha256 == contentHash }?.let { destination.delete(); return it }
            val items = readWallpaperIndex()
            items.add(0, entry)
            writeWallpaperIndex(items)
        }
        return entry
    } finally {
        sourceFile.delete()
    }
}

internal fun MainActivity.importWallpaperFile(
    sourceFile: File, source: String, credit: String, link: String,
    remoteName: String? = null, remoteSize: Long = 0L, themeId: String? = null,
    autoDownloaded: Boolean = false
): WallpaperEntry {
    migrateWallpaperLibraryDuplicates()
    val contentHash = sha256File(sourceFile)
    findWallpaperByHash(contentHash)?.let { return it }
    val orientation = ExifInterface(sourceFile.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
    val decoded = BitmapFactory.decodeFile(
        sourceFile.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: throw IllegalArgumentException("Unable to decode image")
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
    }
    val oriented = if (!matrix.isIdentity) {
        android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    } else decoded
    val scale = minOf(1f, 2560f / maxOf(oriented.width, oriented.height))
    val finalBitmap = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            oriented, (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1), true
        ).also { if (it !== oriented) oriented.recycle() }
    } else oriented
    val id = UUID.randomUUID().toString()
    val filename = "$id.jpg"
    try {
        File(wallpaperDir(), filename).outputStream().use { out ->
            check(finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out))
        }
    } finally { finalBitmap.recycle() }
    val entry = WallpaperEntry(id, filename, source, credit, link, remoteName, remoteSize,
        themeId, autoDownloaded, System.currentTimeMillis(), contentHash)
    synchronized(this) {
        readWallpaperIndex().firstOrNull { it.sha256 == contentHash }?.let { File(wallpaperDir(), filename).delete(); return it }
        val items = readWallpaperIndex()
        items.add(0, entry)
        writeWallpaperIndex(items)
    }
    return entry
}

internal fun MainActivity.clearCurrentWallpaper() {
    prefs.edit().remove("wall_current_id").remove("wall").remove("live_wallpaper_file").apply()
    clearWallpaperVideoPreview()
    if (wallInitialized) wall.setImageDrawable(null)
    wallpaperThumbCache.remove("current")
    if (wallpaperPageOpen && settings.visibility == View.VISIBLE) fillWallpaperSettings()
}

internal fun MainActivity.applyWallpaper(
    id: String,
    rememberForTheme: Boolean = true,
    applyToSystem: Boolean = false
) {
    val entry = readWallpaperIndex().firstOrNull { it.id == id } ?: return
    val now = System.currentTimeMillis()
    synchronized(this) {
        writeWallpaperIndex(readWallpaperIndex().map { if (it.id == id) it.copy(lastUsed = now) else it })
    }
    val edit = prefs.edit().putString("wall_current_id", id).remove("wall")
    if (entry.source == "device") {
        edit.putString("wall_last_local_id", id)
    }
    if (rememberForTheme) {
        currentThemeWallpaperSource()?.let { edit.putString("theme_wall_${it.id}", id) }
    }
    edit.apply()
    showWallpaperBitmap(entry, true)
    if (applyToSystem) {
        if (prefs.getBoolean("wallpaper_home_lock_sync", true)) {
            applySystemWallpaper(
                entry,
                android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK,
                "Home and lock screen wallpapers updated"
            )
        } else {
            showWallpaperApplyDialog(entry)
        }
    }
    if (wallpaperPageOpen && settings.visibility == View.VISIBLE) fillWallpaperSettings()
}

internal fun MainActivity.showWallpaperApplyDialog(entry: WallpaperEntry) {
    val p = pal
    val dialog = Dialog(this)
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(solidColor(), p.cAccent)
        setPadding(dp(18), dp(16), dp(18), dp(12))
    }
    shell.addView(txt(16f, p.cBlue, "Apply wallpaper"))
    shell.addView(txt(13f, p.cFg, "Where would you like to apply this wallpaper?").apply {
        setPadding(0, dp(8), 0, dp(12))
    })
    fun choice(label: String, flags: Int, confirmation: String) {
        shell.addView(txt(13f, p.cFg, label).apply {
            setPadding(dp(4), dp(13), dp(4), dp(13))
            setOnClickListener {
                dialog.dismiss()
                applySystemWallpaper(entry, flags, confirmation)
            }
        })
        shell.addView(View(this).apply { setBackgroundColor(p.cDim) },
            LinearLayout.LayoutParams(-1, dp(1)))
    }
    choice("Home screen", android.app.WallpaperManager.FLAG_SYSTEM, "Home screen wallpaper updated")
    choice("Lock screen", android.app.WallpaperManager.FLAG_LOCK, "Lock screen wallpaper updated")
    choice(
        "Both screens",
        android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK,
        "Home and lock screen wallpapers updated"
    )
    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
    }
    actions.addView(txt(12f, p.cDim, "CANCEL").apply {
        setPadding(dp(12), dp(12), dp(4), dp(6))
        setOnClickListener { dialog.dismiss() }
    })
    shell.addView(actions)
    dialog.setContentView(shell)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.show()
    dialog.window?.setLayout(
        min(dp(520), resources.displayMetrics.widthPixels - dp(40)),
        WindowManager.LayoutParams.WRAP_CONTENT
    )
}

internal fun MainActivity.buildWallpaperOptionsSection(target: LinearLayout) {
    val p = pal
    fun option(label: String, value: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(13), dp(2), dp(13))
            isClickable = true
            setOnClickListener { action() }
        }
        row.addView(txt(13f, p.cFg, label), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(txt(12f, p.cAccent, value))
        target.addView(row, LinearLayout.LayoutParams(-1, -2))
        target.addView(View(this).apply { setBackgroundColor(p.cDim) },
            LinearLayout.LayoutParams(-1, dp(1)))
    }
    option("wallpaper downloads", if (prefs.getBoolean("wallpaper_any_network", false)) "any network" else "wifi only") {
        prefs.edit().putBoolean("wallpaper_any_network", !prefs.getBoolean("wallpaper_any_network", false)).apply()
        fillWallpaperSettings()
    }
    option("wallpaper follows theme", if (prefs.getBoolean("wallpaper_follows_theme", true)) "on" else "off") {
        prefs.edit().putBoolean("wallpaper_follows_theme", !prefs.getBoolean("wallpaper_follows_theme", true)).apply()
        fillWallpaperSettings()
    }
    option("wallpaper parallax", if (prefs.getBoolean("wallpaper_parallax", true)) "on" else "off") {
        prefs.edit().putBoolean("wallpaper_parallax", !prefs.getBoolean("wallpaper_parallax", true)).apply()
        updateWallpaperParallaxScale()
        updateWallpaperParallax(curWs.toFloat())
        fillWallpaperSettings()
    }
    option("home & lock sync", if (prefs.getBoolean("wallpaper_home_lock_sync", true)) "on" else "off") {
        prefs.edit().putBoolean("wallpaper_home_lock_sync", !prefs.getBoolean("wallpaper_home_lock_sync", true)).apply()
        fillWallpaperSettings()
    }
    target.addView(txt(10f, p.cDim,
        "Parallax preserves image proportions and adds only the space needed for workspace movement. When sync is off, selecting a wallpaper asks which screen to update. Theme auto-follow updates Sorairo and the Android wallpaper; home & lock sync controls whether both screens are updated."),
        LinearLayout.LayoutParams(-1, -2))
}

internal fun MainActivity.applySystemWallpaper(entry: WallpaperEntry, which: Int, successMessage: String) {
    if (entry.mediaType == "gif" || entry.mediaType == "video") {
        prefs.edit().putString("live_wallpaper_file", entry.file).apply()
        try {
            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@applySystemWallpaper, SorairoLiveWallpaperService::class.java)
                )
            }
            startActivity(intent)
            Toast.makeText(this, "Choose Sorairo to apply this live wallpaper", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "couldn't open live wallpaper picker", Toast.LENGTH_LONG).show()
        }
        return
    }
    wallpaperExecutor.execute {
        val result = runCatching {
            val file = File(wallpaperDir(), entry.file)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("couldn't decode wallpaper image")
            try {
                android.app.WallpaperManager.getInstance(applicationContext)
                    .setBitmap(bitmap, null, true, which)
            } finally {
                bitmap.recycle()
            }
        }
        handler.post {
            result.onFailure {
                Toast.makeText(this, it.message ?: "couldn't apply system wallpaper", Toast.LENGTH_LONG).show()
            }
        }
    }
}

internal fun MainActivity.removeWallpaper(entry: WallpaperEntry) {
    if (prefs.getString("live_wallpaper_file", null) == entry.file) {
        prefs.edit().remove("live_wallpaper_file").apply()
    }
    synchronized(this) {
        File(wallpaperDir(), entry.file).delete()
        writeWallpaperIndex(readWallpaperIndex().filterNot { it.id == entry.id })
    }
    wallpaperThumbCache.remove(entry.id)
    screenWallpaperCache.remove(entry.id)
    if (prefs.getString("wall_current_id", null) == entry.id) clearCurrentWallpaper()
    else if (wallpaperPageOpen && settings.visibility == View.VISIBLE) fillWallpaperSettings()
}

internal fun MainActivity.loadWallpaper() {
    if (!wallInitialized) return
    if (!prefs.getBoolean("wall_library_migrated_v1", false) && !wallpaperMigrationStarted) {
        wallpaperMigrationStarted = true
        wallpaperExecutor.execute {
            val legacy = prefs.getString("wall", null)
            try {
                if (!legacy.isNullOrBlank()) {
                    val imported = importWallpaper(Uri.parse(legacy), "legacy")
                    if (prefs.getString("wall_current_id", null) == null) {
                        prefs.edit().putString("wall_current_id", imported.id).apply()
                    }
                }
            } catch (_: Exception) {
            } finally {
                prefs.edit().remove("wall").putBoolean("wall_library_migrated_v1", true).apply()
                migrateWallpaperLibraryDuplicates()
                handler.post { loadWallpaperFromLibrary() }
            }
        }
    } else if (prefs.getBoolean("wall_library_migrated_v1", false)) {
        wallpaperExecutor.execute {
            migrateWallpaperLibraryDuplicates()
            handler.post { loadWallpaperFromLibrary() }
        }
    }
}

internal fun MainActivity.loadWallpaperFromLibrary() {
    val id = prefs.getString("wall_current_id", null)
    val entry = readWallpaperIndex().firstOrNull { it.id == id }
    if (entry == null) {
        wall.setImageDrawable(null)
        return
    }
    showWallpaperBitmap(entry, false)
}

internal fun MainActivity.clearWallpaperVideoPreview() {
    wallpaperVideoView?.let { view ->
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.stopPlayback() }
    }
    wallpaperVideoView = null
    if (wallInitialized) wall.visibility = View.VISIBLE
}

internal fun MainActivity.showWallpaperBitmap(entry: WallpaperEntry, crossfade: Boolean) {
    if (!wallInitialized) return
    if (entry.mediaType == "video") {
        screenWallpaperCache.remove(entry.id)
        val parent = wall.parent as? ViewGroup ?: return
        clearWallpaperVideoPreview()
        val index = parent.indexOfChild(wall).coerceAtLeast(0)
        val video = android.widget.VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnPreparedListener { mp -> mp.isLooping = true; start() }
            setVideoURI(Uri.fromFile(File(wallpaperDir(), entry.file)))
        }
        parent.addView(video, index, wall.layoutParams)
        wall.visibility = View.GONE
        wallpaperVideoView = video
        return
    }
    clearWallpaperVideoPreview()
    if (entry.mediaType == "gif" && Build.VERSION.SDK_INT >= 28) {
        wallpaperExecutor.execute {
            val drawable = runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(wallpaperDir(), entry.file)))
            }.getOrNull()
            handler.post {
                if (prefs.getString("wall_current_id", null) == entry.id && drawable != null) {
                    wall.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            }
        }
        return
    }
    fun display(bitmap: android.graphics.Bitmap) {
        val next = BitmapDrawable(resources, bitmap)
        if (crossfade && wall.drawable != null) {
            wall.setImageDrawable(android.graphics.drawable.TransitionDrawable(arrayOf(wall.drawable, next)).apply {
                isCrossFadeEnabled = true
                startTransition(250)
            })
        } else wall.setImageDrawable(next)
    }
    screenWallpaperCache.get(entry.id)?.let { if (prefs.getString("wall_current_id", null) == entry.id) display(it); return }
    wallpaperExecutor.execute {
        val bitmap = runCatching {
            val file = File(wallpaperDir(), entry.file)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            val maxDimension = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels).coerceAtLeast(1)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 1.15f) sample *= 2
            if (entry.mediaType == "video") null else BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
        handler.post {
            if (bitmap != null) {
                screenWallpaperCache.put(entry.id, bitmap)
                if (prefs.getString("wall_current_id", null) == entry.id) display(bitmap)
            }
        }
    }
}

internal fun MainActivity.loadWallpaperThumbnail(entry: WallpaperEntry, target: ImageView, sizePx: Int) {
    target.tag = entry.id
    wallpaperThumbCache.get(entry.id)?.let {
        target.setImageBitmap(it)
        return
    }
    target.setImageDrawable(null)
    wallpaperExecutor.execute {
        val bitmap = try {
            val file = File(wallpaperDir(), entry.file)
            if (entry.mediaType == "video") {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { retriever.release() }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > sizePx * 2) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) { null }
        if (bitmap != null) wallpaperThumbCache.put(entry.id, bitmap)
        handler.post {
            if (target.tag == entry.id && bitmap != null) target.setImageBitmap(bitmap)
        }
    }
}

internal fun MainActivity.openWallpaperPage() {
    showSettings()
    wallpaperPageOpen = true
    wallpaperTab = "theme"
    fillWallpaperSettings()
}

internal fun MainActivity.fillWallpaperSettings() {
    wallpaperPageOpen = true
    cancelThemeWallpaperJobs()
    themeWallpaperGeneration++
    val p = pal
    setScroll.visibility = View.GONE
    wallpaperPageShell?.let { oldShell ->
        oldShell.animate().cancel()
        settings.removeView(oldShell)
    }

    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        alpha = 0f
        background = box(solidColor(), p.cAccent)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
    }
    wallpaperPageShell = shell

    val current = readWallpaperIndex().firstOrNull {
        it.id == prefs.getString("wall_current_id", null)
    }
    val currentRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(2), dp(2), dp(10))
    }
    val preview = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = box(p.cBg, p.cDim)
    }
    if (current != null) loadWallpaperThumbnail(current, preview, dp(180))
    currentRow.addView(preview, LinearLayout.LayoutParams(dp(76), dp(48)))
    val currentInfo = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), 0, dp(8), 0)
        addView(txt(12f, p.cFg, if (current == null) "no wallpaper selected" else "current"))
        if (current != null && !current.credit.isNullOrBlank()) {
            addView(txt(10f, p.cDim, current.credit.orEmpty()).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }
    currentRow.addView(currentInfo, LinearLayout.LayoutParams(0, -2, 1f))
    currentRow.addView(txt(11f, p.cAccent, "[ clear ]").apply {
        setPadding(dp(8), dp(10), dp(2), dp(10))
        setOnClickListener {
            clearCurrentWallpaper()
            fillWallpaperSettings()
        }
    })
    shell.addView(currentRow, LinearLayout.LayoutParams(-1, -2))

    val tabRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(8))
    }
    fun addTab(label: String, key: String) {
        tabRow.addView(txt(12f, if (wallpaperTab == key) p.cAccent else p.cDim,
            if (wallpaperTab == key) "[ " + label + " ]" else label).apply {
            setPadding(dp(8), dp(8), dp(12), dp(8))
            setOnClickListener {
                wallpaperTab = key
                fillWallpaperSettings()
            }
        })
    }
    addTab("theme", "theme")
    addTab("library", "library")
    addTab("online", "online")
    addTab("settings", "settings")
    tabRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    tabRow.addView(txt(11f, p.cAccent, "+ add from device").apply {
        setPadding(dp(8), dp(8), dp(2), dp(8))
        setOnClickListener { pickWall.launch(arrayOf("image/*", "video/*")) }
    })
    shell.addView(tabRow, LinearLayout.LayoutParams(-1, -2))

    val innerScroll = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = true
    }
    val innerContent = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(2), 0, dp(4))
    }
    innerScroll.addView(innerContent, android.widget.FrameLayout.LayoutParams(-1, -2))
    shell.addView(innerScroll, LinearLayout.LayoutParams(-1, 0, 1f))

    when (wallpaperTab) {
        "theme" -> innerContent.addView(buildThemeWallpaperSection())
        "online" -> innerContent.addView(buildWallhavenSection())
        "settings" -> buildWallpaperOptionsSection(innerContent)
        else -> buildWallpaperLibrary(innerContent)
    }

    val dm = resources.displayMetrics
    val insets = ViewCompat.getRootWindowInsets(settings)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
    val insetHeight = (insets?.top ?: 0) + (insets?.bottom ?: 0)
    val maxHeight = (dm.heightPixels - insetHeight - dp(48)).coerceAtLeast(dp(240))
    val width = if (dm.widthPixels >= dp(700)) {
        (dm.widthPixels * 0.94f).toInt().coerceAtMost(dp(1500))
    } else {
        min(dp(520), dm.widthPixels - dp(32))
    }
    settings.addView(shell, FrameLayout.LayoutParams(width, maxHeight, Gravity.CENTER))
    shell.animate().alpha(1f).setDuration(150).start()
}

internal fun MainActivity.buildWallpaperLibrary(target: LinearLayout) {
    val p = pal
    val entries = readWallpaperIndex()
    val groups = linkedMapOf<String, MutableList<WallpaperEntry>>()
    entries.forEach { entry ->
        val label = when {
            entry.source.equals("device", true) -> "device"
            entry.themeId != null -> {
                val installed = CommunityThemes.installedCatalog(this, communityCatalog)
                val title = installed.firstOrNull { it.id == entry.themeId }?.title
                "theme · " + (title ?: entry.themeId)
            }
            entry.source.equals("wallhaven", true) -> "wallhaven"
            else -> entry.source.ifBlank { "other" }
        }
        groups.getOrPut(label) { mutableListOf() }.add(entry)
    }

    if (entries.isEmpty()) {
        target.addView(txt(12f, p.cDim, "no wallpapers yet").apply {
            setPadding(dp(2), dp(10), 0, dp(14))
        })
    }
    groups.forEach { (sourceLabel, group) ->
        target.addView(txt(11f, p.cBlue, sourceLabel.lowercase()).apply {
            setPadding(dp(2), dp(10), 0, dp(5))
        })
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@buildWallpaperLibrary, 3)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = wallpaperLibraryAdapter(group)
        }
        target.addView(grid, LinearLayout.LayoutParams(-1, -2))
        grid.post {
            val columns = (grid.width / dp(132)).coerceIn(1, 10)
            (grid.layoutManager as? GridLayoutManager)?.spanCount = columns
            val rows = (group.size + columns - 1) / columns
            grid.layoutParams = grid.layoutParams.apply { height = rows * dp(140) }
        }
    }

    val bytes = wallpaperDir().listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "mp4", "webm", "mkv", "mov", "3gp", "m4v") }
        ?.sumOf { it.length() } ?: 0L
    target.addView(txt(11f, p.cDim,
        "wallpapers use %.1f MB".format(bytes / 1048576.0)).apply {
        setPadding(0, dp(12), 0, dp(5))
    })
    target.addView(txt(11f, p.cAccent, "[ clear downloaded theme wallpapers ]").apply {
        setPadding(0, dp(4), 0, dp(12))
        setOnClickListener {
            showSorairoConfirmDialog(
                "Clear downloaded theme wallpapers?",
                "Only automatically downloaded theme wallpapers will be removed. Wallpapers added from your device stay untouched.",
                "Clear"
            ) {
                clearDownloadedThemeWallpapers()
                fillWallpaperSettings()
            }
        }
    })
}

internal fun MainActivity.wallpaperLibraryAdapter(entries: List<WallpaperEntry>): RecyclerView.Adapter<RecyclerView.ViewHolder> =
    object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(3), dp(3), dp(3), dp(3))
                layoutParams = RecyclerView.LayoutParams(-1, dp(140)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            }
            val image = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = box(pal.cBg, pal.cDim)
            }
            card.addView(image, LinearLayout.LayoutParams(-1, dp(92)))
            card.addView(txt(10f, pal.cFg, "").apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setPadding(dp(2), dp(3), dp(2), 0)
            }, LinearLayout.LayoutParams(-1, dp(17)))
            card.addView(txt(9f, pal.cDim, "").apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(1), dp(2), 0)
            }, LinearLayout.LayoutParams(-1, dp(15)))
            return object : RecyclerView.ViewHolder(card) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val entry = entries[position]
            val card = holder.itemView as LinearLayout
            loadWallpaperThumbnail(entry, card.getChildAt(0) as ImageView, dp(180))
            (card.getChildAt(1) as TextView).text = (if (entry.mediaType == "video") "VIDEO · " else if (entry.mediaType == "gif") "GIF · " else "") + entry.file
            (card.getChildAt(2) as TextView).text = entry.credit.orEmpty()
            val isCurrent = prefs.getString("wall_current_id", null) == entry.id
            card.background = if (isCurrent) box(pal.cBg, pal.cAccent) else null
            card.setOnClickListener { applyWallpaper(entry.id, applyToSystem = true) }
            card.setOnLongClickListener {
                showSorairoConfirmDialog(
                    "Remove this wallpaper?",
                    "This removes the saved copy from your wallpaper library.",
                    "Remove"
                ) {
                    removeWallpaper(entry)
                    fillWallpaperSettings()
                }
                true
            }
        }
    }
