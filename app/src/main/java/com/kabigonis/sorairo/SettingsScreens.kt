package com.kabigonis.sorairo

import android.Manifest
import android.app.Dialog
import android.app.NotificationManager
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.math.min

// Settings overlay and its screens (style, home screen, permissions, config transfer, hidden apps).
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.pickLogoFromStyle() {
    returnToStyleAfterLogoPick = true
    prefs.edit().putBoolean("pending_logo_return_style", true).apply()
    // Do not leave a fade-out callback pending: it can hide the newly
    // reopened Style overlay after the document picker returns.
    settings.animate().cancel()
    settings.alpha = 1f
    settings.visibility = View.GONE
    pickLogo.launch(arrayOf("image/*"))
}

internal fun MainActivity.refreshDefaultLauncherPrompts() {
    val showPrompt = !isDefaultLauncher()
    defaultLauncherPrompts.forEach { prompt ->
        prompt?.visibility = if (showPrompt) View.VISIBLE else View.GONE
    }
}

internal fun MainActivity.openSettingsOnLongPress(v: View) {
    val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            showSettings()
        }
    })
    v.setOnTouchListener { _, ev ->
        gd.onTouchEvent(ev)
        false
    }
}

// ================= settings =================
internal fun MainActivity.showOverlay() {
    // Cancel any stale fade-out callback before reusing the settings overlay.
    settings.animate().cancel()
    settings.visibility = View.VISIBLE
    settings.alpha = 0f
    settings.animate().alpha(1f).setDuration(140).start()
}

internal fun MainActivity.showSettings() {
    wallpaperPageOpen = false
    cancelThemeWallpaperJobs()
    closeDrawer()
    fillSettings()
    setScroll.scrollTo(0, 0)
    showOverlay()
}

internal fun MainActivity.closeSettings() {
    wallpaperPageOpen = false
    cancelThemeWallpaperJobs()
    wallpaperPageShell?.let { settings.removeView(it) }
    wallpaperPageShell = null
    setScroll.visibility = View.VISIBLE
    val closingSettings = settings
    if (closingSettings.visibility != View.VISIBLE) return
    closingSettings.animate().cancel()
    closingSettings.animate().alpha(0f).setDuration(120)
        .withEndAction {
            // Capture the view being closed; never hide a replacement overlay
            // created by rebuild() while this animation is finishing.
            closingSettings.visibility = View.GONE
        }.start()
}

internal fun MainActivity.cycle(key: String, n: Int, def: Int = 0) {
    val returnToStyle = styleConfigOpen
    prefs.edit().putInt(key, (prefs.getInt(key, def) + 1) % n).apply()
    rebuild(false)
    if (returnToStyle) {
        fillStyleSettings()
        setScroll.scrollTo(0, 0)
        showOverlay()
    } else {
        showSettings()
    }
}

