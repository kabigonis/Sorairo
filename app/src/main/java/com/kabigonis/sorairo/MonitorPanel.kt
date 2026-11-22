package com.kabigonis.sorairo

import android.app.ActivityManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

// System monitor, fastfetch panel and device information helpers.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

// ---- fastfetch block (runs by default on every workspace) ----
internal fun MainActivity.buildFetch(ws: Int): View {
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
    val info = txt(11.5f, termFg()).apply {
        setLineSpacing(0f, 1.08f)
        includeFontPadding = false
        text = fetchText()
    }
    fetchInfos[ws] = info
    r.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
    v.addView(r, LinearLayout.LayoutParams(-1, -2))
    return v
}

internal fun MainActivity.logoDrawable(): Drawable? {
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

internal fun MainActivity.logoView(sizeDp: Int = 112): View? {
    val p = pal
    val name = logoNames[prefs.getInt("logo", 0).coerceIn(0, logoNames.size - 1)]
    if (name == "none") return null
    if (name == "image") {
        val d = logoDrawable()
        if (d != null) {
            return ImageView(this).apply {
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.FIT_CENTER
                // Keep the original fastfetch custom-image size; only vector logos use the compact size.
                layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply { marginEnd = dp(18) }
            }
        }
    }
    if (name == "omarchy") {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.cAccent
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.SQUARE
                strokeJoin = Paint.Join.MITER
            }
            private val path = Path()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val side = minOf(width, height).toFloat()
                val inset = dp(5).toFloat()
                val scale = (side - inset * 2) / 100f
                canvas.save()
                canvas.translate((width - side) / 2f + inset, (height - side) / 2f + inset)
                canvas.scale(scale, scale)
                path.reset()
                path.moveTo(8f, 8f); path.lineTo(92f, 8f); path.lineTo(92f, 92f); path.lineTo(66f, 92f)
                path.moveTo(8f, 8f); path.lineTo(8f, 92f); path.lineTo(50f, 92f); path.lineTo(50f, 76f)
                path.lineTo(24f, 76f); path.lineTo(24f, 24f); path.lineTo(76f, 24f); path.lineTo(76f, 8f)
                path.moveTo(76f, 24f); path.lineTo(76f, 76f); path.lineTo(50f, 76f)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(16) }
        }
    }
    val art = if (name == "arch") archArt else androidArt
    return TextView(this).apply {
        text = art
        typeface = Typeface.MONOSPACE
        textSize = 8f
        includeFontPadding = false
        setTextColor(if (name == "arch") p.cBlue else p.cGreen)
        layoutParams = LinearLayout.LayoutParams(dp(112), dp(112)).apply { marginEnd = dp(16) }
    }
}

internal fun MainActivity.updateMonitorSummary(ws: Int) {
    val view = monitorSummaries[ws] ?: return
    val p = pal
    val mi = ActivityManager.MemoryInfo()
    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)

    fun line(label: String, used: Long, total: Long): SpannableStringBuilder {
        val safeTotal = total.coerceAtLeast(1L)
        val pct = ((used.coerceAtLeast(0L) * 100L) / safeTotal).toInt().coerceIn(0, 100)
        val color = when {
            pct > 90 -> Color.RED
            pct > 75 -> Color.rgb(255, 191, 0)
            else -> p.cGreen
        }
        val gb = 1024.0 * 1024 * 1024
        return SpannableStringBuilder().apply {
            append(label.padEnd(5))
            val start = length
            append(bar(pct))
            setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" %.1f/%.1fG %3d%%".format(used.coerceAtLeast(0L) / gb, safeTotal / gb, pct))
        }
    }

    val diskStats = StatFs(Environment.getDataDirectory().path)
    val diskTotal = diskStats.blockCountLong * diskStats.blockSizeLong
    val diskFree = diskStats.availableBlocksLong * diskStats.blockSizeLong
    val diskUsed = (diskTotal - diskFree).coerceAtLeast(0L)

    val sd = sdPath()
    val sdStats = try {
        sd?.let { StatFs(it) }
    } catch (_: Exception) {
        null
    }
    val sdTotal = sdStats?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
    val sdFree = sdStats?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L

    val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

    val text = SpannableStringBuilder()
    text.append(line("RAM", mi.totalMem - mi.availMem, mi.totalMem)).append("\n")
    text.append(line("Disk", diskUsed, diskTotal)).append("\n")
    if (sdStats == null) {
        text.append("SD   not inserted").append("\n")
    } else {
        text.append(line("SD", sdTotal - sdFree, sdTotal)).append("\n")
    }
    text.append(line("Batt", battery.toLong(), 100L)).append("\n")
    text.append(
        "down ${fmtSpeed(sharedNetHistory.lastDown.toLong())}  " +
            "up ${fmtSpeed(sharedNetHistory.lastUp.toLong())}  " +
            "peak ${fmtSpeed(sharedNetHistory.peak.toLong())}"
    )
    view.text = text
}

internal fun MainActivity.fetchText(): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    fetchInto(sb)
    return sb
}

