package com.kabigonis.sorairo

import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.Normalizer
import kotlin.math.min
import com.kabigonis.sorairo.MainActivity.App
import com.kabigonis.sorairo.MainActivity.RowAdapter
import com.kabigonis.sorairo.MainActivity.Companion.NEW_APP_WINDOW_MS

// App drawer, search, pins, hidden apps and app launching.
// Moved verbatim from MainActivity.kt as extension functions on MainActivity.

internal fun MainActivity.showPinnedPicker(ws: Int) {
    fillPinnedPicker(ws)
    setScroll.scrollTo(0, 0)
    showOverlay()
}

internal fun MainActivity.fillPinnedPicker(ws: Int) {
    val p = pal
    setBox.removeAllViews()
    setBox.addView(txt(15f, p.cBlue, "~/pinned"))
    setBox.addView(txt(11f, p.cDim, "pick the apps pinned to workspace ${ws + 1}").apply { setPadding(0, dp(2), 0, dp(6)) })
    setBox.addView(txt(14f, p.cAccent, "← back").apply {
        setPadding(0, dp(8), 0, dp(8))
        setOnClickListener { closeSettings() }
    })
    allApps.forEach { app ->
        val chosen = app.pkg in pins(ws)
        setBox.addView(txt(14f, if (chosen) p.cAccent else p.cFg, displayAppLabel(app)).apply {
            setPadding(0, dp(9), 0, dp(9))
            setOnClickListener { togglePin(ws, app); fillPinnedPicker(ws) }
        })
    }
}

internal fun MainActivity.fillPins(ws: Int) {
    val p = pal
    val list = pinBoxes[ws] ?: return
    list.removeAllViews()
    val pinned = pins(ws).mapNotNull { k -> allApps.find { it.pkg == k } }

    if (pinned.isEmpty()) {
        list.addView(
            txt(
                12.5f, p.cDim,
                "nothing pinned yet.\nconfigure pinned apps in ~/config"
            )
        )
    } else {
        pinned.forEach { app ->
            list.addView(
                txt(16f, p.cFg, displayAppLabel(app)).apply {
                    setPadding(0, dp(9), 0, dp(9))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener {
                        val terminal = terminalWindowForFocus[ws]
                        val pinnedWindow = pinWindowForFocus[ws]
                        if (terminal != null && pinnedWindow != null) {
                            setWindowFocus(ws, 1, terminal, pinnedWindow)
                        }
                        launchApp(app)
                    }
                    setOnLongClickListener { true }
                }
            )
        }
    }

    defaultLauncherPrompts[ws]?.visibility =
        if (isDefaultLauncher()) View.GONE else View.VISIBLE
}

