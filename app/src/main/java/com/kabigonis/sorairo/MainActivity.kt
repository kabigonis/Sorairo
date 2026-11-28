package com.kabigonis.sorairo

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.Executors
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min
import com.kabigonis.sorairo.MainActivity.App
import com.kabigonis.sorairo.MainActivity.Pal
import com.kabigonis.sorairo.MainActivity.WallpaperEntry
import com.kabigonis.sorairo.MainActivity.ThemeWallpaperSource
import com.kabigonis.sorairo.MainActivity.ThemeWallpaperRemoteFile
import com.kabigonis.sorairo.MainActivity.WallhavenWallpaper
import com.kabigonis.sorairo.MainActivity.WallhavenPage
import com.kabigonis.sorairo.MainActivity.WallpaperMergePlan
import com.kabigonis.sorairo.MainActivity.HomeLayer
import com.kabigonis.sorairo.MainActivity.PageAdapter
import com.kabigonis.sorairo.MainActivity.RowAdapter
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_PACKAGE
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_API_COMMAND
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_API_RECEIVER
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_MODE_CHANGED
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_EXTRA_SHUFFLE
import com.kabigonis.sorairo.MainActivity.Companion.POWERAMP_EXTRA_REPEAT
import com.kabigonis.sorairo.MainActivity.Companion.NEW_APP_WINDOW_MS
import com.kabigonis.sorairo.MainActivity.Companion.NEW_APP_MARKER

class MainActivity : AppCompatActivity() {
    internal companion object {
        const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
        const val POWERAMP_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
        const val POWERAMP_API_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
        const val POWERAMP_MODE_CHANGED = "com.maxmpz.audioplayer.PLAYING_MODE_CHANGED"
        const val POWERAMP_EXTRA_SHUFFLE = "shuffle"
        const val POWERAMP_EXTRA_REPEAT = "repeat"
        const val NEW_APP_WINDOW_MS = 60L * 60L * 1000L
        const val NEW_APP_MARKER = " ◆"
    }

    data class App(val label: String, val pkg: String)

    class Pal(
        val name: String, bg: String, fg: String, dim: String,
        blue: String, green: String, accent: String, sel: String,
        ansi: List<String> = emptyList()
    ) {
        val cAnsi: List<Int> = if (ansi.size == 16) ansi.map { Color.parseColor(it) } else listOf(
            Color.parseColor(bg), Color.parseColor("#f7768e"), Color.parseColor(green),
            Color.parseColor(accent), Color.parseColor(blue), Color.parseColor("#bb9af7"),
            Color.parseColor("#7dcfff"), Color.parseColor(fg),
            Color.parseColor(dim), Color.parseColor("#ff899d"), Color.parseColor("#b9f27c"),
            Color.parseColor("#ffda8b"), Color.parseColor("#8db0ff"), Color.parseColor("#c7a0ff"),
            Color.parseColor("#8de6ff"), Color.parseColor(fg)
        )
        val cBg = Color.parseColor(bg)
        val cFg = Color.parseColor(fg)
        val cDim = Color.parseColor(dim)
        val cBlue = Color.parseColor(blue)
        val cGreen = Color.parseColor(green)
        val cAccent = Color.parseColor(accent)
        val cSel = Color.parseColor(sel)
    }

    internal val basePals = listOf(
        Pal("Tokyo Night", "#1a1b26", "#c0caf5", "#565f89", "#7aa2f7", "#9ece6a", "#e0af68", "#33467c"),
        Pal("Catppuccin", "#1e1e2e", "#cdd6f4", "#6c7086", "#89b4fa", "#a6e3a1", "#f9e2af", "#45475a"),
        Pal("Gruvbox", "#282828", "#ebdbb2", "#928374", "#83a598", "#b8bb26", "#fabd2f", "#504945"),
        Pal("Nord", "#2e3440", "#d8dee9", "#4c566a", "#81a1c1", "#a3be8c", "#ebcb8b", "#434c5e"),
        Pal("Dracula", "#282a36", "#f8f8f2", "#6272a4", "#bd93f9", "#50fa7b", "#ff79c6", "#44475a")
    )
    internal var palsCache: List<Pal>? = null
    internal var builtInCount = 0
    internal val pals: List<Pal> get() = palsCache ?: buildPals().also { palsCache = it }

    internal fun invalidatePals() {
        palsCache = null
    }

    internal fun toPal(t: OmarchyThemes.Palette) =
        Pal(t.name, t.bg, t.fg, t.dim, t.blue, t.green, t.accent, t.sel, t.ansiColors)

    internal fun buildPals(): List<Pal> {
        val have = basePals.map { it.name.lowercase().replace(" ", "") }.toSet()
        val builtIn = basePals + OmarchyThemes.loadBundled(this)
            .filter { !it.isLight && it.name.lowercase().replace(" ", "") !in have }
            .map { toPal(it) }
        builtInCount = builtIn.size
        return builtIn + CommunityThemes.loadInstalled(this).map { toPal(it) }
    }
    internal val fontNames = listOf(
        "monospace", "serif-monospace", "sans-serif", "sans-serif-condensed", "casual", "serif"
    )
    internal val termColors = listOf(
        "theme" to 0,
        "green" to Color.parseColor("#9ece6a"),
        "blue" to Color.parseColor("#7aa2f7"),
        "amber" to Color.parseColor("#e0af68"),
        "pink" to Color.parseColor("#f7768e"),
        "white" to Color.parseColor("#ffffff")
    )
    internal val opacities = listOf(40, 60, 85, 100)
    internal val launchModes = listOf("both", "drawer", "terminal")
    internal val logoNames = listOf("android", "arch", "omarchy", "image", "none")