internal fun MainActivity.refreshFetch(ws: Int) {
    fetchInfos[ws]?.text = fetchText()
}

internal fun MainActivity.disk(path: String): String = try {
    val s = StatFs(path)
    val total = s.blockCountLong * s.blockSizeLong
    val free = s.availableBlocksLong * s.blockSizeLong
    val gb = 1024.0 * 1024 * 1024
    "%.1f / %.1f GiB (%d%%)".format((total - free) / gb, total / gb, ((total - free) * 100 / total).toInt())
} catch (_: Exception) {
    "n/a"
}

internal fun MainActivity.sdPath(): String? {
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

internal fun MainActivity.battTemp(): Float? {
    val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    return if (t > 0) t / 10f else null
}

internal fun MainActivity.cpuTemp(): Float? {
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

internal fun MainActivity.appendPaletteRow(sb: SpannableStringBuilder, colors: List<Int>) {
    colors.forEach { color ->
        val start = sb.length
        sb.append(" ")
        sb.setSpan(object : ReplacementSpan() {
            private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            override fun getSize(
                paint: Paint, text: CharSequence, start: Int, end: Int,
                fm: android.graphics.Paint.FontMetricsInt?
            ): Int = dp(12)
            override fun draw(
                canvas: Canvas, text: CharSequence, start: Int, end: Int,
                x: Float, top: Int, y: Int, bottom: Int, paint: Paint
            ) {
                val left = x + dp(1).toFloat()
                val topY = y.toFloat() - dp(9).toFloat()
                canvas.drawRect(left, topY, left + dp(10).toFloat(),
                    topY + dp(5).toFloat(), swatchPaint)
            }
        }, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

internal fun MainActivity.fetchInto(sb: SpannableStringBuilder) {
    val p = pal
    val fg = termFg()
    fun kv(k: String, v: String) {
        sb.add(k.padEnd(8), p.cBlue).add(v + "\n", fg)
    }

    val mi = ActivityManager.MemoryInfo()
    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
    val gb = 1024.0 * 1024 * 1024
    val dm = resources.displayMetrics
    val mins = SystemClock.elapsedRealtime() / 60000
    val bt = battTemp()
    val ct = cpuTemp()
    val sd = sdPath()

    sb.add("▣ Hardware ───────────\n", p.cDim)
    kv("PC", "${Build.MANUFACTURER} ${Build.MODEL}")
    kv("SoC", Build.HARDWARE)
    kv("RAM", "%.1f / %.1f GiB".format((mi.totalMem - mi.availMem) / gb, mi.totalMem / gb))
    kv("Screen", "${dm.widthPixels}x${dm.heightPixels}")
    kv(
        "Temp",
        (if (bt != null) "batt %.1f°C".format(bt) else "batt n/a") + "  " +
            (if (ct != null) "cpu %.0f°C".format(ct) else "cpu n/a")
    )
    sb.add("\n▤ Storage ────────────\n", p.cDim)
    kv("Disk", disk(Environment.getDataDirectory().path))
    kv("SD", if (sd != null) disk(sd) else "not inserted")
    sb.add("\n⚙ Software ───────────\n", p.cDim)
    kv("OS", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    kv("Kernel", System.getProperty("os.version") ?: "?")
    kv("Shell", "Sorairo")
    kv("Apps", "${apps.size} (${allApps.size - apps.size} hidden)")
    kv("Theme", pal.name)
    sb.append("        ")
    appendPaletteRow(sb, listOf(
        p.cAnsi[1], p.cAnsi[2], p.cAnsi[3],
        p.cAnsi[4], p.cAnsi[5], p.cAnsi[6]
    ))
    sb.append("\n")
    kv("Up", "${mins / 60}h ${mins % 60}m")
    sb.add("\ntype help for commands\n", p.cDim)
}

internal fun MainActivity.fmtSpeed(b: Long): String = when {
    b >= 1024 * 1024 -> "%.1f MB/s".format(b / 1048576.0)
    b >= 1024 -> "${b / 1024} KB/s"
    else -> "$b B/s"
}

internal fun MainActivity.headphones(): String? {
    return try {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
internal fun MainActivity.netLabel(): String {
    return try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                var s = "wifi"
                try {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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

internal fun MainActivity.sampleMonitorNetwork(): Pair<Float, Float>? {
    val now = SystemClock.elapsedRealtime()
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()

    if (rx < 0L || tx < 0L) {
        monitorLastT = 0L
        return null
    }

    if (monitorLastT == 0L || rx < monitorLastRx || tx < monitorLastTx) {
        monitorLastT = now
        monitorLastRx = rx
        monitorLastTx = tx
        return null
    }

    val elapsed = now - monitorLastT
    if (elapsed <= 0L) return null

    val down = ((rx - monitorLastRx) * 1000f / elapsed).coerceAtLeast(0f)
    val up = ((tx - monitorLastTx) * 1000f / elapsed).coerceAtLeast(0f)
    monitorLastT = now
    monitorLastRx = rx
    monitorLastTx = tx
    return down to up
}