internal fun MainActivity.fillSettings() {
    setSettingsWide(false)
    styleConfigOpen = false
    permissionConfigOpen = false
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/config"))
    setBox.addView(txt(11f, p.cDim, "choose what you want to configure").apply {
        setPadding(0, dp(2), 0, dp(6))
    })
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
    row("dynamic island", if (prefs.getBoolean("dynamic_island", true)) "on" else "off") {
        val enabled = !prefs.getBoolean("dynamic_island", true)
        prefs.edit().putBoolean("dynamic_island", enabled).apply()
        if (!enabled) islandEmptyExpanded = false
        refreshIsland()
        fillSettings()
    }
    row("style", "appearance & layout ›") {
        switchSettingsContent {
            fillStyleSettings()
            setScroll.scrollTo(0, 0)
        }
    }
    row("pinned apps", "workspace ${curWs + 1} ›") {
        switchSettingsContent {
            fillPinnedPicker(curWs)
            setScroll.scrollTo(0, 0)
        }
    }
    row("default launcher", "change ›") {
        closeSettings()
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
    row("perms", "access & system settings ›") {
        switchSettingsContent {
            fillPermissionSettings()
            setScroll.scrollTo(0, 0)
        }
    }
    row("vpn app", allApps.find { it.pkg == prefs.getString("vpn_pkg", null) }?.label ?: "choose ›") {
        switchSettingsContent {
            fillVpnPicker()
            setScroll.scrollTo(0, 0)
        }
    }
    row("import/export config", "backup or restore ›") {
        switchSettingsContent {
            fillConfigTransfer()
            setScroll.scrollTo(0, 0)
        }
    }
    row("hidden apps", "${allApps.size - apps.size} ›") {
        switchSettingsContent {
            fillHidden()
            setScroll.scrollTo(0, 0)
        }
    }
    row("close", "esc") { closeSettings() }
}

internal fun MainActivity.fillConfigTransfer() {
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/config/import-export"))
    setBox.addView(txt(11f, p.cDim, "back up or restore your complete Sorairo configuration").apply {
        setPadding(0, dp(2), 0, dp(6))
    })
    setBox.addView(txt(14f, p.cAccent, "← back").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener {
            switchSettingsContent {
                fillSettings()
                setScroll.scrollTo(0, 0)
            }
        }
    })
    fun action(label: String, detail: String, onTap: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(11), 0, dp(11))
            setOnClickListener { onTap() }
        }
        row.addView(txt(14f, p.cFg, label), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(txt(14f, p.cAccent, detail))
        setBox.addView(row)
    }
    action("export complete config", "export ›") {
        exportConfigFile.launch("sorairo-config.json")
    }
    action("import complete config", "import ›") {
        importConfigFile.launch(arrayOf("application/json", "text/*", "*/*"))
    }
    setBox.addView(txt(11f, p.cDim,
        "Includes themes, layout, pinned and hidden apps, VPN app, wallpaper selection, and other saved preferences. Wallpaper files and app installations themselves are not included."
    ).apply { setPadding(0, dp(8), 0, 0) })
}

