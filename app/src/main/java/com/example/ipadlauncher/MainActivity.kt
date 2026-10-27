package com.example.ipadlauncher

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt
import kotlin.math.sin
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    data class App(val label: String, val pkg: String)

    class Pal(
        val name: String, bg: String, fg: String, dim: String,
        blue: String, green: String, accent: String, sel: String
    ) {
        val cBg = Color.parseColor(bg)
        val cFg = Color.parseColor(fg)
        val cDim = Color.parseColor(dim)
        val cBlue = Color.parseColor(blue)
        val cGreen = Color.parseColor(green)
        val cAccent = Color.parseColor(accent)
        val cSel = Color.parseColor(sel)
    }

    private val pals = listOf(
        Pal("Tokyo Night", "#1a1b26", "#c0caf5", "#565f89", "#7aa2f7", "#9ece6a", "#e0af68", "#33467c"),
        Pal("Catppuccin", "#1e1e2e", "#cdd6f4", "#6c7086", "#89b4fa", "#a6e3a1", "#f9e2af", "#45475a"),
        Pal("Gruvbox", "#282828", "#ebdbb2", "#928374", "#83a598", "#b8bb26", "#fabd2f", "#504945"),
        Pal("Nord", "#2e3440", "#d8dee9", "#4c566a", "#81a1c1", "#a3be8c", "#ebcb8b", "#434c5e"),
        Pal("Dracula", "#282a36", "#f8f8f2", "#6272a4", "#bd93f9", "#50fa7b", "#ff79c6", "#44475a")
    )
    private val fontNames = listOf(
        "monospace", "serif-monospace", "sans-serif", "sans-serif-condensed", "casual", "serif"
    )
    private val termColors = listOf(
        "theme" to 0,
        "green" to Color.parseColor("#9ece6a"),
        "blue" to Color.parseColor("#7aa2f7"),
        "amber" to Color.parseColor("#e0af68"),
        "pink" to Color.parseColor("#f7768e"),
        "white" to Color.parseColor("#ffffff")
    )
    private val opacities = listOf(40, 60, 85, 100)
    private val launchModes = listOf("both", "drawer", "terminal")
    private val logoNames = listOf("android", "arch", "omarchy", "image", "none")

    private val androidArt = listOf(
        "  ;,           ,;",
        "   ';,.-----.,;'",
        "  ,'           ',",
        " /    O     O    \\",
        "|                 |",
        "'-----------------'"
    ).joinToString("\n")
    private val archArt = listOf(
        "       /\\",
        "      /  \\",
        "     /    \\",
        "    /      \\",
        "   /   ,,   \\",
        "  /   |  |   \\",
        " /_-''    ''-_\\"
    ).joinToString("\n")
    private val omarchyArt = listOf(
        "████████████████",
        "██            ██",
        "██  ████████  ██",
        "██  ██    ██  ██",
        "██  ████████  ██",
        "██            ██",
        "████████████████"
    ).joinToString("\n")

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val pal: Pal get() = pals[prefs.getInt("theme", 0).coerceIn(0, pals.size - 1)]
    private val font: Typeface
        get() = Typeface.create(
            fontNames[prefs.getInt("font", 0).coerceIn(0, fontNames.size - 1)], Typeface.NORMAL
        )
    private val mode: String
        get() = launchModes[prefs.getInt("mode", 0).coerceIn(0, launchModes.size - 1)]

    private fun termFg(): Int {
        val t = termColors[prefs.getInt("tcolor", 0).coerceIn(0, termColors.size - 1)].second
        return if (t == 0) pal.cFg else t
    }

    private fun panelColor(): Int {
        val a = opacities[prefs.getInt("opacity", 2).coerceIn(0, opacities.size - 1)] * 255 / 100
        return (pal.cBg and 0x00FFFFFF) or (a shl 24)
    }

    private fun solidColor(): Int = (pal.cBg and 0x00FFFFFF) or (0xF2 shl 24)

    // ---- state ----
    private var allApps: List<App> = emptyList()
    private var apps: List<App> = emptyList()
    private var curWs = 0
    private lateinit var wall: ImageView
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var pager: ViewPager2
    private val wsLabels = ArrayList<TextView>()
    private lateinit var drawer: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var search: EditText
    private lateinit var settings: FrameLayout
    private lateinit var setBox: LinearLayout
    private lateinit var setScroll: ScrollView
    private val adapter = RowAdapter()

    private val history = Array(5) { SpannableStringBuilder() }
    private val outViews = arrayOfNulls<TextView>(5)
    private val outScrolls = arrayOfNulls<ScrollView>(5)
    private val pinBoxes = arrayOfNulls<LinearLayout>(5)
    private val fetchInfos = arrayOfNulls<TextView>(5)
    private lateinit var vis: VisualizerView
    private lateinit var song: TextView
    private lateinit var battText: TextView
    private lateinit var qs: FrameLayout
    private lateinit var qsBox: LinearLayout
    private var ctrl: MediaController? = null
    private var askedWrite = false

    private val handler = Handler(Looper.getMainLooper())
    private var autoRun: Runnable? = null

    // network speed state
    private var lastT = 0L
    private var lastRx = 0L
    private var lastTx = 0L
    private var downBps = 0L
    private var upBps = 0L

    private val pickWall = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            prefs.edit().putString("wall", uri.toString()).apply()
            loadWallpaper()
        }
    }

    private val pickLogo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            prefs.edit().putString("logo_uri", uri.toString()).putInt("logo", 3).apply()
            rebuild(true)
        }
    }

    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (mode != "terminal" && e1 != null &&
                    e1.y - e2.y > dp(80) && vy < -800 && abs(vx) < abs(vy)
                ) {
                    openDrawer()
                    return true
                }
                return false
            }
        })
    }

    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 2000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(2), stroke)
    }

    private fun txt(size: Float, color: Int, s: CharSequence = ""): TextView = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = font
    }

    private fun SpannableStringBuilder.add(t: String, c: Int): SpannableStringBuilder {
        val s = length
        append(t)
        setSpan(ForegroundColorSpan(c), s, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    private inner class HomeLayer(c: Context) : FrameLayout(c) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            detector.onTouchEvent(ev)
            return super.dispatchTouchEvent(ev)
        }
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

    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeDrawer()
        closeSettings()
        closeQs()
    }

    @Deprecated("Launcher stays put on back")
    override fun onBackPressed() {
        if (qs.visibility == View.VISIBLE) closeQs()
        else if (settings.visibility == View.VISIBLE) closeSettings() else closeDrawer()
    }

    // ================= UI =================
    private fun buildUi() {
        val p = pal
        val dm = resources.displayMetrics

        val root = FrameLayout(this).apply { setBackgroundColor(p.cBg) }
        wall = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(wall, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            View(this).apply { setBackgroundColor(Color.parseColor("#55000000")) },
            FrameLayout.LayoutParams(-1, -1)
        )
        loadWallpaper()

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- top bar ----
        val bar = FrameLayout(this)
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
        clock = txt(13f, p.cFg)
        status = txt(13f, p.cFg)
        battText = txt(13f, p.cFg).apply {
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnLongClickListener { openQs(); true }
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        right.addView(status)
        right.addView(battText)
        bar.addView(left, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        bar.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        bar.addView(right, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(34)))

        // ---- workspaces ----
        pager = ViewPager2(this).apply {
            adapter = PageAdapter()
            offscreenPageLimit = 1
            setCurrentItem(curWs, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    curWs = position
                    updateWsBar()
                }
            })
        }
        content.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))

        val home = HomeLayer(this)
        home.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

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
                adapter.items.firstOrNull()?.let { launchApp(it) }
                true
            }
            doAfterTextChanged { t ->
                val q = t?.toString()?.trim().orEmpty()
                adapter.items = matches(q)
                adapter.notifyDataSetChanged()
                autoRun?.let { handler.removeCallbacks(it) }
                if (q.length >= 2 && adapter.items.size == 1) {
                    val app = adapter.items[0]
                    val r = Runnable {
                        if (drawer.visibility == View.VISIBLE &&
                            adapter.items.size == 1 && adapter.items[0] == app
                        ) launchApp(app)
                    }
                    autoRun = r
                    handler.postDelayed(r, 400)
                }
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

        // ---- quick settings overlay ----
        qs = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#55000000"))
            setOnClickListener { closeQs() }
        }
        qsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(solidColor(), p.cAccent)
            setPadding(dp(16), dp(12), dp(16), dp(14))
            isClickable = true
        }
        qs.addView(
            qsBox,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(44); marginEnd = dp(12)
            }
        )
        root.addView(qs, FrameLayout.LayoutParams(-1, -1))

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

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            // Stable insets: layout never shifts when the system status bar is pulled down
            val stable = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            content.setPadding(dp(12), dp(6), dp(12), maxOf(stable.bottom, ime.bottom) + dp(12))
            val avail = (dm.heightPixels - ime.bottom - panelTop - dp(16)).coerceAtLeast(dp(200))
            panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                height = min((dm.heightPixels * 0.6f).toInt(), avail)
            }
            insets
        }

        setContentView(root)
        updateWsBar()
        updateStatus()
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

    private fun buildPage(ws: Int): View {
        val p = pal
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            setOnLongClickListener { showSettings(); true }
        }

        // ---- terminal window ----
        val term = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = box(panelColor(), p.cAccent)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(buildFetch(ws), LinearLayout.LayoutParams(-1, -2))
        val out = txt(12.5f, termFg()).apply {
            setLineSpacing(0f, 1.15f)
            text = history[ws]
        }
        col.addView(out, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(-1, -2))
        }
        outViews[ws] = out
        outScrolls[ws] = scroll
        term.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        var input: EditText? = null
        if (mode != "drawer") {
            val promptRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            promptRow.addView(
                txt(14f, p.cFg, SpannableStringBuilder().add("~ ", p.cBlue).add("❯ ", p.cGreen))
            )
            input = EditText(this).apply {
                setTextColor(termFg())
                setHintTextColor(p.cDim)
                hint = "type an app or command"
                typeface = font
                textSize = 14f
                background = null
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_GO
                setPadding(0, dp(8), 0, dp(8))
                setOnEditorActionListener { v, _, _ ->
                    val cmd = v.text.toString().trim()
                    v.text = ""
                    runCommand(ws, cmd)
                    true
                }
            }
            promptRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            term.addView(promptRow, LinearLayout.LayoutParams(-1, -2))
        }
        val field = input
        fun focusInput() {
            if (field != null) {
                field.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        if (field != null) {
            term.setOnClickListener { focusInput() }
            out.setOnClickListener { focusInput() }
            col.setOnClickListener { focusInput() }
            scroll.setOnClickListener { focusInput() }
        }
        term.setOnLongClickListener { showSettings(); true }
        out.setOnLongClickListener { showSettings(); true }
        col.setOnLongClickListener { showSettings(); true }

        row.addView(term, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(6) })

        // ---- pinned window ----
        val pinWin = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = box(panelColor(), p.cDim)
            setOnLongClickListener { showSettings(); true }
        }
        pinWin.addView(txt(13f, p.cBlue, "~/pinned"))
        val pinList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pinBoxes[ws] = pinList
        pinWin.addView(pinList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        fillPins(ws)
        row.addView(pinWin, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(6) })

        return row
    }

    // ---- fastfetch block (runs by default on every workspace) ----
    private fun buildFetch(ws: Int): View {
        val p = pal
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        v.addView(
            txt(
                12.5f, p.cFg,
                SpannableStringBuilder().add("~ ", p.cBlue).add("❯ ", p.cGreen).add("fastfetch", termFg())
            )
        )
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
        }
        logoView()?.let { r.addView(it) }
        val info = txt(12f, termFg()).apply {
            setLineSpacing(0f, 1.15f)
            text = fetchText()
        }
        fetchInfos[ws] = info
        r.addView(info, LinearLayout.LayoutParams(-2, -2))
        v.addView(r, LinearLayout.LayoutParams(-1, -2))
        return v
    }

    private fun logoDrawable(): Drawable? {
        val s = prefs.getString("logo_uri", null) ?: return null
        return try {
            val uri = Uri.parse(s)
            if (Build.VERSION.SDK_INT >= 28) {
                val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
                    val m = maxOf(info.size.width, info.size.height)
                    if (m > 320) dec.setTargetSize(info.size.width * 320 / m, info.size.height * 320 / m)
                }
                if (d is AnimatedImageDrawable) {
                    d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    d.start()
                }
                d
            } else {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                    ?.let { BitmapDrawable(resources, it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun logoView(): View? {
        val p = pal
        val name = logoNames[prefs.getInt("logo", 0).coerceIn(0, logoNames.size - 1)]
        if (name == "none") return null
        if (name == "image") {
            val d = logoDrawable()
            if (d != null) {
                return ImageView(this).apply {
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply { marginEnd = dp(18) }
                }
            }
        }
        val art = when (name) {
            "arch" -> archArt
            "omarchy" -> omarchyArt
            else -> androidArt
        }
        return TextView(this).apply {
            text = art
            typeface = Typeface.MONOSPACE
            textSize = 8f
            includeFontPadding = false
            setTextColor(if (name == "arch") p.cBlue else p.cGreen)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(18) }
        }
    }

    private fun fetchText(): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        fetchInto(sb)
        return sb
    }

    private fun refreshFetch(ws: Int) {
        fetchInfos[ws]?.text = fetchText()
    }

    private fun disk(path: String): String = try {
        val s = StatFs(path)
        val total = s.blockCountLong * s.blockSizeLong
        val free = s.availableBlocksLong * s.blockSizeLong
        val gb = 1024.0 * 1024 * 1024
        "%.1f / %.1f GiB (%d%%)".format((total - free) / gb, total / gb, ((total - free) * 100 / total).toInt())
    } catch (_: Exception) {
        "n/a"
    }

    private fun sdPath(): String? {
        val dirs = getExternalFilesDirs(null)
        for (i in 1 until dirs.size) {
            val d = dirs[i] ?: continue
            try {
                if (Environment.isExternalStorageRemovable(d)) return d.path
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun battTemp(): Float? {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        return if (t > 0) t / 10f else null
    }

    private fun cpuTemp(): Float? {
        var best: Float? = null
        for (i in 0..19) {
            try {
                val f = File("/sys/class/thermal/thermal_zone$i/temp")
                if (!f.canRead()) continue
                val v = f.readText().trim().toFloat()
                val c = if (v > 1000f) v / 1000f else v
                val b = best
                if (c in 10f..120f && (b == null || c > b)) best = c
            } catch (_: Exception) {
            }
        }
        return best
    }

    private fun fetchInto(sb: SpannableStringBuilder) {
        val p = pal
        val fg = termFg()
        fun kv(k: String, v: String) {
            sb.add(k.padEnd(8), p.cBlue).add(v + "\n", fg)
        }

        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val gb = 1024.0 * 1024 * 1024
        val dm = resources.displayMetrics
        val mins = SystemClock.elapsedRealtime() / 60000
        val bt = battTemp()
        val ct = cpuTemp()
        val sd = sdPath()

        sb.add("┌─ Hardware ───────────\n", p.cDim)
        kv("PC", "${Build.MANUFACTURER} ${Build.MODEL}")
        kv("SoC", Build.HARDWARE)
        kv("RAM", "%.1f / %.1f GiB".format((mi.totalMem - mi.availMem) / gb, mi.totalMem / gb))
        kv("Screen", "${dm.widthPixels}x${dm.heightPixels}")
        kv(
            "Temp",
            (if (bt != null) "batt %.1f°C".format(bt) else "batt n/a") + "  " +
                (if (ct != null) "cpu %.0f°C".format(ct) else "cpu n/a")
        )
        sb.add("\n┌─ Storage ────────────\n", p.cDim)
        kv("Disk", disk(Environment.getDataDirectory().path))
        kv("SD", if (sd != null) disk(sd) else "not inserted")
        sb.add("\n┌─ Software ───────────\n", p.cDim)
        kv("OS", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        kv("Kernel", System.getProperty("os.version") ?: "?")
        kv("Shell", "omarchy-launcher")
        kv("Apps", "${apps.size} (${allApps.size - apps.size} hidden)")
        kv("Theme", pal.name)
        kv("Up", "${mins / 60}h ${mins % 60}m")
        sb.add("\ntype help for commands\n", p.cDim)
    }

    private fun fillPins(ws: Int) {
        val p = pal
        val list = pinBoxes[ws] ?: return
        list.removeAllViews()
        val pinned = pins(ws).mapNotNull { k -> apps.find { it.pkg == k } }
        if (pinned.isEmpty()) {
            list.addView(
                txt(
                    12.5f, p.cDim,
                    "nothing pinned yet.\nterminal: pin <app>\ndrawer: long-press an app"
                ).apply { setOnLongClickListener { showSettings(); true } }
            )
            return
        }
        pinned.forEach { app ->
            list.addView(
                txt(16f, p.cFg, app.label).apply {
                    setPadding(0, dp(9), 0, dp(9))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener { launchApp(app) }
                    setOnLongClickListener { togglePin(ws, app); true }
                }
            )
        }
    }

    private fun updateWsBar() {
        wsLabels.forEachIndexed { i, t ->
            if (i == curWs) {
                t.text = "●"
                t.setTextColor(pal.cFg)
            } else {
                t.text = "${i + 1}"
                t.setTextColor(pal.cDim)
            }
        }
    }

    // ================= settings =================
    private fun showOverlay() {
        settings.visibility = View.VISIBLE
        settings.alpha = 0f
        settings.animate().alpha(1f).setDuration(140).start()
    }

    private fun showSettings() {
        closeDrawer()
        closeQs()
        fillSettings()
        setScroll.scrollTo(0, 0)
        showOverlay()
    }

    // ================= quick settings =================
    private fun openQs() {
        closeDrawer()
        askedWrite = false
        fillQs()
        qs.visibility = View.VISIBLE
        qs.alpha = 0f
        qs.animate().alpha(1f).setDuration(140).start()
    }

    private fun closeQs() {
        if (qs.visibility != View.VISIBLE) return
        qs.animate().alpha(0f).setDuration(120).withEndAction { qs.visibility = View.GONE }.start()
    }

    private fun openSystem(action: String) {
        closeQs()
        closeSettings()
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            Toast.makeText(this, "Can't open that setting", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mediaAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun setBrightness(v: Float) {
        try {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                    (v * 255).roundToInt().coerceIn(1, 255)
                )
            } else if (!askedWrite) {
                askedWrite = true
                closeQs()
                Toast.makeText(this, "Allow \"modify system settings\" for brightness", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            }
        } catch (_: Exception) {
        }
    }

    private fun launchVpn() {
        val app = allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }
        if (app == null) showVpnPicker() else launchApp(app)
    }

    private fun fillVpnPicker() {
        val p = pal
        setBox.removeAllViews()
        setBox.addView(txt(15f, p.cBlue, "~/vpn-app"))
        setBox.addView(
            txt(11f, p.cDim, "pick the app the vpn tile opens").apply { setPadding(0, dp(2), 0, dp(6)) }
        )
        setBox.addView(txt(14f, p.cAccent, "← back").apply {
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { fillSettings(); setScroll.scrollTo(0, 0) }
        })
        allApps.forEach { app ->
            val chosen = app.pkg == prefs.getString("vpn_pkg", null)
            setBox.addView(
                txt(14f, if (chosen) p.cAccent else p.cFg, app.label).apply {
                    setPadding(0, dp(9), 0, dp(9))
                    setOnClickListener {
                        prefs.edit().putString("vpn_pkg", app.pkg).apply()
                        closeSettings()
                    }
                }
            )
        }
    }

    private fun showVpnPicker() {
        closeQs()
        fillVpnPicker()
        setScroll.scrollTo(0, 0)
        showOverlay()
    }

    private fun fillQs() {
        val p = pal
        qsBox.removeAllViews()
        qsBox.addView(txt(15f, p.cBlue, "~/quick"))

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = netLabel()
        val wifiOn = net.startsWith("wifi")
        val btOn = try {
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
        val ring = am.ringerMode
        val airplane = try {
            Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) {
            false
        }
        val saver = (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
        val micMuted = am.isMicrophoneMute
        val vpnOn = try {
            cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) {
            false
        }
        val vpnApp = allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }

        fun tile(name: String, state: String, on: Boolean, hold: (() -> Unit)? = null, tap: () -> Unit): View {
            val t = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = box(
                    if (on) p.cSel else (p.cSel and 0x00FFFFFF) or (0x44 shl 24),
                    if (on) p.cAccent else p.cDim
                )
                setOnClickListener { tap() }
                if (hold != null) setOnLongClickListener { hold(); true }
            }
            t.addView(txt(14f, p.cFg, name))
            t.addView(txt(11f, if (on) p.cAccent else p.cDim, state))
            return t
        }

        val ringState = when (ring) {
            AudioManager.RINGER_MODE_SILENT -> "on"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "off"
        }
        val tiles = listOf(
            tile("wifi", if (wifiOn) net else "off", wifiOn) {
                openSystem(
                    if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                    else Settings.ACTION_WIFI_SETTINGS
                )
            },
            tile("bluetooth", if (btOn) "on" else "off", btOn) {
                openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)
            },
            tile("silent", ringState, ring == AudioManager.RINGER_MODE_SILENT) {
                try {
                    am.ringerMode =
                        if (ring == AudioManager.RINGER_MODE_SILENT) AudioManager.RINGER_MODE_NORMAL
                        else AudioManager.RINGER_MODE_SILENT
                    fillQs()
                } catch (_: SecurityException) {
                    Toast.makeText(this, "Allow Do Not Disturb access first", Toast.LENGTH_LONG).show()
                    openSystem(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                }
            },
            tile("airplane", if (airplane) "on" else "off", airplane) {
                openSystem(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            },
            tile("battery saver", if (saver) "on" else "off", saver) {
                openSystem(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            },
            tile("mic", if (micMuted) "muted" else "live", micMuted, { openSystem(Settings.ACTION_PRIVACY_SETTINGS) }) {
                am.setMicrophoneMute(!micMuted)
                fillQs()
            },
            tile("camera", "privacy ›", false) { openSystem(Settings.ACTION_PRIVACY_SETTINGS) },
            tile(
                "vpn", if (vpnOn) "connected" else (vpnApp?.label ?: "pick app"), vpnOn,
                { showVpnPicker() }
            ) { launchVpn() }
        )

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in tiles.indices step 2) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(tiles[i], LinearLayout.LayoutParams(dp(130), dp(62)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            if (i + 1 < tiles.size) {
                r.addView(tiles[i + 1], LinearLayout.LayoutParams(dp(130), dp(62)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            grid.addView(r)
        }

        val bright = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        } catch (_: Exception) {
            0.5f
        }
        val maxV = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxV

        fun sliderCol(label: String, init: Float, onChange: (Float) -> Unit): View {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val pct = txt(11f, p.cDim, "${(init * 100).roundToInt()}%")
            val sl = VSlider(this, p.cAccent, (p.cDim and 0x00FFFFFF) or (0x66 shl 24)) { v ->
                pct.text = "${(v * 100).roundToInt()}%"
                onChange(v)
            }
            sl.value = init
            c.addView(sl, LinearLayout.LayoutParams(dp(46), dp(250)))
            c.addView(pct, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
            c.addView(txt(11f, p.cFg, label))
            return c
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        body.addView(grid)
        body.addView(
            sliderCol("bright", bright) { v -> setBrightness(v) },
            LinearLayout.LayoutParams(dp(60), -2).apply { marginStart = dp(10) }
        )
        body.addView(
            sliderCol("vol", vol) { v ->
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxV).roundToInt(), 0)
            },
            LinearLayout.LayoutParams(dp(60), -2).apply { marginStart = dp(4) }
        )
        qsBox.addView(body)
    }

    // ================= now playing =================
    private fun togglePlay() {
        val c = ctrl ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
        handler.postDelayed({ updateNowPlaying() }, 400)
    }

    private fun updateNowPlaying() {
        var title: String? = null
        var playing = false
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(this, NowPlayingService::class.java))
            val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull {
                    val st = it.playbackState?.state
                    st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING
                }
            ctrl = c
            val md = c?.metadata
            val t = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val a = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (!t.isNullOrEmpty()) {
                title = if (a.isNullOrEmpty()) t else "$t — $a"
                playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        } catch (_: Exception) {
        }
        if (title == null) {
            song.visibility = View.GONE
            vis.visibility = View.GONE
            vis.playing = false
        } else {
            song.text = title
            song.setTextColor(if (playing) pal.cFg else pal.cDim)
            song.visibility = View.VISIBLE
            vis.visibility = View.VISIBLE
            vis.color = if (playing) pal.cGreen else pal.cDim
            vis.playing = playing
        }
    }

    private fun closeSettings() {
        if (settings.visibility != View.VISIBLE) return
        settings.animate().alpha(0f).setDuration(120)
            .withEndAction { settings.visibility = View.GONE }.start()
    }

    private fun cycle(key: String, n: Int, def: Int = 0) {
        prefs.edit().putInt(key, (prefs.getInt(key, def) + 1) % n).apply()
        rebuild(true)
    }

    private fun fillSettings() {
        val p = pal
        setBox.removeAllViews()
        setBox.addView(txt(15f, p.cBlue, "~/config"))
        setBox.addView(txt(11f, p.cDim, "tap a row to change it").apply { setPadding(0, dp(2), 0, dp(6)) })

        fun row(k: String, v: String, onTap: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener { onTap() }
            }
            r.addView(txt(14f, p.cFg, k), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(txt(14f, p.cAccent, v))
            setBox.addView(r)
        }

        row("wallpaper", "choose ›") { closeSettings(); pickWall.launch(arrayOf("image/*")) }
        row("clear wallpaper", "›") {
            prefs.edit().remove("wall").apply()
            wall.setImageDrawable(null)
        }
        row("theme", pal.name) { cycle("theme", pals.size) }
        row("font", fontNames[prefs.getInt("font", 0).coerceIn(0, fontNames.size - 1)]) {
            cycle("font", fontNames.size)
        }
        row("terminal color", termColors[prefs.getInt("tcolor", 0).coerceIn(0, termColors.size - 1)].first) {
            cycle("tcolor", termColors.size)
        }
        row("window opacity", "${opacities[prefs.getInt("opacity", 2).coerceIn(0, opacities.size - 1)]}%") {
            cycle("opacity", opacities.size, 2)
        }
        row("launch style", mode) { cycle("mode", launchModes.size) }
        row("fetch logo", logoNames[prefs.getInt("logo", 0).coerceIn(0, logoNames.size - 1)]) {
            val n = (prefs.getInt("logo", 0) + 1) % logoNames.size
            prefs.edit().putInt("logo", n).apply()
            if (logoNames[n] == "image" && prefs.getString("logo_uri", null) == null) {
                closeSettings()
                pickLogo.launch(arrayOf("image/*"))
            } else {
                rebuild(true)
            }
        }
        row("logo image / gif", "choose ›") { closeSettings(); pickLogo.launch(arrayOf("image/*")) }
        row("media access", if (mediaAccess()) "granted" else "enable ›") {
            openSystem(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        row("vpn app", allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }?.label ?: "choose ›") {
            fillVpnPicker(); setScroll.scrollTo(0, 0)
        }
        row("hidden apps", "${allApps.size - apps.size} ›") { fillHidden(); setScroll.scrollTo(0, 0) }
        row("close", "esc") { closeSettings() }
    }

    private fun fillHidden() {
        val p = pal
        setBox.removeAllViews()
        setBox.addView(txt(15f, p.cBlue, "~/hidden-apps"))
        setBox.addView(
            txt(11f, p.cDim, "tap an app to hide or show it in this launcher").apply {
                setPadding(0, dp(2), 0, dp(6))
            }
        )
        setBox.addView(txt(14f, p.cAccent, "← back").apply {
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { fillSettings(); setScroll.scrollTo(0, 0) }
        })
        allApps.forEach { app ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(9), 0, dp(9))
            }
            val name = txt(14f, p.cFg, app.label)
            val st = txt(14f, p.cGreen, "shown")
            fun paint() {
                val h = app.pkg in hiddenSet()
                name.setTextColor(if (h) p.cDim else p.cFg)
                st.text = if (h) "hidden" else "shown"
                st.setTextColor(if (h) p.cAccent else p.cGreen)
            }
            paint()
            r.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(st)
            r.setOnClickListener { toggleHidden(app); paint() }
            setBox.addView(r)
        }
    }

    private fun rebuild(reopenSettings: Boolean) {
        initHistory()
        buildUi()
        if (reopenSettings) showSettings()
    }

    // ================= hidden apps =================
    private fun hiddenSet(): Set<String> =
        (prefs.getString("hidden", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    private fun visibleApps(): List<App> {
        val h = hiddenSet()
        return allApps.filter { it.pkg !in h }
    }

    private fun toggleHidden(app: App) {
        val h = hiddenSet().toMutableSet()
        if (!h.remove(app.pkg)) h.add(app.pkg)
        prefs.edit().putString("hidden", h.joinToString(",")).apply()
        apps = visibleApps()
        adapter.items = apps
        adapter.notifyDataSetChanged()
        for (i in 0..4) fillPins(i)
    }

    // ================= terminal =================
    private fun initHistory() {
        for (i in 0..4) history[i].clear()
    }

    private fun refreshOut(ws: Int) {
        outViews[ws]?.text = history[ws]
        outScrolls[ws]?.post { outScrolls[ws]?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun norm(s: String) = s.lowercase().replace(" ", "")

    private fun runCommand(ws: Int, line: String) {
        val p = pal
        val sb = history[ws]
        val fg = termFg()
        sb.add("~ ", p.cBlue).add("❯ ", p.cGreen).add("$line\n", fg)
        fun say(s: String, c: Int = fg) {
            sb.add(s + "\n", c)
        }

        if (line.isNotEmpty()) {
            val parts = line.split(Regex("\\s+"), 2)
            val cmd = parts[0].lowercase()
            val arg = parts.getOrElse(1) { "" }.trim()
            when (cmd) {
                "help" -> listOf(
                    "  help           this list",
                    "  clear          clear screen",
                    "  fetch          refresh system info",
                    "  logo [name]    android/arch/omarchy/image/none",
                    "  ws <1-5>       switch workspace",
                    "  theme [name]   change theme",
                    "  font [name]    change font",
                    "  wall           pick wallpaper",
                    "  config         open settings",
                    "  pin <app>      pin to this workspace",
                    "  unpin <app>    unpin it",
                    "  hide <app>     hide app from launcher",
                    "  unhide <app>   show it again",
                    "  hidden         list hidden apps",
                    "  ls             list apps",
                    "  qs             quick settings panel",
                    "  vpn            open your vpn app",
                    "  <app name>     launch (fuzzy match)"
                ).forEach { say(it) }
                "clear" -> sb.clear()
                "fetch", "fastfetch", "neofetch" -> refreshFetch(ws)
                "ws" -> {
                    val n = arg.toIntOrNull()
                    if (n != null && n in 1..5) pager.setCurrentItem(n - 1, true)
                    else say("usage: ws 1-5", p.cDim)
                }
                "wall", "wallpaper" -> pickWall.launch(arrayOf("image/*"))
                "config", "settings" -> showSettings()
                "logo" -> {
                    val i = logoNames.indexOfFirst { it.startsWith(arg.lowercase()) }
                    if (arg.isEmpty() || i < 0) {
                        say("logos: " + logoNames.joinToString(", "), p.cDim)
                    } else if (logoNames[i] == "image") {
                        pickLogo.launch(arrayOf("image/*"))
                    } else {
                        prefs.edit().putInt("logo", i).apply()
                        rebuild(false)
                        return
                    }
                }
                "theme" -> {
                    val i = pals.indexOfFirst { norm(it.name).contains(norm(arg)) }
                    if (arg.isEmpty() || i < 0) {
                        say("themes: " + pals.joinToString(", ") { it.name.lowercase() }, p.cDim)
                    } else {
                        prefs.edit().putInt("theme", i).apply()
                        rebuild(false)
                        return
                    }
                }
                "font" -> {
                    val i = fontNames.indexOfFirst { it.contains(arg.lowercase()) }
                    if (arg.isEmpty() || i < 0) {
                        say("fonts: " + fontNames.joinToString(", "), p.cDim)
                    } else {
                        prefs.edit().putInt("font", i).apply()
                        rebuild(false)
                        return
                    }
                }
                "pin", "unpin" -> {
                    val a = matches(arg).firstOrNull()
                    if (arg.isEmpty() || a == null) {
                        say("usage: $cmd <app>", p.cDim)
                    } else {
                        val has = a.pkg in pins(ws)
                        if ((cmd == "pin" && !has) || (cmd == "unpin" && has)) togglePin(ws, a)
                        say(if (cmd == "pin") "pinned ${a.label}" else "unpinned ${a.label}", p.cGreen)
                    }
                }
                "hide" -> {
                    val a = matches(arg).firstOrNull()
                    if (arg.isEmpty() || a == null) {
                        say("usage: hide <app>", p.cDim)
                    } else {
                        toggleHidden(a)
                        say("hidden ${a.label}", p.cGreen)
                    }
                }
                "unhide" -> {
                    val h = hiddenSet()
                    val a = allApps.firstOrNull {
                        it.pkg in h && it.label.lowercase().contains(arg.lowercase())
                    }
                    if (arg.isEmpty() || a == null) {
                        say("usage: unhide <app>  (see: hidden)", p.cDim)
                    } else {
                        toggleHidden(a)
                        say("unhidden ${a.label}", p.cGreen)
                    }
                }
                "hidden" -> {
                    val h = hiddenSet()
                    val names = allApps.filter { it.pkg in h }.joinToString("  ") { it.label.lowercase() }
                    say(if (names.isEmpty()) "no hidden apps" else names, p.cDim)
                }
                "qs" -> openQs()
                "vpn" -> launchVpn()
                "ls" -> say(apps.joinToString("  ") { it.label.lowercase() }, p.cDim)
                else -> {
                    val a = matches(line).firstOrNull()
                    if (a != null) {
                        say("launching ${a.label}...", p.cGreen)
                        refreshOut(ws)
                        launchApp(a)
                        return
                    } else {
                        say("command not found: $line", p.cAccent)
                    }
                }
            }
        }
        if (sb.length > 8000) sb.delete(0, sb.length - 5000)
        refreshOut(ws)
    }

    // ================= apps / search =================
    private fun loadApps(): List<App> {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
        }
        return d[a.length][b.length]
    }

    private fun score(q: String, l: String): Int {
        if (l.startsWith(q)) return 1000 - l.length
        val words = l.split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 800 - l.length
        val idx = l.indexOf(q)
        if (idx >= 0) return 600 - idx
        var qi = 0
        var gaps = 0
        var last = -1
        for (i in l.indices) {
            if (qi < q.length && l[i] == q[qi]) {
                if (last >= 0 && i - last > 1) gaps++
                last = i
                qi++
            }
        }
        if (qi == q.length && q.length >= 2) {
            return (400 - gaps * 20 - (l.length - q.length)).coerceAtLeast(1)
        }
        if (q.length >= 3) {
            val tol = if (q.length >= 6) 2 else 1
            if (lev(q, l.take(q.length)) <= tol || lev(q, l.take(q.length + 1)) <= tol) return 200
        }
        return 0
    }

    private fun matches(query: String): List<App> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.map { it to score(q, it.label.lowercase()) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<App, Int>> { it.second }
                    .thenBy { it.first.label.lowercase() }
            )
            .map { it.first }
    }

    private fun launchApp(app: App) {
        closeDrawer()
        closeQs()
        packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
    }

    private fun pins(ws: Int): List<String> =
        (prefs.getString("pins_$ws", "") ?: "").split(",").filter { it.isNotEmpty() }

    private fun togglePin(ws: Int, app: App): Boolean {
        val l = pins(ws).toMutableList()
        val added = !l.remove(app.pkg)
        if (added) l.add(app.pkg)
        prefs.edit().putString("pins_$ws", l.joinToString(",")).apply()
        fillPins(ws)
        adapter.notifyDataSetChanged()
        return added
    }

    // ================= drawer =================
    private fun openDrawer() {
        if (drawer.visibility == View.VISIBLE || settings.visibility == View.VISIBLE) return
        search.setText("")
        drawer.visibility = View.VISIBLE
        drawer.alpha = 0f
        panel.translationY = dp(80).toFloat()
        drawer.animate().alpha(1f).setDuration(160).start()
        panel.animate().translationY(0f).setDuration(200).start()
        search.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeDrawer() {
        autoRun?.let { handler.removeCallbacks(it) }
        if (drawer.visibility != View.VISIBLE) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        drawer.animate().alpha(0f).setDuration(140)
            .withEndAction { drawer.visibility = View.GONE }.start()
    }

    inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        var items: List<App> = emptyList()

        inner class VH(val t: TextView) : RecyclerView.ViewHolder(t)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val t = txt(16f, pal.cFg).apply {
                setPadding(dp(10), dp(11), dp(10), dp(11))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            return VH(t)
        }

        override fun onBindViewHolder(h: VH, i: Int) {
            val app = items[i]
            h.t.text = app.label
            h.t.setTextColor(if (app.pkg in pins(curWs)) pal.cAccent else pal.cFg)
            h.t.setBackgroundColor(if (i == 0) pal.cSel else Color.TRANSPARENT)
            h.t.setOnClickListener { launchApp(app) }
            h.t.setOnLongClickListener {
                val added = togglePin(curWs, app)
                Toast.makeText(
                    this@MainActivity,
                    (if (added) "Pinned " else "Unpinned ") + app.label + " (workspace ${curWs + 1})",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }

        override fun getItemCount() = items.size
    }

    // ================= wallpaper / status =================
    private fun loadWallpaper() {
        val s = prefs.getString("wall", null) ?: return
        try {
            val uri = Uri.parse(s)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 2560) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp != null) wall.setImageBitmap(bmp)
        } catch (_: Exception) {
        }
    }

    private fun fmtSpeed(b: Long): String = when {
        b >= 1024 * 1024 -> "%.1f MB/s".format(b / 1048576.0)
        b >= 1024 -> "${b / 1024} KB/s"
        else -> "$b B/s"
    }

    private fun headphones(): String? {
        return try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val types = setOf(3, 4, 7, 8, 22, 26)
            val d = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in types }
                ?: return null
            val n = d.productName?.toString().orEmpty()
            if (n.isEmpty() || n == Build.MODEL) "headphones" else n.take(16)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun netLabel(): String {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    var s = "wifi"
                    try {
                        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                        val ls = wm.connectionInfo.linkSpeed
                        if (ls > 0) s += " ${ls}M"
                    } catch (_: Exception) {
                    }
                    s
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "lte"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
                else -> "net"
            }
        } catch (_: Exception) {
            "net"
        }
    }

    private fun updateStatus() {
        clock.text = SimpleDateFormat("EEEE h:mm a", Locale.getDefault())
            .format(Date()).replace("AM", "am").replace("PM", "pm")

        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (lastT == 0L) {
            lastT = now; lastRx = rx; lastTx = tx
        } else if (now - lastT >= 1000 && rx >= 0 && tx >= 0) {
            val dt = now - lastT
            downBps = ((rx - lastRx) * 1000 / dt).coerceAtLeast(0)
            upBps = ((tx - lastTx) * 1000 / dt).coerceAtLeast(0)
            lastT = now; lastRx = rx; lastTx = tx
        }

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val parts = ArrayList<String>()
        headphones()?.let { parts.add("♪ $it") }
        val net = netLabel()
        parts.add(
            if (net == "offline") "offline"
            else "$net ↓${fmtSpeed(downBps)} ↑${fmtSpeed(upBps)}"
        )
        status.text = parts.joinToString("   ")
        battText.text = "▮ ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
        updateNowPlaying()
    }
}


/** Little animated equalizer bars shown next to the song name. */
class VisualizerView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val phases = floatArrayOf(0f, 1.3f, 2.1f, 0.7f)
    var color = Color.WHITE
    var playing = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        val n = 4
        val bw = width / (n * 2f - 1f)
        val t = SystemClock.uptimeMillis() / 1000f
        for (i in 0 until n) {
            val h = if (playing) 0.25f + 0.75f * (0.5f + 0.5f * sin(t * (5f + i * 1.7f) + phases[i])) else 0.2f
            val bh = h * height
            val left = i * 2 * bw
            canvas.drawRoundRect(left, height - bh, left + bw, height.toFloat(), bw / 2, bw / 2, paint)
        }
        if (playing) postInvalidateOnAnimation()
    }
}

/** Vertical slider used for brightness and volume. */
class VSlider(
    c: Context,
    private val fillColor: Int,
    private val trackColor: Int,
    private val onChange: (Float) -> Unit
) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var value = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = trackColor
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.save()
        canvas.clipRect(0f, height * (1f - value), width.toFloat(), height.toFloat())
        paint.color = fillColor
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                value = 1f - e.y / height
                onChange(value)
            }
        }
        return true
    }
}

/** Empty listener: enabling its access lets the launcher read what music is playing. */
class NowPlayingService : NotificationListenerService()
