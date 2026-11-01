package com.kabigonis.sorairo

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A theme listed on omarchy.org/themes. */
class CatalogTheme(
    val title: String,
    val owner: String,
    val repo: String,
    /** Its screenshot on omarchy.org. */
    val imageUrl: String
) {
    /** "owner/repo", lowercase, for comparing. */
    val key: String get() = "$owner/$repo".lowercase()

    /** "owner/repo", as shown to the user. */
    val label: String get() = "$owner/$repo"

    /** Folder name of the installed copy. */
    val id: String get() = CommunityThemes.idFor(owner, repo)
}

/**
 * Community Omarchy themes: the list on omarchy.org/themes and one-tap installs
 * from each theme's GitHub repo.
 *
 * Only the palette is fetched (colors.toml, or alacritty.toml for older themes),
 * a few kilobytes, never the whole repo. Nothing from a theme is ever run; the
 * text is parsed for hex colors and the rest is ignored.
 *
 * The approach (the site has no feed, so its page is read; older themes fall back
 * to alacritty.toml) follows Omalauncher, MIT, Copyright (c) 2026 Simon Schubert
 * and contributors. See THIRD_PARTY.md. Themes and screenshots belong to their
 * authors; screenshots are cached on the device for browsing, never bundled.
 */
object CommunityThemes {
    const val PAGE = "https://omarchy.org/themes/"
    private const val SITE = "https://omarchy.org"
    private const val USER_AGENT = "Sorairo (Android launcher)"
    private const val CATALOG_FRESH_MS = 6L * 60L * 60L * 1000L
    private const val MAX_PAGE_BYTES = 4 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
    private const val MAX_THEME_FILE_BYTES = 256 * 1024

    private val PART = Regex("^[A-Za-z0-9_.-]+$")
    private val SAFE_FILE = Regex("^[A-Za-z0-9_-][A-Za-z0-9._-]*$")
    private val SAFE_ID = Regex("^[a-z0-9][a-z0-9._-]*$")

    /** `<li><a href="repo"><img src="/assets/themes/x.webp" .../><span ...>Title</span></a></li>` */
    private val ENTRY = Regex(
        """<li><a href="(https://github\.com/[^"]+)"[^>]*><img src="([^"]+)"[^>]*/?><span[^>]*>([^<]+)</span></a></li>"""
    )

    // ---------------------------------------------------------------- catalog

    private fun catalogFile(context: Context) = File(context.cacheDir, "theme-catalog.html")
    private fun imageDir(context: Context) = File(context.cacheDir, "theme-catalog")

    /** The last list that parsed, or empty. Safe to call off the main thread. */
    fun cachedCatalog(context: Context): List<CatalogTheme> =
        runCatching { parseCatalog(catalogFile(context).readText()) }.getOrDefault(emptyList())

    fun catalogIsFresh(context: Context): Boolean {
        val f = catalogFile(context)
        return f.isFile && System.currentTimeMillis() - f.lastModified() < CATALOG_FRESH_MS
    }

    /** Downloads the page and parses it. Blocking: call from a background thread. */
    fun fetchCatalog(context: Context): List<CatalogTheme> {
        val bytes = httpGet(PAGE, MAX_PAGE_BYTES) ?: throw IOException("omarchy.org/themes not found")
        val html = String(bytes, Charsets.UTF_8)
        val parsed = parseCatalog(html)
        // A redesign of the page would parse to nothing: keep the old copy and say so.
        if (parsed.isEmpty()) throw IOException("omarchy.org changed its themes page")
        writeAtomically(catalogFile(context), html.toByteArray(Charsets.UTF_8))
        return parsed
    }

    internal fun parseCatalog(html: String): List<CatalogTheme> =
        ENTRY.findAll(html).mapNotNull { m ->
            val (href, src, title) = m.destructured
            val (owner, repo) = parseRepoUrl(href) ?: return@mapNotNull null
            if (!src.startsWith("/") || src.startsWith("//")) return@mapNotNull null
            CatalogTheme(unescape(title.trim()), owner, repo, SITE + src)
        }.distinctBy { it.key }.toList()

