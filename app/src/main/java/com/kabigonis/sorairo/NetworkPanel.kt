package com.kabigonis.sorairo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

// Omarchy-style network panel: live stats plus a DNS provider switcher (Private DNS).

/** Round-trip time of a TCP connect in ms, or -1 on failure. Run off the main thread. */
internal fun tcpPingMs(host: String, port: Int, timeoutMs: Int): Long {
    val start = SystemClock.elapsedRealtime()
    return try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        SystemClock.elapsedRealtime() - start
    } catch (_: Exception) {
        -1L
    }
}

internal fun MainActivity.hasSecureSettings(): Boolean = canSetBatterySaver()

/** Writes Android's Private DNS setting. Needs the optional WRITE_SECURE_SETTINGS grant. */
internal fun MainActivity.setPrivateDns(provider: Int, customHost: String?): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasSecureSettings()) return false
    return try {
        val host = when (provider) {
            1 -> DNS_CLOUDFLARE
            2 -> DNS_GOOGLE
            3 -> customHost?.trim()?.takeIf { it.isNotEmpty() }
            0 -> null
            else -> return false
        }
        if (provider != 0 && host.isNullOrBlank()) return false
        if (provider == 0) {
            Settings.Global.putString(contentResolver, "private_dns_specifier", null)
            Settings.Global.putString(contentResolver, "private_dns_mode", "opportunistic")
        } else {
            Settings.Global.putString(contentResolver, "private_dns_specifier", host)
            Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
        }
        val mode = Settings.Global.getString(contentResolver, "private_dns_mode")
        val actualHost = Settings.Global.getString(contentResolver, "private_dns_specifier")
        if (provider == 0) mode == "opportunistic" && actualHost.isNullOrBlank()
        else mode == "hostname" && actualHost.equals(host, ignoreCase = true)
    } catch (_: Exception) { false }
}

internal fun MainActivity.currentDnsProvider(): Int = try {
    dnsProviderFor(
        Settings.Global.getString(contentResolver, "private_dns_mode"),
        Settings.Global.getString(contentResolver, "private_dns_specifier")
    )
} catch (_: Exception) {
    0
}

internal fun MainActivity.askCustomDns(onSaved: (String) -> Unit) {
    val p = pal
    var dialog: AlertDialog? = null
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(14))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }
    root.addView(txt(15f, p.cBlue, "~/custom-dns"))
    root.addView(txt(11f, p.cDim, "hostname only, e.g. dns.adguard-dns.com").apply { setPadding(0, dp(6), 0, dp(10)) })
    val input = EditText(this).apply {
        hint = "dns.example.com"
        setText(prefs.getString("dns_custom", "") ?: "")
        setSelection(text.length)
        textSize = 14f * uiScale * (uiTextSize / 13f)
        setTextColor(p.cFg)
        setHintTextColor(p.cDim)
        typeface = font
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        imeOptions = EditorInfo.IME_ACTION_DONE
        background = box(Color.TRANSPARENT, p.cDim)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }
    root.addView(input, LinearLayout.LayoutParams(-1, -2))
    val error = txt(10f, p.cAccent, "Enter a hostname like dns.example.com").apply {
        setPadding(0, dp(6), 0, 0)
        visibility = View.GONE
    }
    root.addView(error)

    fun save() {
        val host = normalizeDnsHost(input.text.toString())
        if (host == null) {
            error.visibility = View.VISIBLE
            return
        }
        prefs.edit().putString("dns_custom", host).apply()
        dialog?.dismiss()
        onSaved(host)
    }
    input.setOnEditorActionListener { _, _, _ ->
        save()
        true
    }
    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(10), 0, 0)
    }
    actions.addView(txt(13f, p.cDim, "CANCEL").apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { dialog?.dismiss() }
    })
    actions.addView(txt(13f, p.cAccent, "SAVE").apply {
        setPadding(dp(14), dp(8), dp(4), dp(8))
        setOnClickListener { save() }
    })
    root.addView(actions)

    dialog = AlertDialog.Builder(this).create().apply {
        setView(root)
        show()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(40), dp(440)), -2)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
    input.requestFocus()
    root.alpha = 0f
    root.translationY = -dp(8).toFloat()
    root.animate().alpha(1f).translationY(0f).setDuration(170).start()
}

