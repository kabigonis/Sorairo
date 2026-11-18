package com.kabigonis.sorairo

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.CalendarContract
import android.text.TextUtils
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** Live front-camera preview (mirrored, centre-cropped, clipped to a circle). */
@SuppressLint("MissingPermission")
class FrontCameraView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var thread: HandlerThread? = null
    private var bg: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var size: Size? = null
    private var sensor = 0
    private var closed = true

    init {
        surfaceTextureListener = this
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setOval(0, 0, v.width, v.height)
            }
        }
        clipToOutline = true
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = open(st)
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = applyTransform()
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        close()
        return true
    }

    private fun open(st: SurfaceTexture) {
        try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = mgr.cameraIdList.firstOrNull {
                mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: return
            val ch = mgr.getCameraCharacteristics(id)
            sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java) ?: return
            val s = sizes.filter { it.width <= 1280 && it.height <= 960 }
                .maxByOrNull { it.width * it.height } ?: sizes.minByOrNull { it.width * it.height } ?: return
            size = s
            st.setDefaultBufferSize(s.width, s.height)
            applyTransform()
            closed = false
            val t = HandlerThread("sorairo-cam").also { it.start() }
            thread = t
            val h = Handler(t.looper)
            bg = h
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    if (closed) { cam.close(); return }
                    device = cam
                    try {
                        val surface = Surface(st)
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        @Suppress("DEPRECATION")
                        cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (closed) { s.close(); return }
                                session = s
                                try { s.setRepeatingRequest(req.build(), null, h) } catch (_: Exception) {}
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {}
                        }, h)
                    } catch (_: Exception) {
                    }
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close() }
                override fun onError(cam: CameraDevice, error: Int) { cam.close() }
            }, h)
        } catch (_: Exception) {
        }
    }

    fun close() {
        closed = true
        try { session?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        session = null
        device = null
        thread?.quitSafely()
        thread = null
        bg = null
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }

    /** Centre-crop to the square view, correct for display rotation, and mirror like a selfie camera. */
    private fun applyTransform() {
        val s = size ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw == 0f || vh == 0f) return
        val rot = display?.rotation ?: Surface.ROTATION_0
        val cx = vw / 2f
        val cy = vh / 2f
        val m = Matrix()
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            val viewRect = RectF(0f, 0f, vw, vh)
            val buf = RectF(0f, 0f, s.height.toFloat(), s.width.toFloat())
            buf.offset(cx - buf.centerX(), cy - buf.centerY())
            m.setRectToRect(viewRect, buf, Matrix.ScaleToFit.FILL)
            val sc = maxOf(vh / s.height, vw / s.width)
            m.postScale(sc, sc, cx, cy)
            m.postRotate(90f * (rot - 2), cx, cy)
        } else {
            val swapped = sensor == 90 || sensor == 270
            val uw = (if (swapped) s.height else s.width).toFloat()
            val uh = (if (swapped) s.width else s.height).toFloat()
            val ar = uw / uh
            if (ar < 1f) m.postScale(1f, 1f / ar, cx, cy) else m.postScale(ar, 1f, cx, cy)
            if (rot == Surface.ROTATION_180) m.postRotate(180f, cx, cy)
        }
        m.postScale(-1f, 1f, cx, cy)
        setTransform(m)
    }
}

/**
 * A "dynamic island" in the centre of the top bar.
 *
 * Idle: just the clock. A pill grows out of it for short peeks (headphones, charger,
 * track change) and while music plays. Tap it for the expanded card:
 *   [ album art + player ] [ week strip + agenda ] [ front-camera mirror ]
 */