internal fun MainActivity.fillPermissionSettings() {
    permissionConfigOpen = true
    styleConfigOpen = false
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/config/permissions"))
    setBox.addView(txt(11f, p.cDim, "access Sorairo uses and manage each permission").apply {
        setPadding(0, dp(2), 0, dp(6))
    })
    setBox.addView(txt(14f, p.cAccent, "← back").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener {
            switchSettingsContent {
                fillSettings()
                setScroll.scrollTo(0, 0)
            }
        }
    })

    fun locationEnabled(): Boolean = if (Build.VERSION.SDK_INT >= 28) {
        (getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager).isLocationEnabled
    } else {
        Settings.Secure.getInt(
            contentResolver, Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF
        ) != Settings.Secure.LOCATION_MODE_OFF
    }

    fun permissionRow(
        title: String,
        detail: String,
        state: String,
        granted: Boolean,
        onTap: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { onTap() }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(txt(14f, p.cFg, title))
        copy.addView(txt(11f, p.cDim, detail).apply {
            setPadding(0, dp(3), dp(8), 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(txt(11f, if (granted) p.cGreen else p.cAccent, state))
        setBox.addView(row)
        setBox.addView(View(this).apply {
            setBackgroundColor(p.cDim and 0x55FFFFFF)
        }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    val homeGranted = isDefaultLauncher()
    permissionRow(
        "Default launcher",
        "Lets Sorairo handle the Home button and display your workspaces.",
        if (homeGranted) "active" else "set up ›",
        homeGranted
    ) {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    val notificationsGranted = mediaAccess()
    permissionRow(
        "Notification access",
        "Used by the media panel to read media-session and playback information.",
        if (notificationsGranted) "granted" else "enable ›",
        notificationsGranted
    ) {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        catch (_: Exception) { openSystem(Settings.ACTION_SETTINGS) }
    }

    val writeGranted = Settings.System.canWrite(this)
    permissionRow(
        "Modify system settings",
        "Allows the brightness control to change the screen brightness directly.",
        if (writeGranted) "granted" else "enable ›",
        writeGranted
    ) {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            openSystem(Settings.ACTION_SETTINGS)
        }
    }

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val dndGranted = notificationManager.isNotificationPolicyAccessGranted
    permissionRow(
        "Do Not Disturb access",
        "Lets Sorairo change the ringer mode. No current panel uses it; kept for future controls.",
        if (dndGranted) "granted" else "enable ›",
        dndGranted
    ) {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        catch (_: Exception) { openSystem(Settings.ACTION_SETTINGS) }
    }

    val secureGranted = canSetBatterySaver()
    permissionRow(
        "Secure settings (advanced)",
        "Optional. Lets the battery panel switch Battery Saver and the network panel switch DNS in one tap. Needs a one-time adb grant.",
        if (secureGranted) "granted" else "adb setup ›",
        secureGranted
    ) {
        if (secureGranted) Toast.makeText(this, "Granted: Power-saver and DNS switch directly", Toast.LENGTH_SHORT).show()
        else showSecureGrantHelp()
    }

    val locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val locationReady = locationGranted && locationEnabled()
    permissionRow(
        "Location for Wi-Fi discovery",
        "Only used when you opt in to scan and display nearby Wi-Fi networks. Not needed to open the Wi-Fi panel.",
        when {
            locationReady -> "ready"
            locationGranted -> "turn on ›"
            else -> "enable ›"
        },
        locationReady
    ) {
        when {
            !locationGranted -> requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 742
            )
            !locationEnabled() -> try {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (_: Exception) {
                openSystem(Settings.ACTION_SETTINGS)
            }
            else -> Toast.makeText(this, "Wi-Fi discovery is ready", Toast.LENGTH_SHORT).show()
        }
    }

    permissionRow(
        "Network & Wi-Fi state",
        "Install-time access used to detect connectivity and read Wi-Fi state. Android grants this automatically.",
        "automatic",
        true
    ) { }

    permissionRow(
        "Audio settings",
        "Install-time access used for supported audio controls. Android grants this automatically.",
        "automatic",
        true
    ) { }

    setBox.addView(txt(
        11f, p.cDim,
        "Sorairo never enables special access silently. Android may ask you to confirm access in its own settings."
    ).apply { setPadding(0, dp(10), 0, dp(4)) })
}

internal fun MainActivity.showThemePicker() {
    val p = pal
    val selected = prefs.getInt("theme", 0).coerceIn(0, pals.lastIndex.coerceAtLeast(0))
    val dialog = Dialog(this)
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(solidColor(), p.cAccent)
        setPadding(dp(16), dp(14), dp(16), dp(12))
    }
    shell.addView(txt(16f, p.cBlue, "~/config/theme"))
    shell.addView(txt(11f, p.cDim, "choose a palette · color preview").apply {
        setPadding(0, dp(2), 0, dp(10))
    })
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(list, FrameLayout.LayoutParams(-1, -2))
    }
    shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    pals.forEachIndexed { index, theme ->
        val active = index == selected
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(7), dp(8), dp(7))
            background = if (active) box(theme.cSel, p.cAccent) else null
            setOnClickListener {
                prefs.edit().putInt("theme", index).apply()
                dialog.dismiss()
                rebuild(false)
                onThemeApplied(index)
                fillStyleSettings()
                setScroll.scrollTo(0, 0)
                showOverlay()
            }
        }
        row.addView(txt(12f,
            if (active) Contrast.readable(p.cAccent, theme.cSel, p.cFg, p.cBg)
            else Contrast.readable(theme.cFg, p.cBg, p.cFg),
            (if (active) "● " else "  ") + theme.name),
            LinearLayout.LayoutParams(0, -2, 1f))
        val swatches = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // Match the compact six-color strip shown by the terminal fetch.
        listOf(1, 2, 3, 4, 5, 6).forEach { colorIndex ->
            swatches.addView(View(this).apply {
                setBackgroundColor(theme.cAnsi[colorIndex])
            }, LinearLayout.LayoutParams(dp(10), dp(5)).apply {
                marginStart = dp(2)
            })
        }
        row.addView(swatches)
        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(3)
        })
    }
    val close = txt(12f, p.cAccent, "CLOSE").apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(4), dp(4))
        setOnClickListener { dialog.dismiss() }
    }
    shell.addView(close)
    dialog.setContentView(shell)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.show()
    dialog.window?.setLayout(
        min(dp(680), resources.displayMetrics.widthPixels - dp(40)),
        min(dp(760), resources.displayMetrics.heightPixels - dp(64))
    )
}