    internal val androidArt = listOf(
        "  ;,           ,;",
        "   ';,.-----.,;'",
        "  ,'           ',",
        " /    O     O    \\",
        "|                 |",
        "'-----------------'"
    ).joinToString("\n")
    internal val archArt = listOf(
        "       /\\",
        "      /  \\",
        "     /    \\",
        "    /      \\",
        "   /   ,,   \\",
        "  /   |  |   \\",
        " /_-''    ''-_\\"
    ).joinToString("\n")
    internal val omarchyArt = listOf(
        "████████████████",
        "██            ██",
        "██  ████████  ██",
        "██  ██    ██  ██",
        "██  ████████  ██",
        "██            ██",
        "████████████████"
    ).joinToString("\n")

    internal val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    /** Android's per-session consent for screen recording. */
    internal val screenCaptureRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onScreenCaptureResult(result.resultCode, result.data)
        }

    /** Device screen-lock authentication used by Sorairo's sudo gate. */
    internal val sudoAuth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onSudoResult(result.resultCode == android.app.Activity.RESULT_OK)
        }

    internal val wifiPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showWifiPanel() else
                Toast.makeText(this, "Location permission is needed to list nearby Wi-Fi networks", Toast.LENGTH_LONG).show()
        }


    internal val pal: Pal get() = pals[prefs.getInt("theme", 0).coerceIn(0, pals.size - 1)]
    internal val font: Typeface
        get() = Typeface.create(
            fontNames[prefs.getInt("font", 0).coerceIn(0, fontNames.size - 1)], Typeface.NORMAL
        )
    internal val mode: String
        get() = launchModes[prefs.getInt("mode", 0).coerceIn(0, launchModes.size - 1)]

    internal fun termFg(): Int {
        val t = termColors[prefs.getInt("tcolor", 0).coerceIn(0, termColors.size - 1)].second
        return if (t == 0) pal.cFg else t
    }

    internal fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(homeIntent, 0)
        return resolved?.activityInfo?.packageName == packageName
    }

    internal fun panelColor(): Int {
        val a = opacities[prefs.getInt("opacity", 2).coerceIn(0, opacities.size - 1)] * 255 / 100
        return (pal.cBg and 0x00FFFFFF) or (a shl 24)
    }

    internal fun solidColor(): Int = (pal.cBg and 0x00FFFFFF) or (0xF2 shl 24)


    // ---- state ----
    internal var allApps: List<App> = emptyList()
    internal var apps: List<App> = emptyList()
    internal var curWs = 0
    internal val focusedWindow = IntArray(5) { 0 } // 0 = terminal, 1 = pinned
    internal val pinWindowForFocus = arrayOfNulls<View>(5)
    internal val bottomWindowForFocus = arrayOfNulls<View>(5)
    internal val terminalWindowForFocus = arrayOfNulls<View>(5)
    internal val tileLayouts = arrayOfNulls<TileLayout>(5)
    internal val moveSource = IntArray(5) { -1 }
    internal lateinit var moveHint: TextView
    internal val tileIds = listOf("terminal", "pinned", "bottom")
    internal val focusBorderAnimators = java.util.WeakHashMap<View, ValueAnimator>()
    internal val focusBorderColors = java.util.WeakHashMap<View, Int>()
    internal lateinit var wall: ImageView
    internal var wallpaperVideoView: android.widget.VideoView? = null
    internal data class WallpaperEntry(
        val id: String,
        val file: String,
        val source: String,
        val credit: String? = null,
        val link: String? = null,
        val remoteName: String? = null,
        val remoteSize: Long = 0L,
        val themeId: String? = null,
        val autoDownloaded: Boolean = false,
        val lastUsed: Long = 0L,
        val sha256: String? = null,
        val mediaType: String = "image"
    )
    internal val wallpaperExecutor = Executors.newFixedThreadPool(4)
    internal val wallpaperThumbCache = object : LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount
    }
    internal var wallpaperPageOpen = false
    internal var wallpaperTab = "theme"
    internal var wallpaperPageShell: LinearLayout? = null
    internal var wallpaperMigrationStarted = false
    internal val screenWallpaperCache = object : LruCache<String, android.graphics.Bitmap>(3) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = 1
    }
    internal val themeWallpaperBusy = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    internal val themeWallpaperCardBusy = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    internal data class ThemeWallpaperSource(
        val id: String, val theme: String, val owner: String, val repo: String, val path: String,
        val omarchy: Boolean = false
    )
    internal data class ThemeWallpaperRemoteFile(
        val name: String, val size: Long, val downloadUrl: String, val htmlUrl: String
    )
    internal val previewExecutor = Executors.newFixedThreadPool(4)
    internal val applyExecutor = Executors.newFixedThreadPool(2)
    internal val bulkExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "theme-wallpaper-bulk").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }
    }
    internal val themeWallpaperJobs = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.CopyOnWriteArrayList<java.util.concurrent.Future<*>>>()
    internal val themeWallpaperOriginalLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    internal var themeWallpaperShown = 8
    internal var themeWallpaperGeneration = 0
    internal var themeWallpaperStatus = ""
    internal var themeWallpaperFiles: List<ThemeWallpaperRemoteFile> = emptyList()
    internal var themeWallpaperLoadedFor: String? = null
    internal var themeWallpaperAllowPreviewLoad = false
    internal data class WallhavenWallpaper(val id: String, val url: String, val resolution: String, val fileSize: Long, val thumbs: JSONObject, val path: String)
    internal data class WallhavenPage(val items: List<WallhavenWallpaper>, val lastPage: Int, val fetchedAt: Long)
    internal val wallhavenPageCache = object : LinkedHashMap<String, WallhavenPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WallhavenPage>?): Boolean = size > 24
    }
    internal var wallhavenLastApiCall = 0L
    internal var wallhavenQuery = ""
    internal var wallhavenMatchTheme = true
    internal var wallhavenColorsEnabled = true
    internal var wallhavenPeopleEnabled = false
    internal var wallhavenSort = "toplist"
    internal var wallhavenGeneration = 0L
    internal val wallhavenThumbJobs = java.util.concurrent.CopyOnWriteArrayList<java.util.concurrent.Future<*>>()
    internal val wallhavenApiExecutor = Executors.newFixedThreadPool(2)
    internal val wallhavenThrottleLock = Any()
    internal var wallhavenPage = 1
    internal var wallhavenLastPage = 1
    internal var wallhavenResults: List<WallhavenWallpaper> = emptyList()
    internal var wallhavenStatus = ""
    internal val wallhavenBusy = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    internal val wallhavenExecutor = Executors.newSingleThreadExecutor()
    internal val wallhavenColors = listOf("660000", "990000", "cc0000", "cc3333", "ea4c88", "993399", "663399", "333399", "0066cc", "0099cc", "66cccc", "77cc33", "669900", "336600", "666600", "999900", "cccc33", "ffff00", "ffcc33", "ff9900", "ff6600", "cc6633", "996633", "663300", "000000", "999999", "cccccc", "ffffff", "424153")
    internal lateinit var clock: TextView
    internal val islandCore = IslandCore()
    internal var islandView: IslandView? = null
    internal var islandEmptyExpanded = false
    internal val islandWake = Runnable { refreshIsland() }
    internal lateinit var status: TextView
    internal lateinit var pager: ViewPager2
    internal val wsLabels = ArrayList<TextView>()
    internal lateinit var drawer: FrameLayout
    internal lateinit var panel: LinearLayout
    internal lateinit var search: EditText
    internal lateinit var settings: FrameLayout
    internal lateinit var setBox: LinearLayout
    internal lateinit var setScroll: ScrollView
    internal val adapter = RowAdapter()
    internal var selectedDrawerApp: App? = null
    internal var activeDrawerActionHolder: RowAdapter.VH? = null

    internal val history = Array(5) { SpannableStringBuilder() }
    internal val outViews = arrayOfNulls<TextView>(5)
    internal val outScrolls = arrayOfNulls<ScrollView>(5)
    internal val pinBoxes = arrayOfNulls<LinearLayout>(5)
    internal val bottomWindowViews = arrayOfNulls<LinearLayout>(5)
    internal val bottomMonitorColumns = arrayOfNulls<LinearLayout>(5)
    internal val bottomWindowContents = arrayOfNulls<ScrollView>(5)
    internal val playerTitles = arrayOfNulls<TextView>(5)
    internal val playerArtists = arrayOfNulls<TextView>(5)
    internal val playerStatuses = arrayOfNulls<TextView>(5)
    internal val playerHeaders = arrayOfNulls<TextView>(5)
    internal val playerTimes = arrayOfNulls<TextView>(5)
    internal val playerQueueViews = arrayOfNulls<LinearLayout>(5)
    internal val playerQueueScrolls = arrayOfNulls<ScrollView>(5)
    internal val playerQueueSignatures = arrayOfNulls<String>(5)
    internal val playerQueueRows = Array(5) { mutableListOf<TextView>() }
    internal val terminalPrompts = arrayOfNulls<EditText>(5)
    internal val playerQueueEmptyLabels = arrayOfNulls<TextView>(5)
    internal val playerPlayButtons = arrayOfNulls<TextView>(5)
    internal val powerampShuffleButtons = arrayOfNulls<TextView>(5)
    internal val powerampRepeatButtons = arrayOfNulls<TextView>(5)
    internal var powerampShuffleMode = 0
    internal var powerampRepeatMode = 0
    internal var powerampModeReceiver: android.content.BroadcastReceiver? = null
    internal val playerProgressBars = arrayOfNulls<android.widget.ProgressBar>(5)
    internal val monitorSummaries = arrayOfNulls<TextView>(5)
    internal val monitorGraphs = arrayOfNulls<NetGraphView>(5)
    internal val sharedNetHistory = NetGraphHistory()
    internal val bottomWindowTitles = arrayOfNulls<TextView>(5)
    internal val defaultLauncherPrompts = arrayOfNulls<TextView>(5)
    internal val fetchInfos = arrayOfNulls<TextView>(5)
    internal lateinit var vis: VisualizerView
    internal lateinit var song: TextView
    internal lateinit var battText: TextView
    internal lateinit var iconBar: LinearLayout
    internal lateinit var barBattery: BatteryIconView
    internal lateinit var barDisplay: DisplayIconView
    internal lateinit var barAudio: AudioIconView
    internal lateinit var barWifi: WifiIconView
    internal val barButtons = HashMap<BarKind, BarIconButton>()
    /** The single active top-bar panel/calendar popup. */
    internal var activePopup: AlertDialog? = null
    internal var activePopupKind: BarKind? = null
    internal var popupHandoffInProgress = false
    internal val iconBarInitialized: Boolean get() = ::iconBar.isInitialized && ::barBattery.isInitialized
    internal var ctrl: MediaController? = null
    internal var listeningController: MediaController? = null
    internal var playerCallback: MediaController.Callback? = null

    internal val handler = Handler(Looper.getMainLooper())

    internal var lastSongDetails = "No active media"
    internal var missingSessionReads = 0

    internal val playerRefresh = object : Runnable {
        override fun run() {
            if (!activityResumed || bottomWindowFor(curWs) != "player") return
            startPlayerCallbacks()
        }
    }


    internal var askedWrite = false

    internal var autoLaunchedQuery: String? = null

    // network speed state
    internal var lastT = 0L
    internal var lastRx = 0L
    internal var lastTx = 0L
    internal var downBps = 0L
    internal var upBps = 0L
    internal var monitorLastT = 0L
    internal var monitorLastRx = 0L
    internal var monitorLastTx = 0L
    internal var monitorLastSummaryT = 0L

    internal val exportConfigFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val values = JSONObject()
                prefs.all.forEach { (key, value) ->
                    when (value) {
                        is Set<*> -> values.put(key, JSONArray(value.filterIsInstance<String>()))
                        is String, is Int, is Long, is Float, is Boolean -> values.put(key, value)
                    }
                }
                val backup = JSONObject()
                    .put("format", "sorairo-config")
                    .put("version", 1)
                    .put("preferences", values)
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                    it.write(backup.toString(2))
                } ?: throw IllegalStateException("Unable to open destination")
                Toast.makeText(this, "Sorairo config exported", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "couldn't export config", Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal val importConfigFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val backup = contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    JSONObject(it.readText())
                } ?: throw IllegalArgumentException("Empty file")
                require(backup.optString("format") == "sorairo-config") { "Not a Sorairo config" }
                require(backup.optInt("version", -1) == 1) { "Unsupported config version" }
                val values = backup.getJSONObject("preferences")
                val editor = prefs.edit().clear()
                val keys = values.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    when (val value = values.get(key)) {
                        is JSONObject -> Unit
                        is JSONArray -> {
                            val set = LinkedHashSet<String>()
                            for (i in 0 until value.length()) {
                                val item = value.optString(i, "")
                                if (item.isNotEmpty()) set.add(item)
                            }
                            editor.putStringSet(key, set)
                        }
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        else -> Unit
                    }
                }
                editor.apply()
                apps = visibleApps()
                adapter.items = apps
                adapter.notifyDataSetChanged()
                rebuild(false)
                fillSettings()
                setScroll.scrollTo(0, 0)
                showOverlay()
                Toast.makeText(this, "Sorairo config imported", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "invalid or unsupported Sorairo config", Toast.LENGTH_LONG).show()
            }
        }
    }

    internal val pickWall = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            wallpaperExecutor.execute {
                val added = try { importWallpaper(uri, "device") } catch (_: Exception) { null }
                handler.post {
                    if (added == null) {
                        Toast.makeText(this, "couldn't add wallpaper", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "${added.mediaType} wallpaper added · tap it to apply", Toast.LENGTH_SHORT).show()
                        if (wallpaperPageOpen && settings.visibility == View.VISIBLE) fillWallpaperSettings()
                    }
                }
            }
        }
    }

    internal var returnToStyleAfterLogoPick = false

    internal val pickLogo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Persist the navigation target because Android may recreate this
        // Activity while the system document picker is open.
        val returnToStyle = returnToStyleAfterLogoPick ||
            prefs.getBoolean("pending_logo_return_style", false)
        returnToStyleAfterLogoPick = false
        prefs.edit().remove("pending_logo_return_style").apply()

        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            prefs.edit().putString("logo_uri", uri.toString()).putInt("logo", 3).apply()
            // Rebuild the home UI without reopening the root config page. The
            // style submenu is restored below after the new UI is in place.
            rebuild(!returnToStyle)
        }

        if (returnToStyle) {
            fillStyleSettings()
            setScroll.scrollTo(0, 0)
            showOverlay()
        }
    }

    internal var touchDownScreenX = 0f
    internal var touchDownScreenY = 0f

    internal val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (moveSource[curWs] >= 0) return false
                if (mode != "terminal" && e1 != null &&
                    e1.y - e2.y > dp(80) && vy < -800 && abs(vx) < abs(vy)
                ) {
                    if (touchDownStartedInCurrentQueue()) return false
                    openDrawer()
                    return true
                }
                return false
            }
        })
    }

    internal val tick = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 2000)
        }
    }

    internal val monitorTick = object : Runnable {
        override fun run() {
            if (!activityResumed) return

            val now = SystemClock.elapsedRealtime()
            if (bottomWindowFor(curWs) == "monitor" &&
                (monitorLastSummaryT == 0L || now - monitorLastSummaryT >= 1000L)
            ) {
                updateMonitorSummary(curWs)
                monitorLastSummaryT = now
            }

            sampleMonitorNetwork()?.let { (down, up) ->
                sharedNetHistory.push(down, up, now)
            }
            monitorGraphs[curWs]?.invalidate()
            handler.postDelayed(this, 1000)
        }
    }

    internal var activityResumed = false

    // True while the app drawer (not the terminal prompt) owns the soft keyboard.
    // Keeps the workspace windows from moving when the drawer's keyboard opens/closes.
    internal var drawerOwnsIme = false

    internal var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // UI scale and text size (Display panel). Cached: dp() runs thousands of times per layout.
    private var uiScaleCache = -1f
    internal val uiScale: Float
        get() {
            if (uiScaleCache < 0f) uiScaleCache = prefs.getFloat("ui_scale", 1f).coerceIn(0.75f, 2.5f)
            return uiScaleCache
        }

    internal fun setUiScale(v: Float) {
        uiScaleCache = v
        prefs.edit().putFloat("ui_scale", v).apply()
    }

    private var uiTextCache = -1
    internal val uiTextSize: Int
        get() {
            if (uiTextCache < 0) uiTextCache = prefs.getInt("text_size", 13).coerceIn(8, 24)
            return uiTextCache
        }

    internal fun setUiTextSize(v: Int) {
        uiTextCache = v
        prefs.edit().putInt("text_size", v).apply()
    }

    internal fun dp(v: Int) = (v * resources.displayMetrics.density * uiScale).toInt()

    internal fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(2), stroke)
    }

    internal fun onFill(preferred: Int, fill: Int): Int =
        Contrast.readable(preferred, fill, pal.cFg, pal.cBg)

    internal fun isLightTheme(p: Pal): Boolean = Contrast.luminance(p.cBg) > 0.4

    internal fun wallpaperScrim(p: Pal): Int =
        if (isLightTheme(p)) (p.cBg and 0x00FFFFFF) or (0x99 shl 24) else Color.parseColor("#55000000")

    internal fun txt(size: Float, color: Int, s: CharSequence = ""): TextView = TextView(this).apply {
        text = s
        textSize = size * uiScale * (uiTextSize / 13f)
        setTextColor(color)
        typeface = font
    }

    internal fun SpannableStringBuilder.add(t: String, c: Int): SpannableStringBuilder {
        val s = length
        append(t)
        setSpan(ForegroundColorSpan(c), s, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    internal inner class HomeLayer(c: Context) : FrameLayout(c) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                touchDownScreenX = ev.rawX
                touchDownScreenY = ev.rawY
            }
            detector.onTouchEvent(ev)
            return super.dispatchTouchEvent(ev)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            // A top-bar tap outside the active popup switches directly to that panel.
            popupHandoffInProgress = routePopupTap(ev)
        }
        if (popupHandoffInProgress) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                popupHandoffInProgress = false
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    // ================= lifecycle =================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideStatusBar()

        allApps = loadApps()
        apps = visibleApps()
        initHistory()
        buildUi()
    }

    internal fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onResume() {
        super.onResume()
        wallpaperVideoView?.start()
        activityResumed = true
        applyTrayStayAwake()
        refreshOmarchyActionTray()
        refreshInstalledApps()
        handler.removeCallbacks(tick)
        tick.run()
        updateNowPlaying()
        pager.post {
            updateBottomWindowDisplay(curWs, false)
            registerActiveSessionsListener()
            updateBottomWindowRuntime()
            updateMonitorSummary(curWs)
            updatePlayerWindow(curWs)
            monitorGraphs[curWs]?.invalidate()

            // Media sessions can become available a little after the launcher
            // resumes (especially after a cold start). Retry discovery instead
            // of requiring the user to switch the monitor/player tab.
            fun retryMediaDiscovery(attempt: Int) {
                if (!activityResumed || attempt >= 8) return
                updateNowPlaying()
                if (bottomWindowFor(curWs) == "player") {
                    val controller = activeController()
                    if (controller != null) {
                        ctrl = controller
                        startPlayerCallbacks()
                        updatePlayerWindow(curWs)
                    } else {
                        handler.postDelayed({ retryMediaDiscovery(attempt + 1) }, 900L)
                    }
                } else if (activeController() == null) {
                    handler.postDelayed({ retryMediaDiscovery(attempt + 1) }, 900L)
                }
            }
            handler.postDelayed({ retryMediaDiscovery(0) }, 400L)
        }
        refreshDefaultLauncherPrompts()
        if (permissionConfigOpen && settings.visibility == View.VISIBLE) {
            fillPermissionSettings()
        }
    }

    override fun onPause() {
        wallpaperVideoView?.pause()
        super.onPause()
        activityResumed = false
        unregisterActiveSessionsListener()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(monitorTick)
        handler.removeCallbacks(islandWake)
        stopPlayerCallbacks()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeDrawer()
        closeSettings()
    }

    @Deprecated("Launcher stays put on back")
    override fun onBackPressed() {
        if (moveSource[curWs] >= 0) endMove()
        else if (settings.visibility == View.VISIBLE) closeSettings() else closeDrawer()
    }

    // ================= UI =================
    internal fun buildUi() {
        val p = pal
        val dm = resources.displayMetrics

        val root = FrameLayout(this).apply { setBackgroundColor(p.cBg) }
        wall = ImageView(this).apply {
            scaleType = if (prefs.getBoolean("wallpaper_parallax", true))
                ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                if (right > left) updateWallpaperParallaxScale()
            }
        }
        root.addView(wall, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            View(this).apply { setBackgroundColor(wallpaperScrim(p)) },
            FrameLayout.LayoutParams(-1, -1)
        )
        loadWallpaper()

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- top bar ----
        val bar = FrameLayout(this)
        setupTopBar(bar)
        moveHint = txt(11f, p.cAccent, "move: tap another window").apply { visibility = View.GONE; gravity = Gravity.CENTER; setOnClickListener { endMove() } }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        wsLabels.clear()
        for (i in 0..4) {
            val t = txt(13f, p.cDim, "${i + 1}").apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener { pager.setCurrentItem(i, true) }
            }
            wsLabels.add(t)
            left.addView(t)
        }
        vis = VisualizerView(this).apply {
            color = p.cGreen
            visibility = View.GONE
        }
        left.addView(vis, LinearLayout.LayoutParams(dp(16), dp(14)).apply {
            marginStart = dp(14); marginEnd = dp(8)
        })
        song = txt(13f, p.cFg).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = maxOf(dp(100), dm.widthPixels / 2 - dp(230))
            visibility = View.GONE
            setOnClickListener { togglePlay() }
        }
        left.addView(song)
        clock = ClockView(this, p.cBlue).apply {
            textSize = 13f * uiScale * (uiTextSize / 13f)
            setTextColor(p.cFg)
            typeface = font
            visibility = View.VISIBLE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { showCalendarPopup() }
        }
        // Headphone name only; the network label moved into the Wi-Fi icon and its panel.
        status = txt(13f, p.cFg).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            visibility = View.GONE
        }
        // Legacy text view, kept off-screen: the battery now lives in the icon row.
        battText = txt(13f, p.cFg)
        barButtons.clear()
        barBattery = BatteryIconView(this, p.cFg, p.cBg or (0xFF shl 24))
        barDisplay = DisplayIconView(this, p.cFg)
        barAudio = AudioIconView(this, p.cFg)
        barWifi = WifiIconView(this, p.cFg)
        fun barButton(kind: BarKind, glyph: View, gw: Int, gh: Int, open: () -> Unit): BarIconButton {
            val b = BarIconButton(this, glyph, p.cBlue, dp(gw), dp(gh))
            b.setOnClickListener { open() }
            barButtons[kind] = b
            return b
        }
        iconBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = { LinearLayout.LayoutParams(dp(38), dp(34)) }
            // left to right: Wi-Fi, audio, display, battery (battery is the right-most)
            addView(barButton(BarKind.WIFI, barWifi, 17, 15) { showNetworkPanel() }, lp())
            addView(barButton(BarKind.AUDIO, barAudio, 17, 15) { showAudioPanel() }, lp())
            addView(barButton(BarKind.DISPLAY, barDisplay, 17, 15) { showDisplayPanel() }, lp())
            addView(barButton(BarKind.BATTERY, barBattery, 10, 17) { showBatteryPanel() }, lp())
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        right.addView(status)
        right.addView(iconBar)
        // Match Omarchy: status actions sit immediately BEFORE the centered clock.
        // The clock remains independently centered and never shifts with tray visibility.
        val centerGroup = FrameLayout(this)
        centerGroup.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val actionTray = createOmarchyActionTray()
        // World clock: a globe right after the clock (mirrors the tray on the left).
        val worldW = dp(30)
        val worldBtn = BarIconButton(this, GlobeIconView(this, p.cFg), p.cBlue, dp(17), dp(17)).apply {
            contentDescription = "World clock"
            setOnClickListener { showWorldClockPanel() }
        }
        barButtons[BarKind.WORLD_CLOCK] = worldBtn
        centerGroup.addView(worldBtn, FrameLayout.LayoutParams(worldW, dp(30), Gravity.CENTER))
        // Keep a generous invisible touch zone so tapping beside the clock reveals the tray.
        // Children are end-aligned, so the visible pinned glyph remains close to the clock.
        val trayWidth = dp(168)
        centerGroup.addView(actionTray, FrameLayout.LayoutParams(trayWidth, dp(30), Gravity.CENTER))
        fun positionActionTray() {
            actionTray.translationX = -(clock.width / 2f - dp(4) + trayWidth / 2f)
            worldBtn.translationX = clock.width / 2f + dp(7)
        }
        centerGroup.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionActionTray() }
        clock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionActionTray() }
        centerGroup.post { positionActionTray() }

        bar.addView(left, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        bar.addView(centerGroup, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        bar.addView(right, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        // Floating bar: inset 12dp from the sides (lined up with the window borders) and 6dp from the top.
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(34)).apply { setMargins(dp(12), dp(6), dp(12), 0) })
        content.addView(moveHint, LinearLayout.LayoutParams(-1, dp(22)).apply { leftMargin = dp(12); rightMargin = dp(12) })

        // ---- workspaces ----
        pager = ViewPager2(this).apply {
            adapter = PageAdapter()
            offscreenPageLimit = 1
            setCurrentItem(curWs, false)
            // Keep the wallpaper aspect ratio intact while reserving only the width
            // needed for the workspace parallax travel.
            updateWallpaperParallaxScale()
            updateWallpaperParallax(curWs.toFloat())
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    updateWallpaperParallax(position + positionOffset)
                }

                override fun onPageSelected(position: Int) {
                    curWs = position
                    applyTileLayout(position, false)
                    updateWsBar()
                    updateBottomWindowRuntime()
                }
            })
        }
        content.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))

        val home = HomeLayer(this)
        home.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        // Dynamic Island is disabled; the centered clock remains the normal top-bar element.

        // ---- drawer ----
        drawer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#99000000"))
            setOnClickListener { closeDrawer() }
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(solidColor(), p.cAccent)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
        }
        search = EditText(this).apply {
            hint = "Search..."
            setHintTextColor(p.cDim)
            setTextColor(p.cFg)
            typeface = font
            textSize = 18f
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(4), dp(8), dp(4), dp(12))
            setOnEditorActionListener { _, _, _ ->
                if (adapter.items.isEmpty()) {
                    val query = text.toString().trim()
                    if (query.isNotEmpty()) {
                        try {
                            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "No app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    launchApp(adapter.items.first())
                }
                true
            }
            doAfterTextChanged { editable ->
                val query = editable?.toString().orEmpty()
                adapter.items = matches(query)
                adapter.notifyDataSetChanged()
                autoLaunchSearchResult(query)
            }
        }
        adapter.items = apps
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@MainActivity.adapter
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, -2))
        panel.addView(View(this).apply { setBackgroundColor(p.cDim) }, LinearLayout.LayoutParams(-1, dp(1)))
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
        val panelTop = (dm.heightPixels * 0.08f).toInt()
        drawer.addView(
            panel,
            FrameLayout.LayoutParams(
                min(dp(640), dm.widthPixels - dp(32)),
                (dm.heightPixels * 0.6f).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = panelTop }
        )
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))

        // ---- settings overlay ----
        settings = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#99000000"))
            setOnClickListener { closeSettings() }
        }
        setBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(solidColor(), p.cAccent)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
        }
        setScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(setBox, FrameLayout.LayoutParams(-1, -2))
        }
        settings.addView(
            setScroll,
            FrameLayout.LayoutParams(min(dp(520), dm.widthPixels - dp(32)), -2, Gravity.CENTER).apply {
                topMargin = dp(24); bottomMargin = dp(24)
            }
        )
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))

        // Keyboard handling: the workspace only follows the keyboard when the
        // terminal prompt owns it (not the app drawer / settings / quick panel).
        fun terminalOwnsIme(): Boolean =
            terminalPrompts.getOrNull(curWs)?.hasFocus() == true &&
                !drawerOwnsIme &&
                drawer.visibility != View.VISIBLE &&
                settings.visibility != View.VISIBLE

        fun applyContentPadding(insets: WindowInsetsCompat) {
            val stable = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // No horizontal padding here: the pager must reach the screen edges so workspaces
            // slide in and out without a wallpaper gap. The bar and each page add their own 12dp.
            content.setPadding(
                0, 0, 0,
                maxOf(stable.bottom, if (terminalOwnsIme()) ime.bottom else 0) + dp(12)
            )
        }

        var imeAnimating = false
        var lastInsets: WindowInsetsCompat? = null

        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    // Runs before the final insets are dispatched. Hold the layout
                    // still so the windows glide with the keyboard instead of jumping.
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) imeAnimating = true
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    applyContentPadding(insets)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        imeAnimating = false
                        lastInsets?.let { applyContentPadding(it) }
                        // Keyboard fully gone: the drawer no longer owns it.
                        if (lastInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0) {
                            drawerOwnsIme = false
                        }
                    }
                }
            }
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            lastInsets = insets
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (ime.bottom == 0 && !imeAnimating) drawerOwnsIme = false
            // Workspace padding is applied here only; overlay keyboards never resize the workspace.
            // While the keyboard is animating, onProgress drives the padding.
            if (!imeAnimating) applyContentPadding(insets)
            val avail = (dm.heightPixels - ime.bottom - panelTop - dp(16)).coerceAtLeast(dp(200))
            panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                height = min((dm.heightPixels * 0.6f).toInt(), avail)
            }
            insets
        }

        setContentView(root)
        updateWsBar()
        updateStatus()
        refreshIsland()
    }

    inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val box: FrameLayout) : RecyclerView.ViewHolder(box)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(FrameLayout(parent.context).apply { layoutParams = ViewGroup.LayoutParams(-1, -1) })

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.box.removeAllViews()
            holder.box.addView(buildPage(position), FrameLayout.LayoutParams(-1, -1))

        }

        override fun getItemCount() = 5
    }

    internal var wifiScanReceiver: android.content.BroadcastReceiver? = null
    internal var wifiNetworkList: LinearLayout? = null
    internal var wifiScanStatus: TextView? = null

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 741) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                wifiDialog?.dismiss()
                showWifiPanel()
            } else {
                wifiScanStatus?.text = "LOCATION PERMISSION DENIED"
            }
        } else if (requestCode == 742 && permissionConfigOpen && settings.visibility == View.VISIBLE) {
            fillPermissionSettings()
        }
    }

    internal var wifiDialog: AlertDialog? = null

    internal var styleConfigOpen = false
    internal var permissionConfigOpen = false

    // ================= community themes =================
    internal val netPool = java.util.concurrent.Executors.newFixedThreadPool(3)
    internal val themeThumbs = android.util.LruCache<String, android.graphics.Bitmap>(30)
    internal var communityCatalog: List<CatalogTheme> = emptyList()
    internal var communityQuery = ""
    internal var communityShown = 32
    internal var communityTab = "browse"
    internal var communityStatus = ""
    internal var communityRenderGeneration = 0
    internal val communityBusy = HashSet<String>()


    // ================= apps / search =================
    internal val appInstallTimes = HashMap<String, Long>()


    // Olauncher GPL-3.0 app-label filtering, adapted to Sorairo's App model.
    internal val searchSeparators = Regex("[-_+,.\\x60'\\s\\p{Z}]")
    internal val searchDiacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        var items: List<App> = emptyList()

        inner class VH(
            val row: FrameLayout,
            val name: TextView,
            val actions: LinearLayout
        ) : RecyclerView.ViewHolder(row) {
            var boundPackage: String? = null

            // Olauncher toggles visibility on the existing ViewHolder itself.
            fun showActions() {
                name.visibility = View.INVISIBLE
                actions.visibility = View.VISIBLE
            }

            fun hideActions() {
                actions.visibility = View.GONE
                name.visibility = View.VISIBLE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                layoutTransition = LayoutTransition()
            }
            val name = txt(16f, pal.cFg).apply {
                setPadding(dp(10), dp(11), dp(10), dp(11))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL)
            }
            val actions = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                isClickable = true
                isFocusable = true
                setPadding(dp(4), 0, dp(4), 0)
                layoutParams = FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL)
                layoutTransition = LayoutTransition()
            }
            row.addView(name)
            row.addView(actions)
            return VH(row, name, actions)
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            val app = items[i]
            val selected = selectedDrawerApp?.pkg == app.pkg

            if (h.boundPackage != app.pkg) {
                h.actions.removeAllViews()
                addDrawerAction(h.actions, "Uninstall", "▣") { runDrawerAction(app, "Uninstall") }
                addDrawerAction(h.actions, "Rename", "✎") { runDrawerAction(app, "Rename") }
                addDrawerAction(h.actions, "Hide", "◉") { runDrawerAction(app, "Hide") }
                addDrawerAction(h.actions, "Info", "ⓘ") { runDrawerAction(app, "Info") }
                addDrawerAction(h.actions, "Close", "×") { runDrawerAction(app, "Close") }
                h.boundPackage = app.pkg
            }

            h.name.text = displayAppLabel(app) + if (isNewApp(app.pkg)) NEW_APP_MARKER else ""
            h.name.setTextColor(if (i == 0) onFill(pal.cAccent, pal.cSel) else pal.cFg)
            h.name.setBackgroundColor(if (i == 0) pal.cSel else Color.TRANSPARENT)
            h.name.setOnClickListener { launchApp(app) }
            h.name.setOnLongClickListener {
                beginDrawerAppActions(app, h)
                true
            }

            if (selected) {
                activeDrawerActionHolder = h
                h.showActions()
            } else {
                if (activeDrawerActionHolder === h) activeDrawerActionHolder = null
                h.hideActions()
            }
        }

        override fun onViewRecycled(holder: VH) {
            if (activeDrawerActionHolder === holder) {
                activeDrawerActionHolder = null
                selectedDrawerApp = null
            }
            holder.hideActions()
            super.onViewRecycled(holder)
        }

        override fun getItemCount() = items.size
    }

    internal data class WallpaperMergePlan(
        val kept: List<WallpaperEntry>,
        val removed: List<WallpaperEntry>,
        val replacements: Map<String, String>
    )



    // Kotlin only allows lateinit isInitialized checks inside the declaring class, so
    // the moved extension functions use these instead of ::x.isInitialized.
    internal val clockInitialized: Boolean get() = ::clock.isInitialized
    internal val panelInitialized: Boolean get() = ::panel.isInitialized
    internal val setBoxInitialized: Boolean get() = ::setBox.isInitialized
    internal val setScrollInitialized: Boolean get() = ::setScroll.isInitialized
    internal val wallInitialized: Boolean get() = ::wall.isInitialized
}



/** Empty listener: enabling its access lets the launcher read what music is playing. */
class NowPlayingService : NotificationListenerService()