// ================= hidden apps =================
internal fun MainActivity.hiddenSet(): Set<String> =
    (prefs.getString("hidden", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

internal fun MainActivity.visibleApps(): List<App> {
    val h = hiddenSet()
    return allApps.filter { it.pkg !in h }
}

internal fun MainActivity.toggleHidden(app: App) {
    val h = hiddenSet().toMutableSet()
    if (!h.remove(app.pkg)) h.add(app.pkg)
    prefs.edit().putString("hidden", h.joinToString(",")).apply()
    apps = visibleApps()
    adapter.items = apps
    adapter.notifyDataSetChanged()
    for (i in 0..4) {
        fillPins(i)
        refreshFetch(i)
    }
}

internal fun MainActivity.isNewApp(pkg: String): Boolean {
    val installed = appInstallTimes[pkg] ?: return false
    return System.currentTimeMillis() - installed < NEW_APP_WINDOW_MS
}

internal fun MainActivity.refreshInstalledApps() {
    val fresh = loadApps()
    if (fresh == allApps) return
    allApps = fresh
    apps = visibleApps()
    val query = search.text?.toString().orEmpty()
    adapter.items = if (query.isBlank()) apps else matches(query)
    adapter.notifyDataSetChanged()
    for (i in 0..4) fillPins(i)
}

internal fun MainActivity.loadApps(): List<App> {
    val pm = packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(q, 0)
        .filter { it.activityInfo.packageName != packageName }
        .map {
            val pkg = it.activityInfo.packageName
            try {
                appInstallTimes[pkg] = pm.getPackageInfo(pkg, 0).firstInstallTime
            } catch (_: Exception) {
            }
            App(it.loadLabel(pm).toString(), pkg)
        }
        .sortedBy { it.label.lowercase() }
}

internal fun MainActivity.score(q: String, l: String): Int {
    if (q.isEmpty() || l.isEmpty()) return 0

    // Exact match, then app-name prefix.
    if (l == q) return 1000
    if (l.startsWith(q)) return 900 - l.length

    // Match the start of a word, e.g. "play" in "Google Play Store".
    val words = l.split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
    if (words.any { it.startsWith(q) }) return 800 - l.length

    // Match only consecutive letters, e.g. "flac" in "SpotiFLAC".
    val idx = l.indexOf(q)
    if (idx >= 0) return 600 - idx

    // No scattered-letter or typo matches.
    return 0
}

internal fun MainActivity.normalizeAppSearch(value: CharSequence): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(searchDiacritics, "")
        .replace(searchSeparators, "")

internal fun MainActivity.matches(query: String): List<App> {
    if (query.isBlank()) return apps
    val normalizedQuery = normalizeAppSearch(query)
    return apps.filter { app ->
        val label = displayAppLabel(app)
        label.contains(query.trim(), ignoreCase = true) ||
            (normalizedQuery.isNotEmpty() &&
                normalizeAppSearch(label).contains(normalizedQuery, ignoreCase = true))
    }
}

// Olauncher-style: launch a unique match once per query after filtering completes.
internal fun MainActivity.autoLaunchSearchResult(query: String) {
    if (query == autoLaunchedQuery) return
    autoLaunchedQuery = null
    if (query.isBlank() || query.startsWith("!") || adapter.items.size != 1 ||
        drawer.visibility != View.VISIBLE
    ) return
    val app = adapter.items.first()
    if (app.pkg.isBlank()) return
    autoLaunchedQuery = query
    launchApp(app)
}

internal fun MainActivity.displayAppLabel(app: App): String = prefs.getString("app_name_${app.pkg}", null)?.takeIf { it.isNotBlank() } ?: app.label

internal fun MainActivity.beginDrawerAppActions(app: App, holder: RowAdapter.VH) {
    if (activeDrawerActionHolder !== holder) activeDrawerActionHolder?.hideActions()
    selectedDrawerApp = app
    activeDrawerActionHolder = holder
    holder.showActions()
}

internal fun MainActivity.endDrawerAppActions() {
    selectedDrawerApp = null
    activeDrawerActionHolder?.hideActions()
    activeDrawerActionHolder = null
}

internal fun MainActivity.addDrawerAction(bar: LinearLayout, label: String, glyph: String, onClick: () -> Unit) {
    bar.addView(
        txt(10f, pal.cFg, "$glyph\n$label").apply {
            gravity = Gravity.CENTER
            contentDescription = label
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setOnClickListener { onClick() }
        },
        LinearLayout.LayoutParams(0, -1, 1f)
    )
}

internal fun MainActivity.runDrawerAction(app: App, action: String) {
    when (action) {
        "Uninstall" -> {
            try {
                val uninstall = Intent(
                    Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.parse("package:${app.pkg}")
                ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
                startActivity(uninstall)
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open uninstall screen", Toast.LENGTH_SHORT).show()
            }
            endDrawerAppActions()
        }
        "Rename" -> showRenameDialog(app)
        "Hide" -> { endDrawerAppActions(); toggleHidden(app) }
        "Info" -> {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) }
            catch (_: Exception) { Toast.makeText(this, "Unable to open app info", Toast.LENGTH_SHORT).show() }
            endDrawerAppActions()
        }
        "Close" -> endDrawerAppActions()
    }
}

internal fun MainActivity.showRenameDialog(app: App) {
    val p = pal
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(p.cBg, p.cAccent)
        setPadding(dp(18), dp(16), dp(18), dp(12))
    }
    content.addView(
        txt(15f, p.cBlue, "~/rename-app").apply {
            setPadding(0, 0, 0, dp(12))
        }
    )
    val edit = EditText(this).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setText(displayAppLabel(app))
        setSelection(text.length)
        setTextColor(p.cFg)
        setHintTextColor(p.cDim)
        typeface = font
        textSize = 15f
        background = box(p.cBg, p.cDim)
        setPadding(dp(10), dp(9), dp(10), dp(9))
        hint = "App name"
    }
    content.addView(edit, LinearLayout.LayoutParams(-1, -2))
    val buttons = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, 0)
    }
    fun dialogButton(label: String, color: Int, onClick: () -> Unit) {
        buttons.addView(
            txt(12f, color, label).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(8), dp(10))
                setOnClickListener { onClick() }
            }
        )
    }
    val dialog = AlertDialog.Builder(this).setView(content).create()
    dialogButton("CANCEL", p.cDim) { dialog.dismiss() }
    dialogButton("SAVE", p.cAccent) {
        val name = edit.text.toString().trim()
        if (name.isNotEmpty()) {
            prefs.edit().putString("app_name_${app.pkg}", name).apply()
            adapter.notifyDataSetChanged()
            endDrawerAppActions()
        }
        dialog.dismiss()
    }
    content.addView(buttons, LinearLayout.LayoutParams(-1, -2))
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(min(dp(520), resources.displayMetrics.widthPixels - dp(48)), -2)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    edit.requestFocus()
}

internal fun MainActivity.launchApp(app: App) {
    closeDrawer()
    packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
}

internal fun MainActivity.pins(ws: Int): List<String> =
    (prefs.getString("pins_$ws", "") ?: "").split(",").filter { it.isNotEmpty() }

internal fun MainActivity.togglePin(ws: Int, app: App): Boolean {
    val l = pins(ws).toMutableList()
    val added = !l.remove(app.pkg)
    if (added) l.add(app.pkg)
    prefs.edit().putString("pins_$ws", l.joinToString(",")).apply()
    fillPins(ws)
    adapter.notifyDataSetChanged()
    return added
}

// ================= drawer =================
internal fun MainActivity.openDrawer() {
    if (drawer.visibility == View.VISIBLE || settings.visibility == View.VISIBLE) return
    search.setText("")
    drawerOwnsIme = true
    drawer.visibility = View.VISIBLE
    drawer.alpha = 0f
    panel.translationY = dp(80).toFloat()
    drawer.animate().alpha(1f).setDuration(160).start()
    panel.animate().translationY(0f).setDuration(200).start()
    search.requestFocus()
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
}

internal fun MainActivity.closeDrawer() {
    autoLaunchedQuery = null
    endDrawerAppActions()
    if (drawer.visibility != View.VISIBLE) return
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(search.windowToken, 0)
    drawer.animate().alpha(0f).setDuration(140)
        .withEndAction { drawer.visibility = View.GONE }.start()
}