class IslandView(
    context: Context,
    private val bg: Int,
    private val fg: Int,
    private val dim: Int,
    private val accent: Int,
    private val green: Int,
    private val font: Typeface?,
    private val host: Host,
    private val maxPillWidth: Int
) : FrameLayout(context) {

    interface Host {
        fun controller(): MediaController?
        fun hasPermission(permission: String): Boolean
        /** Ask for a runtime permission; the island re-opens its card when the activity resumes. */
        fun requestPermission(permission: String)
        fun cameraEnabled(): Boolean
        fun openCalendar()
    }

    private companion object {
        const val IDLE = 0
        const val PEEK = 1
        const val MEDIA = 2
        const val CAL = android.Manifest.permission.READ_CALENDAR
        const val CAM = android.Manifest.permission.CAMERA
    }

    private class Ev(val title: String, val begin: Long, val allDay: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private fun tv(size: Float, color: Int, s: CharSequence = ""): TextView = TextView(context).apply {
        textSize = size
        setTextColor(color)
        text = s
        if (font != null) typeface = font
    }

    // ---- pill ----
    private val pill = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
    }
    private val glyph = tv(13f, accent)
    private val vis = VisualizerView(context)
    private val label = tv(13f, fg).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = (maxPillWidth - dp(96)).coerceAtLeast(dp(80))
    }
    private val clockShort = tv(12f, dim)

    // ---- state ----
    private var title: String? = null
    private var artist: String? = null
    private var playing = false
    private var lastTitle: String? = null
    private var peekText: String? = null
    private var peekGlyph = ""
    private var peekUntil = 0L
    private var phonesInit = false
    private var lastPhones: String? = null
    private var chargingInit = false
    private var lastCharging = false
    private var lastKind = -1
    private var reopen = false

    // ---- calendar state ----
    private var events: Map<LocalDate, List<Ev>> = emptyMap()
    private var calVersion = 0
    private var selectedDay: LocalDate? = null
    private var tick = 0

    // ---- expanded card ----
    private var popup: PopupWindow? = null
    private var mediaCol: LinearLayout? = null
    private var calCol: LinearLayout? = null
    private var camSlot: FrameLayout? = null
    private var lastSig = ""
    private var lastCalSig = ""

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        pill.addView(glyph, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        pill.addView(vis, LinearLayout.LayoutParams(dp(14), dp(12)).apply { marginEnd = dp(8) })
        pill.addView(label, LinearLayout.LayoutParams(-2, -2))
        pill.addView(clockShort, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        pill.setOnClickListener { toggleExpanded() }
        addView(pill, LayoutParams(-2, -2, Gravity.CENTER))
        vis.color = green
    }

    // ---------------- host hooks ----------------

    fun onHostResume() {
        handler.removeCallbacks(ticker)
        ticker.run()
        if (reopen) {
            reopen = false
            post { if (isAttachedToWindow && popup == null) showExpanded() }
        }
    }

    fun onHostPause() {
        handler.removeCallbacks(ticker)
        popup?.dismiss()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        popup?.dismiss()
        super.onDetachedFromWindow()
    }

    fun setMedia(newTitle: String?, newArtist: String?, isPlaying: Boolean) {
        val changed = newTitle != null && lastTitle != null && newTitle != lastTitle
        title = newTitle
        artist = newArtist
        playing = isPlaying
        if (newTitle != null) {
            if (changed && isPlaying) {
                peek("\u266A", if (newArtist.isNullOrEmpty()) newTitle else "$newTitle \u2014 $newArtist")
                lastTitle = newTitle
                return
            }
            lastTitle = newTitle
        }
        refresh()
    }

    /** Fed by the launcher's existing 2 s status loop. */
    fun onSystem(phones: String?, charging: Boolean, pct: Int) {
        if (phonesInit && phones != lastPhones) {
            if (phones != null) peek("\u266A", "$phones connected") else peek("\u266A", "headphones off")
        }
        lastPhones = phones
        phonesInit = true
        if (chargingInit && charging != lastCharging) {
            peek("\u26A1", if (charging) "charging $pct%" else "unplugged $pct%")
        }
        lastCharging = charging
        chargingInit = true
    }

    private fun peek(g: String, text: String, ms: Long = 3500L) {
        peekGlyph = g
        peekText = text
        peekUntil = System.currentTimeMillis() + ms
        refresh()
    }

    // ---------------- pill rendering ----------------

    private fun kind(): Int = when {
        peekText != null && System.currentTimeMillis() < peekUntil -> PEEK
        title != null && playing -> MEDIA
        else -> IDLE
    }

    fun refresh() {
        val now = Date()
        val longClock = SimpleDateFormat("EEEE h:mm a", Locale.getDefault()).format(now)
            .replace("AM", "am").replace("PM", "pm")
        val shortClock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
            .replace("AM", "am").replace("PM", "pm")

        if (peekText != null && System.currentTimeMillis() >= peekUntil) peekText = null

        val k = kind()
        if (k != lastKind) {
            lastKind = k
            val bordered = k != IDLE
            pill.background = if (bordered) GradientDrawable().apply {
                setColor(Color.argb(240, Color.red(bg), Color.green(bg), Color.blue(bg)))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (k == MEDIA) green else dim)
            } else null
            val ph = if (bordered) dp(10) else 0
            val pv = if (bordered) dp(3) else 0
            pill.setPadding(ph, pv, ph, pv)
            glyph.visibility = if (k == PEEK) View.VISIBLE else View.GONE
            vis.visibility = if (k == MEDIA) View.VISIBLE else View.GONE
            clockShort.visibility = if (bordered) View.VISIBLE else View.GONE
            if (bordered) {
                pill.post {
                    pill.pivotX = pill.width / 2f
                    pill.pivotY = pill.height / 2f
                    pill.scaleX = 0.8f
                    pill.scaleY = 0.8f
                    pill.animate().scaleX(1f).scaleY(1f).setDuration(280)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
            } else {
                pill.scaleX = 1f
                pill.scaleY = 1f
            }
        }

        when (k) {
            IDLE -> {
                label.text = longClock
                label.setTextColor(fg)
            }
            PEEK -> {
                glyph.text = peekGlyph
                glyph.setTextColor(accent)
                label.text = peekText
                label.setTextColor(fg)
                clockShort.text = shortClock
            }
            MEDIA -> {
                val t = title.orEmpty()
                label.text = if (artist.isNullOrEmpty()) t else "$t \u2014 $artist"
                label.setTextColor(fg)
                vis.playing = playing
                clockShort.text = shortClock
            }
        }

        if (popup?.isShowing == true) {
            // refresh the agenda once a minute (and at midnight)
            if (++tick % 60 == 0) loadEvents()
            populate(false)
        }
    }

    // ---------------- expanded card ----------------

    private fun toggleExpanded() {
        val p = popup
        if (p != null && p.isShowing) { p.dismiss(); return }
        showExpanded()
    }

    private fun roundOutline(radius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(v: View, o: Outline) {
            o.setRoundRect(0, 0, v.width, v.height, radius)
        }
    }

    private fun showExpanded() {
        val screenW = resources.displayMetrics.widthPixels
        val wide = screenW >= dp(560)
        val w = minOf(if (wide) dp(620) else dp(360), screenW - dp(24))

        val root = LinearLayout(context).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), dim)
            }
        }
        val media = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val cal = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        mediaCol = media
        calCol = cal

        if (wide) {
            root.addView(media, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(vDivider(), LinearLayout.LayoutParams(dp(1), dp(90)).apply {
                marginStart = dp(12); marginEnd = dp(12)
            })
            root.addView(cal, LinearLayout.LayoutParams(0, -2, 1.5f))
        } else {
            root.addView(media, LinearLayout.LayoutParams(-1, -2))
            root.addView(hDivider(), LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(10); bottomMargin = dp(10)
            })
            root.addView(cal, LinearLayout.LayoutParams(-1, -2))
        }

        if (host.cameraEnabled()) {
            val slot = FrameLayout(context)
            camSlot = slot
            buildCamera(slot)
            if (wide) {
                root.addView(slot, LinearLayout.LayoutParams(dp(92), dp(92)).apply { marginStart = dp(14) })
            } else {
                root.addView(slot, LinearLayout.LayoutParams(dp(92), dp(92)).apply {
                    topMargin = dp(12); gravity = Gravity.CENTER_HORIZONTAL
                })
            }
        } else camSlot = null

        lastSig = ""
        lastCalSig = ""
        tick = 0
        selectedDay = LocalDate.now()
        populate(true)
        loadEvents()

        val pw = PopupWindow(root, w, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.isOutsideTouchable = true
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.setOnDismissListener {
            popup = null
            mediaCol = null
            calCol = null
            camSlot = null
        }
        popup = pw
        pw.showAsDropDown(this, (width - w) / 2, dp(4))
    }

    private fun buildCamera(slot: FrameLayout) {
        slot.removeAllViews()
        if (host.hasPermission(CAM)) {
            slot.addView(FrontCameraView(context), FrameLayout.LayoutParams(-1, -1))
        } else {
            val ph = tv(11f, dim, "tap to\nenable\ncamera").apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(1), dim)
                }
                setOnClickListener {
                    reopen = true
                    host.requestPermission(CAM)
                }
            }
            slot.addView(ph, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun vDivider(): View = View(context).apply { setBackgroundColor(dim); alpha = 0.4f }
    private fun hDivider(): View = vDivider()

    private fun chip(text: String, color: Int = fg, onClick: () -> Unit): TextView =
        tv(13f, color, text).apply {
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                setStroke(dp(1), dim)
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun artBitmap(): Bitmap? {
        val md = host.controller()?.metadata ?: return null
        return md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    private fun populate(force: Boolean) {
        val m = mediaCol ?: return
        val c = calCol ?: return
        val art = artBitmap()
        val sig = "$title|$artist|$playing|${System.identityHashCode(art)}"
        if (force || sig != lastSig) {
            lastSig = sig
            buildMedia(m, art)
        }
        val calSig = "$selectedDay|$calVersion|${LocalDate.now().toEpochDay()}|${host.hasPermission(CAL)}"
        if (force || calSig != lastCalSig) {
            lastCalSig = calSig
            buildCalendar(c)
        }
    }

    // ---------------- media column ----------------

    private fun buildMedia(col: LinearLayout, art: Bitmap?) {
        col.removeAllViews()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val artSize = dp(72)
        val artView: View = if (art != null) {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(art)
            }
        } else {
            tv(26f, dim, "\u266A").apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setStroke(dp(1), dim)
                    cornerRadius = dp(10).toFloat()
                }
            }
        }
        artView.outlineProvider = roundOutline(dp(10).toFloat())
        artView.clipToOutline = true
        row.addView(artView, LinearLayout.LayoutParams(artSize, artSize).apply { marginEnd = dp(12) })

        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val t = title
        if (t == null) {
            info.addView(tv(13f, dim, "nothing playing"))
        } else {
            info.addView(tv(14f, fg, t).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (!artist.isNullOrEmpty()) info.addView(tv(12f, dim, artist.orEmpty()).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            fun add(v: TextView) = controls.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            add(chip("\u23EE") { host.controller()?.transportControls?.skipToPrevious() })
            add(chip(if (playing) "pause" else "play", green) {
                val c = host.controller()
                if (c != null) {
                    if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                    else c.transportControls.play()
                }
            })
            add(chip("\u23ED") { host.controller()?.transportControls?.skipToNext() })
            info.addView(controls)
        }
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    // ---------------- calendar column ----------------

    private fun loadEvents() {
        if (!host.hasPermission(CAL)) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val first = today.minusDays(3)
        val last = today.plusDays(3)
        val startMs = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val appContext = context.applicationContext
        Thread {
            val map = HashMap<LocalDate, MutableList<Ev>>()
            try {
                val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(b, startMs)
                ContentUris.appendId(b, endMs)
                appContext.contentResolver.query(
                    b.build(),
                    arrayOf(
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY
                    ),
                    null, null, CalendarContract.Instances.BEGIN + " ASC"
                )?.use { cur ->
                    while (cur.moveToNext()) {
                        val name = cur.getString(0)?.takeIf { it.isNotBlank() } ?: "(no title)"
                        val begin = cur.getLong(1)
                        val end = cur.getLong(2)
                        val allDay = cur.getInt(3) == 1
                        val d0: LocalDate
                        val d1: LocalDate
                        if (allDay) {
                            // all-day events are stored as UTC midnights, end exclusive
                            d0 = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                            d1 = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                        } else {
                            d0 = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                            d1 = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(zone).toLocalDate()
                        }
                        var d = if (d0.isBefore(first)) first else d0
                        val stop = if (d1.isAfter(last)) last else maxOf(d1, d0)
                        while (!d.isAfter(stop)) {
                            map.getOrPut(d) { ArrayList() }.add(Ev(name, begin, allDay))
                            d = d.plusDays(1)
                        }
                    }
                }
            } catch (_: Exception) {
                // no permission / no calendar provider: leave the agenda empty
            }
            map.values.forEach { l -> l.sortWith(compareBy({ !it.allDay }, { it.begin })) }
            handler.post {
                events = map
                calVersion++
                if (popup?.isShowing == true) populate(false)
            }
        }.start()
    }

    private fun buildCalendar(col: LinearLayout) {
        col.removeAllViews()
        val today = LocalDate.now()
        val sel = selectedDay ?: today
        val loc = Locale.getDefault()

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            tv(20f, fg, sel.month.getDisplayName(TextStyle.SHORT, loc)).apply { setTypeface(typeface, Typeface.BOLD) },
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) }
        )
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in -3..3) {
            val d = today.plusDays(i.toLong())
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
                if (d == sel) background = GradientDrawable().apply {
                    setStroke(dp(1), if (d == today) accent else dim)
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener {
                    selectedDay = d
                    populate(false)
                }
            }
            cell.addView(tv(9f, dim, d.dayOfWeek.getDisplayName(TextStyle.SHORT, loc).take(3)))
            cell.addView(tv(14f, if (d == today) accent else fg, d.dayOfMonth.toString()).apply {
                if (d == today) setTypeface(typeface, Typeface.BOLD)
            })
            val has = events[d]?.isNotEmpty() == true
            cell.addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (has) accent else Color.TRANSPARENT)
                }
            }, LinearLayout.LayoutParams(dp(4), dp(4)).apply { topMargin = dp(2) })
            strip.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        head.addView(strip, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(head, LinearLayout.LayoutParams(-1, -2))

        val agenda = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        if (!host.hasPermission(CAL)) {
            agenda.addView(chip("connect calendar", accent) {
                reopen = true
                host.requestPermission(CAL)
            })
        } else {
            val list = events[sel].orEmpty()
            if (list.isEmpty()) {
                agenda.addView(tv(12f, dim,
                    if (sel == today) "Nothing for today"
                    else "Nothing on " + sel.dayOfWeek.getDisplayName(TextStyle.SHORT, loc) + " " + sel.dayOfMonth))
            } else {
                val fmt = SimpleDateFormat("h:mm a", loc)
                for (e in list.take(3)) {
                    val time = if (e.allDay) "all day" else fmt.format(Date(e.begin)).replace("AM", "am").replace("PM", "pm")
                    agenda.addView(tv(12f, fg, "$time  ${e.title}").apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }
                if (list.size > 3) agenda.addView(tv(11f, dim, "+${list.size - 3} more"))
            }
            agenda.setOnClickListener { host.openCalendar() }
        }
        col.addView(agenda, LinearLayout.LayoutParams(-1, -2))
    }
}