internal fun MainActivity.showSorairoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    val p = pal
    val dialog = Dialog(this)
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(solidColor(), p.cAccent)
        setPadding(dp(18), dp(16), dp(18), dp(12))
    }
    shell.addView(txt(16f, p.cBlue, title))
    shell.addView(txt(13f, p.cFg, message).apply {
        setPadding(0, dp(8), 0, dp(16))
    })
    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    actions.addView(txt(12f, p.cDim, "CANCEL").apply {
        setPadding(dp(12), dp(10), dp(12), dp(8))
        setOnClickListener { dialog.dismiss() }
    })
    actions.addView(txt(12f, p.cAccent, confirmLabel.uppercase()).apply {
        setPadding(dp(12), dp(10), dp(4), dp(8))
        setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
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

internal fun MainActivity.fillStyleSettings() {
    setSettingsWide(false)
    styleConfigOpen = true
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/config/style"))
    setBox.addView(txt(11f, p.cDim, "appearance, wallpaper and workspace layout").apply {
        setPadding(0, dp(2), 0, dp(6))
    })
    setBox.addView(txt(14f, p.cAccent, "← back").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener {
            switchSettingsContent {
                fillSettings()
                setScroll.scrollTo(0, 0)
            }
        }
    })
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
    row("home screen", "customize ›") {
        switchSettingsContent {
            fillHomeScreenSettings()
            setScroll.scrollTo(0, 0)
        }
    }
    row("wallpaper", "manage ›") {
        switchSettingsContent {
            fillWallpaperSettings()
            setScroll.scrollTo(0, 0)
        }
    }
    row("theme", pal.name) { showThemePicker() }
    row("community themes", "browse ›") {
        communityTab = "browse"
        switchSettingsContent {
            fillCommunityThemes()
            setScroll.scrollTo(0, 0)
        }
    }
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
            pickLogoFromStyle()
        } else {
            rebuild(false)
            fillStyleSettings()
            setScroll.scrollTo(0, 0)
            showOverlay()
        }
    }
    row("logo image / gif", "choose ›") { pickLogoFromStyle() }
    row("window layout", layoutModeFor(curWs)) {
        val modes = listOf("dwindle", "master", "columns", "rows")
        setLayoutMode(curWs, modes[(modes.indexOf(layoutModeFor(curWs)) + 1) % modes.size])
        fillStyleSettings()
    }
    row("window ratio", "${(layoutRatioFor(curWs) * 100).toInt()}%") {
        val ratios = listOf(40, 50, 60, 70)
        val current = (layoutRatioFor(curWs) * 100).toInt()
        setLayoutRatio(curWs, ratios[(ratios.indexOf(current).coerceAtLeast(0) + 1) % ratios.size])
        fillStyleSettings()
    }
    row("layout scope", if (layoutScopeAll()) "all workspaces" else "this workspace") {
        val makeAll = !layoutScopeAll()
        val currentMode = layoutModeFor(curWs)
        val currentRatio = (layoutRatioFor(curWs) * 100).toInt()
        val edit = prefs.edit().putBoolean("layout_scope_all", makeAll)
        if (makeAll) {
            edit.putString("window_layout_mode_global", currentMode)
            edit.putInt("window_layout_ratio_global", currentRatio)
        } else {
            for (ws in 0..4) {
                if (!prefs.contains("window_layout_mode_$ws")) edit.putString("window_layout_mode_$ws", currentMode)
                if (!prefs.contains("window_layout_ratio_$ws")) edit.putInt("window_layout_ratio_$ws", currentRatio)
            }
        }
        edit.apply()
        for (ws in 0..4) applyTileLayout(ws)
        fillStyleSettings()
    }
}