internal fun MainActivity.showNetworkPanel() {
    val p = pal
    var dialog: AlertDialog? = null
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(16))
        background = box(p.cBg or (0xFF shl 24), p.cBlue)
    }

    // header
    val icon = WifiIconView(this, p.cFg)
    val title = txt(17f, p.cFg, "Wi-Fi").apply { setTypeface(typeface, Typeface.BOLD) }
    val subtitle = txt(10f, p.cDim, "")
    val head = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    head.addView(icon, LinearLayout.LayoutParams(dp(32), dp(28)).apply { marginEnd = dp(14) })
    val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    titles.addView(title)
    titles.addView(subtitle)
    head.addView(titles)
    root.addView(head)
    root.addView(View(this).apply { setBackgroundColor(p.cDim and 0x00FFFFFF or (0x44 shl 24)) },
        LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(12) })

    // stats, two columns
    fun statRow(label: String, col: LinearLayout): TextView {
        val value = txt(12f, p.cFg, "--").apply { gravity = Gravity.END }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
            addView(txt(12f, p.cDim, label), LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
        })
        return value
    }
    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val pingV = statRow("Ping", left)
    val rxV = statRow("Receiving", left)
    val downV = statRow("Downloaded", left)
    val ipV = statRow("IP Address", left)
    val lossV = statRow("Packet Loss", right)
    val txV = statRow("Sending", right)
    val upV = statRow("Uploaded", right)
    val gwV = statRow("Gateway", right)
    root.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) })
        addView(right, LinearLayout.LayoutParams(0, -2, 1f))
    })
    root.addView(View(this).apply { setBackgroundColor(p.cDim and 0x00FFFFFF or (0x44 shl 24)) },
        LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })

    // DNS provider (max 4: DHCP, Cloudflare, Google, Custom)
    root.addView(txt(10f, p.cDim, "DNS PROVIDER"))
    val dnsButtons = ArrayList<ChoiceButton>()
    val names = listOf("DHCP", "Cloudflare", "Google", "Custom")
    // colours cross-fade when the active provider changes; unchanged buttons are left alone
    fun styleDns(active: Int) {
        for ((i, b) in dnsButtons.withIndex()) b.set(i == active)
    }
    // The custom-host dialog takes window focus, which closes the panel; bring it back afterwards.
    fun reopenAfterCustom() {
        handler.post { if (dialog?.isShowing != true) showNetworkPanel() }
    }
    fun chooseDns(i: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Private DNS requires Android 9 or newer.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasSecureSettings()) {
            showSecureGrantHelp()
            return
        }
        val custom = prefs.getString("dns_custom", "") ?: ""
        if (i == 3 && custom.isBlank()) {
            askCustomDns { host ->
                if (!setPrivateDns(3, host)) {
                    Toast.makeText(this, "Android didn't apply the custom DNS host.", Toast.LENGTH_SHORT).show()
                }
                reopenAfterCustom()
            }
            return
        }
        if (setPrivateDns(i, custom)) {
            // read it back so the highlight shows what Android really holds; stay quiet unless it didn't stick
            val now = currentDnsProvider()
            styleDns(now)
            if (now != i) Toast.makeText(this, "Android didn't keep that DNS setting", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Couldn't change DNS", Toast.LENGTH_SHORT).show()
        }
    }
    val dnsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    for ((i, n) in names.withIndex()) {
        val cb = choiceButton(n)
        val b = cb.view.apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(2), dp(10), dp(2), dp(10))
            setOnClickListener { chooseDns(i) }
            if (i == 3) setOnLongClickListener {
                askCustomDns { host ->
                    if (!hasSecureSettings() || !setPrivateDns(3, host)) {
                        Toast.makeText(this, "Android didn't apply the custom DNS host.", Toast.LENGTH_SHORT).show()
                    }
                    reopenAfterCustom()
                }
                true
            }
        }
        dnsButtons.add(cb)
        dnsRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < 3) marginEnd = dp(6) })
    }
    root.addView(dnsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    val hint = txt(10f, p.cDim, "").apply { setPadding(0, dp(8), 0, 0) }
    root.addView(hint)

    // DNS app (DNSNet and friends run as a VPN, so Sorairo can open them but not switch them)
    val appRow = txt(12f, p.cAccent, "").apply {
        setPadding(0, dp(10), 0, 0)
        setOnClickListener {
            dialog?.dismiss()
            launchVpn()
        }
        setOnLongClickListener {
            dialog?.dismiss()
            showVpnPicker()
            true
        }
    }
    root.addView(appRow)

    // footer: the full Wi-Fi panel (scan and connect)
    root.addView(txt(11f, p.cAccent, "Wi-Fi networks ›").apply {
        setPadding(0, dp(10), 0, 0)
        setOnClickListener {
            dialog?.dismiss()
            showWifiPanel()
        }
    })

    // ping probe, only while the panel is open
    val results = ArrayList<Long>()
    val running = AtomicBoolean(true)
    val pingThread = Thread({
        while (running.get()) {
            val ms = tcpPingMs("1.1.1.1", 53, 1500)
            synchronized(results) {
                results.add(ms)
                while (results.size > 10) results.removeAt(0)
            }
            try {
                Thread.sleep(2000)
            } catch (_: InterruptedException) {
                break
            }
        }
    }, "Sorairo-Network-Ping").apply { isDaemon = true; start() }

    fun render() {
        val st = netState()
        icon.set(st.connected, st.arcs)
        title.text = st.ssid ?: if (st.onWifi) "Wi-Fi" else if (st.connected) "Network" else "Not connected"
        subtitle.text = when {
            !st.connected -> "NO CARRIER"
            downBps + upBps > 2048L -> "HANDLING PACKETS"
            else -> "WIRING BITS"
        }
        rxV.text = formatRate(downBps)
        txV.text = formatRate(upBps)
        downV.text = formatBytes(TrafficStats.getTotalRxBytes())
        upV.text = formatBytes(TrafficStats.getTotalTxBytes())
        val snapshot = synchronized(results) { ArrayList(results) }
        val (avg, loss) = pingStats(snapshot)
        pingV.text = if (st.connected && avg != null) "${avg} ms" else "--"
        lossV.text = if (st.connected && loss != null) "$loss%" else "--"
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // with a VPN active, the active network is the tunnel: show the real address and gateway
            val net = (if (st.vpn) underlyingNetwork(cm) else null) ?: cm.activeNetwork
            val lp = net?.let { cm.getLinkProperties(it) }
            ipV.text = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress ?: "--"
            val gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway
                ?: lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway
            gwV.text = gw?.hostAddress ?: "--"
        } catch (_: Exception) {
            ipV.text = "--"
            gwV.text = "--"
        }
        styleDns(currentDnsProvider())
        hint.text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> "Private DNS requires Android 9 or newer."
            !hasSecureSettings() -> "Switching DNS needs the optional adb grant. Tap a provider for steps."
            st.vpn -> "A VPN is on, so your DNS app handles DNS instead of these buttons."
            else -> ""
        }
        val dnsApp = allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }
        appRow.text = when {
            dnsApp == null -> "DNS app: pick yours ›"
            st.vpn -> "DNS app: ${displayAppLabel(dnsApp)} (running) ›"
            else -> "DNS app: ${displayAppLabel(dnsApp)} ›"
        }
        hint.visibility = if (hint.text.isEmpty()) View.GONE else View.VISIBLE
    }

    render()
    val tick = object : Runnable {
        override fun run() {
            if (dialog?.isShowing == true) {
                render()
                handler.postDelayed(this, 1000)
            }
        }
    }
    dialog = showBarDialog(BarKind.WIFI, root, 460) {
        running.set(false)
        pingThread.interrupt()
        handler.removeCallbacks(tick)
    }
    handler.postDelayed(tick, 1000)
}