    /** "https://github.com/owner/repo[.git][/]" -> owner to repo, else null. */
    internal fun parseRepoUrl(input: String): Pair<String, String>? {
        val path = input.trim().removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").takeIf { it.startsWith("github.com/") }
            ?.removePrefix("github.com/") ?: return null
        val parts = path.trimEnd('/').removeSuffix(".git").split('/')
        if (parts.size != 2 || parts.any { !PART.matches(it) || it.startsWith('.') }) return null
        return parts[0] to parts[1]
    }

    private fun unescape(s: String) = s
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&quot;", "\"")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    /** The theme's screenshot, downloaded once. Blocking. Null when it can't be had. */
    fun imageFile(context: Context, theme: CatalogTheme): File? = runCatching {
        val name = theme.imageUrl.substringAfterLast('/')
        check(SAFE_FILE.matches(name)) { "odd image name" }
        check(theme.imageUrl.startsWith("$SITE/")) { "image not on omarchy.org" }
        val file = File(imageDir(context), name)
        if (!file.isFile) {
            val bytes = httpGet(theme.imageUrl, MAX_IMAGE_BYTES) ?: error("no image")
            writeAtomically(file, bytes)
        }
        file
    }.getOrNull()

    // ---------------------------------------------------------------- install

    internal fun idFor(owner: String, repo: String): String =
        "${owner}_$repo".lowercase().replace(Regex("[^a-z0-9._-]"), "-")

    private fun root(context: Context) = File(context.filesDir, "community-themes")

    fun installedIds(context: Context): Set<String> =
        root(context).listFiles()?.filter { File(it, "colors.toml").isFile }?.map { it.name }?.toSet()
            ?: emptySet()

    /**
     * Installed themes with original catalog metadata when available. The
     * fallback keeps offline or no-longer-listed installs visible as well.
     */
    fun installedCatalog(context: Context, catalog: List<CatalogTheme>): List<CatalogTheme> {
        val byId = catalog.associateBy { it.id }
        return (root(context).listFiles() ?: emptyArray())
            .filter { File(it, "colors.toml").isFile }
            .sortedBy { File(it, "colors.toml").lastModified() }
            .mapNotNull { dir ->
                byId[dir.name] ?: runCatching {
                    val repo = File(dir, "repo.txt").takeIf { it.isFile }?.readText()?.trim()
                        ?: return@runCatching null
                    val parts = repo.split("/")
                    if (parts.size != 2 || parts.any { !PART.matches(it) }) return@runCatching null
                    val title = File(dir, "title.txt").takeIf { it.isFile }?.readText()?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: OmarchyThemes.titleOf(dir.name)
                    CatalogTheme(title, parts[0], parts[1], "")
                }.getOrNull()
            }
    }

    /**
     * The theme's palette, downloading it first when it isn't installed yet.
     * Blocking: call from a background thread. Throws with a readable message.
     */
    fun install(context: Context, theme: CatalogTheme): OmarchyThemes.Palette {
        val dir = File(root(context), theme.id)
        val have = File(dir, "colors.toml")
        if (have.isFile) {
            readInstalled(dir)?.let { return it }
        }

        var toml: String? = null
        var palette: OmarchyThemes.Palette? = null

        rawFile(theme, "colors.toml")?.let { text ->
            OmarchyThemes.fromColorsToml(theme.id, text)?.let { toml = text; palette = it }
        }
        if (palette == null) {
            rawFile(theme, "alacritty.toml")?.let { text ->
                fromAlacritty(text)?.let { map ->
                    val converted = map.entries.joinToString("\n") { "${it.key} = \"${it.value}\"" }
                    OmarchyThemes.fromColorsToml(theme.id, converted)?.let { toml = converted; palette = it }
                }
            }
        }
        val found = palette ?: throw IOException("no usable colors in ${theme.label}")

        dir.mkdirs()
        File(dir, "title.txt").writeText(theme.title)
        File(dir, "repo.txt").writeText(theme.label)
        // colors.toml last: its presence marks a complete install.
        writeAtomically(have, toml!!.toByteArray(Charsets.UTF_8))
        return named(found, theme.title)
    }

    /** Installed themes, oldest first. Quick: reads a few small files. */
    fun loadInstalled(context: Context): List<OmarchyThemes.Palette> =
        (root(context).listFiles() ?: emptyArray())
            .filter { File(it, "colors.toml").isFile }
            .sortedBy { File(it, "colors.toml").lastModified() }
            .mapNotNull { readInstalled(it) }

    fun remove(context: Context, id: String) {
        if (!SAFE_ID.matches(id)) return
        File(root(context), id).deleteRecursively()
    }

    private fun readInstalled(dir: File): OmarchyThemes.Palette? = runCatching {
        val toml = File(dir, "colors.toml").readText()
        val title = File(dir, "title.txt").takeIf { it.isFile }?.readText()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: OmarchyThemes.titleOf(dir.name)
        OmarchyThemes.fromColorsToml(dir.name, toml)?.let { named(it, title) }
    }.getOrNull()

    private fun named(p: OmarchyThemes.Palette, name: String) = OmarchyThemes.Palette(
        p.id, name, p.bg, p.fg, p.dim, p.blue, p.green, p.accent, p.sel, p.isLight, p.ansiColors
    )

    // ------------------------------------------------- older themes: alacritty

    private val SECTION = Regex("""^\s*\[([^\]]*)]\s*$""")
    private val HEX_VALUE = Regex(
        """^(?:["'](?:0[xX]|#)?([0-9a-fA-F]{6})["'](?:\s*#.*)?|(?:0[xX])?([0-9a-fA-F]{6})(?:\s*#.*)?)$"""
    )

    /**
     * The colors of an alacritty.toml as colors.toml-style keys (accent, selection,
     * background, foreground, color0..color15), or null when it lacks the eight
     * normal colors. Mirrors how Omarchy reads such themes.
     */
    internal fun fromAlacritty(text: String): Map<String, String>? {
        val direct = HashMap<String, String>()
        val dotted = HashMap<String, String>()
        var section = ""
        for (line in text.lineSequence()) {
            if (line.trimStart().startsWith("[")) {
                section = SECTION.find(line)?.groupValues?.get(1)?.trim() ?: ""
                continue
            }
            if (section.isEmpty()) continue
            val eq = line.indexOf('=').takeIf { it > 0 } ?: continue
            val key = line.substring(0, eq).trim()
            if (key.isEmpty() || key.startsWith("#")) continue
            val m = HEX_VALUE.find(line.substring(eq + 1).trim()) ?: continue
            val hex = "#" + m.groupValues[1].ifEmpty { m.groupValues[2] }.lowercase()
            val target = if (section == "colors" && '.' in key) dotted else direct
            val full = "$section.$key"
            if (!target.containsKey(full)) target[full] = hex
        }
        val all = dotted + direct
        val names = listOf("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white")
        val normal = names.map { all["colors.normal.$it"] ?: return null }
        val bright = names.mapIndexed { i, n -> all["colors.bright.$n"] ?: normal[i] }
        val background = all["colors.primary.background"] ?: normal[0]
        val foreground = all["colors.primary.foreground"] ?: normal[7]
        val out = linkedMapOf(
            "accent" to normal[4],
            "selection" to (all["colors.selection.background"] ?: foreground),
            "background" to background,
            "foreground" to foreground
        )
        val colors = listOf(background) + normal.subList(1, 7) + foreground + bright
        colors.forEachIndexed { i, c -> out["color$i"] = c }
        return out
    }

    // ----------------------------------------------------------------- network

    /** A file from the theme's repo, trying its default branch first. Null when it isn't there. */
    private fun rawFile(theme: CatalogTheme, path: String): String? {
        for (ref in listOf("HEAD", "main", "master")) {
            val url = "https://raw.githubusercontent.com/${theme.owner}/${theme.repo}/$ref/$path"
            val bytes = httpGet(url, MAX_THEME_FILE_BYTES)
            if (bytes != null) return String(bytes, Charsets.UTF_8)
        }
        return null
    }

    /** Null on 404; throws on other failures. HTTPS only, size-capped. */
    private fun httpGet(url: String, maxBytes: Int): ByteArray? {
        require(url.startsWith("https://")) { "https only" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = c.responseCode
            if (code == 404) return null
            if (code != 200) throw IOException("HTTP $code")
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("file too large")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("couldn't save ${file.name}")
        }
    }
}