internal fun MainActivity.fillHomeScreenSettings() {
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/home-screen"))
    setBox.addView(
        txt(11f, p.cDim, "show or hide workspace windows").apply {
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

    fun refreshVisibility() {
        if (windowVisibilityScopeAll()) {
            for (ws in 0..4) applyTileLayout(ws)
        } else {
            applyTileLayout(curWs)
        }
    }

    fun scopeRow() {
        val allWorkspaces = windowVisibilityScopeAll()
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                val makeAll = !windowVisibilityScopeAll()
                val edit = prefs.edit().putBoolean("home_visibility_scope_all", makeAll)
                if (!makeAll) {
                    // Seed this workspace's window states from the existing shared
                    // values so switching scope never unexpectedly hides a pane.
                    for (id in tileIds) {
                        val key = "home_show_${id}_$curWs"
                        if (!prefs.contains(key)) {
                            edit.putBoolean(key, prefs.getBoolean("home_show_$id", true))
                        }
                    }
                }
                edit.apply()
                refreshVisibility()
                fillHomeScreenSettings()
                setScroll.scrollTo(0, 0)
            }
        }
        r.addView(txt(14f, p.cFg, "visibility scope"), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(txt(14f, p.cAccent, if (allWorkspaces) "all workspaces" else "this window"))
        setBox.addView(r)
    }

    fun windowRow(label: String, id: String) {
        val enabled = windowShown(curWs, id)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                setWindowShown(curWs, id, !windowShown(curWs, id))
                refreshVisibility()
                fillHomeScreenSettings()
                setScroll.scrollTo(0, 0)
            }
        }
        r.addView(txt(14f, p.cFg, label), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(txt(14f, if (enabled) p.cGreen else p.cDim, if (enabled) "shown" else "hidden"))
        setBox.addView(r)
    }

    scopeRow()
    windowRow("terminal window", "terminal")
    windowRow("pinned apps window", "pinned")
    windowRow("bottom window (monitor / player)", "bottom")
    setBox.addView(
        txt(11f, p.cDim,
            if (windowVisibilityScopeAll()) "Changes apply to all five workspaces."
            else "Changes apply only to this window in workspace ${curWs + 1}.").apply {
            setPadding(0, dp(10), 0, dp(2))
        }
    )
}

internal fun MainActivity.fillHidden() {
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
        setOnClickListener {
            switchSettingsContent {
                fillSettings()
                setScroll.scrollTo(0, 0)
            }
        }
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

internal fun MainActivity.ui(block: () -> Unit) {
    if (!isDestroyed) runOnUiThread(block)
}

internal fun MainActivity.switchSettingsContent(update: () -> Unit) {
    setBox.animate().cancel()
    setBox.animate().alpha(0f).setDuration(90).withEndAction {
        update()
        setBox.alpha = 0f
        setBox.animate().alpha(1f).setDuration(140).start()
    }.start()
}

// Match the established Community Themes window dimensions from the reference implementation.
internal fun MainActivity.setSettingsWide(wide: Boolean) {
    if (!setScrollInitialized) return
    val lp = setScroll.layoutParams as? FrameLayout.LayoutParams ?: return
    val screen = resources.displayMetrics.widthPixels
    lp.width = if (wide) {
        (screen * 0.94f).toInt().coerceAtMost(dp(1500))
    } else {
        min(dp(520), screen - dp(32))
    }
    lp.height = if (wide) {
        ViewGroup.LayoutParams.MATCH_PARENT
    } else {
        ViewGroup.LayoutParams.WRAP_CONTENT
    }
    setScroll.layoutParams = lp
    setScroll.isFillViewport = wide
}

internal fun MainActivity.rebuild(reopenSettings: Boolean) {
    initHistory()
    buildUi()
    refreshPaletteSurfaces()
    if (reopenSettings) showSettings()
}

// Reapply palette-dependent outlines after rebuilding the view hierarchy.
// Some overlay containers can retain the previous theme's stroke when
// Android restores focus during a live theme change.
internal fun MainActivity.refreshPaletteSurfaces() {
    val p = pal
    if (panelInitialized) panel.background = box(solidColor(), p.cAccent)
    if (setBoxInitialized) setBox.background = box(solidColor(), p.cAccent)
}
