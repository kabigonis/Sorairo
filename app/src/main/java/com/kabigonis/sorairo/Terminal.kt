package com.kabigonis.sorairo

import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

// Terminal window page, command history and command interpreter.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.buildPage(ws: Int): View {
    val p = pal
    val row = TileLayout(this, dp(12)).apply {
        setPadding(dp(12), dp(8), dp(12), 0)
        setOnLongClickListener { showSettings(); true }
    }
    row.moveModeActive = { moveSource[ws] >= 0 }
    row.onMoveTap = { id -> moveTap(ws, id) }

    // ---- terminal window ----
    val term = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = box(panelColor(), p.cAccent)
    }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val terminalTitle = txt(12.5f, p.cBlue, "~/terminal").apply {
        setOnLongClickListener {
            if (moveSource[ws] >= 0) moveTap(ws, "terminal")
            else startMove(ws, "terminal")
            true
        }
    }
    col.addView(terminalTitle, LinearLayout.LayoutParams(-1, -2))
    col.addView(buildFetch(ws), LinearLayout.LayoutParams(-1, -2))
    val out = txt(12.5f, termFg()).apply {
        setLineSpacing(0f, 1.15f)
        text = history[ws]
    }
    col.addView(out, LinearLayout.LayoutParams(-1, -2))
    val scroll = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        addView(col, FrameLayout.LayoutParams(-1, -2))
    }
    outViews[ws] = out
    outScrolls[ws] = scroll
    term.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

    var input: EditText? = null
    var promptRow: LinearLayout? = null
    if (mode != "drawer") {
        promptRow = LinearLayout(this).apply {
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
        promptRow!!.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        term.addView(promptRow, LinearLayout.LayoutParams(-1, -2))
    }
    val field = input
    terminalPrompts[ws] = field
    fun focusInput() {
        if (field != null) {
            drawerOwnsIme = false
            field.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    fun focusTerminal(showKeyboard: Boolean) {
        if (moveTap(ws, "terminal")) return
        val pinned = pinWindowForFocus[ws]
        if (pinned != null) setWindowFocus(ws, 0, term, pinned)
        else focusedWindow[ws] = 0
        if (showKeyboard) focusInput()
    }

    promptRow?.setOnClickListener {
        focusTerminal(false)
        focusInput()
    }

    term.setOnClickListener { focusTerminal(false) }
    out.setOnClickListener { focusTerminal(false) }
    col.setOnClickListener { focusTerminal(false) }
    scroll.setOnClickListener { focusTerminal(false) }
    // Long-pressing the terminal surface opens configuration.
    // Only the explicit ~/terminal title is the tile-move handle.
    val openTerminalConfig = View.OnLongClickListener { showSettings(); true }
    term.setOnLongClickListener(openTerminalConfig)
    out.setOnLongClickListener(openTerminalConfig)
    col.setOnLongClickListener(openTerminalConfig)
    scroll.setOnLongClickListener(openTerminalConfig)

    terminalWindowForFocus[ws] = term
    val showTerminalWindow = windowShown(ws, "terminal")
    val showPinnedWindow = windowShown(ws, "pinned")
    val showBottomWindow = windowShown(ws, "bottom")
    term.tag = "terminal"
    row.addView(term, ViewGroup.LayoutParams(-1, -1))

    // ---- pinned window ----
    val pinWin = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = box(panelColor(), p.cDim)
        setOnLongClickListener { showSettings(); true }
    }
    pinWindowForFocus[ws] = pinWin
    pinWin.setOnClickListener {
        if (!moveTap(ws, "pinned")) setWindowFocus(ws, 1, term, pinWin)
    }
    setWindowFocus(ws, focusedWindow[ws], term, pinWin)
    val pinTitle = txt(13f, p.cBlue, "~/pinned")
    pinTitle.setOnLongClickListener { if (moveSource[ws] >= 0) moveTap(ws, "pinned") else startMove(ws, "pinned"); true }
    pinWin.addView(pinTitle)
    val pinList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    pinBoxes[ws] = pinList
    pinWin.addView(pinList, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
    val defaultPrompt = txt(12.5f, p.cBlue, "Set as default launcher").apply {
        setPadding(0, dp(12), 0, dp(4))
        visibility = View.GONE
        setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
    defaultLauncherPrompts[ws] = defaultPrompt
    pinWin.addView(defaultPrompt, LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(4)
    })
    fillPins(ws)
    val bottomWin = buildBottomWindow(ws)
    bottomWindowForFocus[ws] = bottomWin
    bottomWin.setOnClickListener { if (!moveTap(ws, "bottom")) setWindowFocus(ws, 2, term, pinWin) }
    pinWin.tag = "pinned"
    bottomWin.tag = "bottom"
    row.addView(pinWin, ViewGroup.LayoutParams(-1, -1))
    row.addView(bottomWin, ViewGroup.LayoutParams(-1, -1))
    tileLayouts[ws] = row
    updateBottomWindowDisplay(ws, false)
    updateMonitorSummary(ws)
    updatePlayerWindow(ws)
    row.configure(layoutModeFor(ws), layoutRatioFor(ws),
        tileOrder(ws),
        buildSet {
            if (showTerminalWindow) add("terminal")
            if (showPinnedWindow) add("pinned")
            if (showBottomWindow) add("bottom")
        }, false)
    return row
}

// ================= terminal =================
internal fun MainActivity.initHistory() {
    for (i in 0..4) history[i].clear()
}

internal fun MainActivity.refreshOut(ws: Int) {
    outViews[ws]?.text = history[ws]
    outScrolls[ws]?.post { outScrolls[ws]?.fullScroll(View.FOCUS_DOWN) }
}

internal fun MainActivity.norm(s: String) = s.lowercase().replace(" ", "")

internal fun MainActivity.runCommand(ws: Int, line: String) {
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
                "  barreset       put the top bar modules back (long-press a module to move it)",
                "  win <mode>     open apps as windows: left/right/float/off",
                "  fetch          refresh system info",
                "  panel <mode>   set bottom window: monitor/player",
                "  layout <name>  set layout: dwindle/master/columns/rows",
                "  ratio <30-70>  set primary split percentage",
                "  swap <a> <b>  swap terminal/pinned/bottom",
                "  swap          swap focused window with next",
                "  focus next|prev  move focus highlight",
                "  logo [name]    android/arch/omarchy/image/none",
                "  ws <1-5>       switch workspace",
                "  theme [name]   change theme",
                "  font [name]    change font",
                "  wall           open wallpaper library",
                "  wall clear     clear current wallpaper",
                "  config         open settings",
                "  island on|off  toggle the dynamic island",
                "  island demo <kind>  preview hud/media/timer/alarm/power",
                "  pin <app>      pin to this workspace",
                "  unpin <app>    unpin it",
                "  hide <app>     hide app from launcher",
                "  unhide <app>   show it again",
                "  hidden         list hidden apps",
                "  ls             list apps",
                "  battery        battery panel (also: tap the % in the bar)",
                "  battery adb    optional adb grant for Power-saver and DNS switching",
                "  display        brightness, text size and UI scale",
                "  audio          volume and mute",
                "  network        Wi-Fi stats and DNS provider (also: net)",
                "  dns custom <h> set the custom DNS hostname",
                "  scale <x>      UI scale, e.g. scale 1.25 (scale 1 resets)",
                "  vpn            open your vpn / DNS app (e.g. DNSNet)",
                "  sudo [cmd]     authenticate privileged actions with your screen lock",
                "  sudo -k        lock sudo now (sudo passwordless on|off)",
                "  night [on|off] Night Light (needs secure-settings grant for direct switching)",
                "  awake [on|off] stay awake (sudo for system-wide change)",
                "  dnd [on|off]   silence/allow notifications",
                "  rec [stop]     screen recording to Movies/Sorairo",
                "  remind <min>   set a countdown (remind off clears it)",
                "  <app name>     launch (fuzzy match)"
            ).forEach { say(it) }
            "clear" -> sb.clear()
            "barreset" -> { resetBarLayout(); say("top bar layout reset", p.cGreen) }
            "win" -> {
                val setting = when (freeformSettingOn()) { true -> "on"; false -> "off"; null -> "unreadable" }
                if (arg.isEmpty()) {
                    say("app windows: ${appWindowMode()}", p.cGreen)
                    say("freeform feature: ${if (freeformSupported()) "yes" else "no"}, setting: $setting", p.cDim)
                } else if (arg.lowercase() in APP_WINDOW_MODES) {
                    setAppWindowMode(arg.lowercase())
                    say("app windows set to ${arg.lowercase()}", p.cGreen)
                    if (!freeformSupported() && freeformSettingOn() != true)
                        say("freeform looks off: enable it in Developer options or apps stay fullscreen", p.cDim)
                } else say("usage: win left|right|float|off", p.cDim)
            }
            "fetch", "fastfetch", "neofetch" -> refreshFetch(ws)
            "layout" -> {
                if (arg.isEmpty()) say("layout: ${layoutModeFor(ws)}", p.cGreen)
                else if (arg in setOf("dwindle", "master", "columns", "rows")) {
                    setLayoutMode(ws, arg)
                    say("layout set to $arg", p.cGreen)
                } else say("usage: layout dwindle|master|columns|rows", p.cDim)
            }
            "swap" -> {
                val names = arg.split(Regex("\\s+")).filter { it.isNotBlank() }
                val ok = if (names.size >= 2) swapTileNames(ws, names[0], names[1]) else {
                    val visible = tileOrder(ws).filter { windowShown(ws, it) }
                    val current = tileIds.getOrElse(focusedWindow[ws]) { "terminal" }
                    val i = visible.indexOf(current)
                    i >= 0 && visible.size > 1 && swapTileNames(ws, current, visible[(i + 1) % visible.size])
                }
                say(if (ok) "windows swapped" else "usage: swap [terminal|pinned|bottom] [terminal|pinned|bottom]", if (ok) p.cGreen else p.cDim)
            }
            "focus" -> when (arg.lowercase()) {
                "next" -> stepFocus(ws, 1)
                "prev", "previous" -> stepFocus(ws, -1)
                else -> say("usage: focus next|prev", p.cDim)
            }
            "ratio" -> {
                val value = arg.toIntOrNull()
                if (value != null && value in 30..70) {
                    setLayoutRatio(ws, value)
                    say("layout ratio set to $value%", p.cGreen)
                } else say("usage: ratio 30-70", p.cDim)
            }
            "panel" -> {
                if (arg == "monitor" || arg == "player") {
                    setBottomWindowForWorkspace(ws, arg)
                    updateBottomWindowDisplay(ws, true)
                    if (ws == curWs) updateBottomWindowRuntime()
                } else {
                    say("usage: panel monitor|player", p.cDim)
                }
            }
            "ws" -> {
                val n = arg.toIntOrNull()
                if (n != null && n in 1..5) pager.setCurrentItem(n - 1, true)
                else say("usage: ws 1-5", p.cDim)
            }
            "wall", "wallpaper" -> {
                if (arg.equals("clear", true)) {
                    clearCurrentWallpaper()
                    say("wallpaper cleared", p.cGreen)
                } else {
                    openWallpaperPage()
                }
            }
            "config", "settings" -> showSettings()
            "island" -> {
                when {
                    arg.equals("on", true) -> {
                        prefs.edit().putBoolean("dynamic_island", true).apply()
                        refreshIsland()
                        say("dynamic island on", p.cGreen)
                    }
                    arg.equals("off", true) -> {
                        prefs.edit().putBoolean("dynamic_island", false).apply()
                        islandEmptyExpanded = false
                        refreshIsland()
                        say("dynamic island off", p.cGreen)
                    }
                    arg.startsWith("demo ", true) -> {
                        val kind = when (arg.substringAfter(' ').trim().lowercase()) {
                            "hud" -> IslandKind.HUD
                            "media" -> IslandKind.MEDIA
                            "timer" -> IslandKind.TIMER
                            "alarm" -> IslandKind.ALARM
                            "power" -> IslandKind.POWER
                            else -> null
                        }
                        if (kind == null) {
                            say("usage: island demo hud|media|timer|alarm|power", p.cDim)
                        } else {
                            val title = when (kind) {
                                IslandKind.HUD -> "HUD demo"
                                IslandKind.MEDIA -> "Now playing demo"
                                IslandKind.TIMER -> "Timer demo"
                                IslandKind.ALARM -> "Alarm demo"
                                IslandKind.POWER -> "Power demo"
                                IslandKind.NOTIFICATION -> "Notification demo"
                            }
                            // Demo activities are sticky by design, so remove the previous
                            // preview before submitting another one. Otherwise a higher-priority
                            // old demo (for example ALARM) masks the new preview indefinitely.
                            IslandKind.values().forEach { demoKind ->
                                islandCore.remove("demo:" + demoKind.name.lowercase())
                            }
                            val result = islandCore.submit(IslandActivity(
                                id = "demo:" + kind.name.lowercase(),
                                kind = kind,
                                title = title,
                                subtitle = "Dynamic Island preview",
                                progress = if (kind == IslandKind.TIMER) 0.65f else null,
                                ttlMs = 0L
                            ), SystemClock.elapsedRealtime())
                            if (result.accepted) {
                                prefs.edit().putBoolean("dynamic_island", true).apply()
                                islandEmptyExpanded = false
                                refreshIsland()
                                say("submitted " + kind.name.lowercase() + " demo", p.cGreen)
                            } else say(result.error ?: "could not submit demo", p.cAccent)
                        }
                    }
                    else -> say("usage: island on|off|demo hud|media|timer|alarm|power", p.cDim)
                }
            }
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
                    onThemeApplied(i)
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
            "display" -> showDisplayPanel()
            "audio" -> showAudioPanel()
            "network", "net" -> showNetworkPanel()
            "dns" -> {
                val a = arg.split(" ", limit = 2)
                if (a[0] == "custom" && a.size > 1 && a[1].isNotBlank()) {
                    prefs.edit().putString("dns_custom", a[1].trim()).apply()
                    say("custom DNS host: ${a[1].trim()}", p.cGreen)
                } else {
                    val names = listOf("DHCP", "Cloudflare", "Google", "Custom")
                    say("dns provider: ${names[currentDnsProvider()]}  (set a custom host: dns custom <host>)", p.cDim)
                }
            }
            "scale" -> {
                val v = arg.removeSuffix("x").toFloatOrNull()
                if (v == null || v < 0.75f || v > 2.5f) {
                    say("usage: scale <0.75-2.5>   (current ${formatScale(uiScale)})", p.cDim)
                } else {
                    setUiScale(v)
                    say("scale ${formatScale(v)}", p.cGreen)
                    rebuild(false)
                }
            }
            "battery", "batt" -> if (arg == "adb") {
                say("optional, for power users: switch Battery Saver from the panel", p.cDim)
                say("1. enable Wireless debugging, pair it with adb", p.cDim)
                say("2. run: ${batterySaverGrantCommand()}", p.cFg)
                say("without it the panel opens Android's Battery Saver settings", p.cDim)
            } else showBatteryPanel()
            "vpn" -> launchVpn()
            "sudo" -> handleSudoCommand(ws, arg)
            "night", "awake", "dnd", "rec", "remind" -> handleTrayCommand(ws, cmd, arg)
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
