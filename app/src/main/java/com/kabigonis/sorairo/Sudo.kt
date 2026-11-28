package com.kabigonis.sorairo

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast

private const val SUDO_TIMEOUT_MS = 5 * 60_000L
private const val KEY_PASSWORDLESS = "sudo_passwordless"

@Volatile
private var sudoUntil = 0L
private var pendingSudo: (() -> Unit)? = null

internal fun MainActivity.sudoPasswordless(): Boolean =
    prefs.getBoolean(KEY_PASSWORDLESS, false)

internal fun MainActivity.sudoActive(): Boolean =
    sudoPasswordless() || SystemClock.elapsedRealtime() < sudoUntil

private fun MainActivity.deviceHasLock(): Boolean = try {
    (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
} catch (_: Exception) { false }

internal fun MainActivity.sudoIf(needed: Boolean, reason: String, action: () -> Unit) {
    if (needed) requireSudo(reason, action) else action()
}

internal fun MainActivity.requireSudo(reason: String, action: () -> Unit) {
    if (sudoActive()) {
        if (!sudoPasswordless()) sudoUntil = SystemClock.elapsedRealtime() + SUDO_TIMEOUT_MS
        action()
        return
    }
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    @Suppress("DEPRECATION")
    val prompt: Intent? = if (keyguard.isDeviceSecure) {
        keyguard.createConfirmDeviceCredentialIntent("Sorairo sudo", reason)
    } else null
    if (prompt == null) {
        action()
        return
    }
    pendingSudo = action
    try {
        sudoAuth.launch(prompt)
    } catch (_: Exception) {
        pendingSudo = null
        Toast.makeText(this, "sudo: couldn't open the screen-lock prompt", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.onSudoResult(ok: Boolean) {
    val action = pendingSudo
    pendingSudo = null
    if (!ok || action == null) {
        Toast.makeText(this, "sudo: authentication failed", Toast.LENGTH_SHORT).show()
        refreshOmarchyActionTray()
        return
    }
    sudoUntil = SystemClock.elapsedRealtime() + SUDO_TIMEOUT_MS
    action()
    refreshOmarchyActionTray()
}

internal fun MainActivity.lockSudo() {
    sudoUntil = 0L
    refreshOmarchyActionTray()
}

private fun MainActivity.setSudoPasswordless(on: Boolean) {
    prefs.edit().putBoolean(KEY_PASSWORDLESS, on).apply()
    refreshOmarchyActionTray()
}

internal fun MainActivity.sudoStatusLine(): String = when {
    sudoPasswordless() -> "passwordless: enabled"
    sudoActive() -> {
        val left = ((sudoUntil - SystemClock.elapsedRealtime()) / 60_000L + 1L).coerceAtLeast(1L)
        "unlocked, locks in about $left min"
    }
    !deviceHasLock() -> "locked, but this device has no screen lock"
    else -> "locked"
}

/**
 * The sudo glyph's panel. Locked: UNLOCK asks for the device screen lock right here and KEEP UNLOCKED
 * does the same and then stops asking (passwordless). Unlocked: LOCK NOW or KEEP UNLOCKED.
 */
internal fun MainActivity.showSudoPanel() {
    val p = pal
    val root = trayDialogRoot(p.cBg, p.cAnsi[1])
    val passwordless = sudoPasswordless()
    val unlocked = !passwordless && sudoActive()
    val noLock = !passwordless && !unlocked && !deviceHasLock()
    root.addView(txt(13f, p.cAnsi[1], if (passwordless) "Disable Passwordless Sudo" else "Sudo"))
    val detail = when {
        passwordless ->
            "Passwordless sudo is on: privileged Sorairo actions never ask. Turn it off to require your device screen lock again."
        unlocked -> {
            val left = ((sudoUntil - SystemClock.elapsedRealtime()) / 60_000L + 1L).coerceAtLeast(1L)
            "Unlocked. Privileged Sorairo actions will not ask for your screen lock; it locks again in about $left min."
        }
        noLock ->
            "This device has no screen lock, so privileged Sorairo actions run without asking. Set a screen lock in Android settings to protect them."
        else ->
            "Locked. Privileged Sorairo actions use your device screen lock. Unlock for 5 minutes, or keep it unlocked."
    }
    root.addView(txt(12f, p.cFg, detail).apply { setPadding(0, dp(8), 0, dp(10)) })
    val actions = ArrayList<Pair<String, () -> Unit>>()
    when {
        passwordless -> actions.add("LOCK" to {
            setSudoPasswordless(false)
            lockSudo()
            showSudoPanel()
        })
        unlocked -> {
            actions.add("LOCK NOW" to {
                lockSudo()
                showSudoPanel()
            })
            actions.add("KEEP UNLOCKED" to {
                setSudoPasswordless(true)
                showSudoPanel()
            })
        }
        !noLock -> {
            actions.add("UNLOCK" to {
                requireSudo("Unlock Sorairo sudo") { showSudoPanel() }
            })
            actions.add("KEEP UNLOCKED" to {
                requireSudo("Keep Sorairo sudo unlocked") {
                    setSudoPasswordless(true)
                    showSudoPanel()
                }
            })
        }
    }
    actions.add("CLOSE" to { dismissTrayDialog() })
    root.addView(trayDialogActions(*actions.toTypedArray()))
    showTrayDialog(root, 480)
}

internal fun MainActivity.terminalSay(ws: Int, text: String, color: Int? = null) {
    history[ws].add(text + "\n", color ?: termFg())
    refreshOut(ws)
}

private fun MainActivity.terminalAllowed(ws: Int, name: String): Boolean {
    if (sudoActive() || !deviceHasLock()) return true
    terminalSay(ws, "$name: permission denied (try: sudo $name)", pal.cAccent)
    return false
}

private fun parseOnOff(arg: String, current: Boolean): Boolean = when (arg.trim().lowercase()) {
    "on", "1", "true", "start" -> true
    "off", "0", "false", "stop" -> false
    else -> !current
}

internal fun MainActivity.handleSudoCommand(ws: Int, arg: String) {
    val p = pal
    val parts = arg.split(Regex("\\s+"), 2)
    val first = parts.firstOrNull().orEmpty().lowercase()
    val rest = parts.getOrElse(1) { "" }.trim()
    when {
        arg.isBlank() -> {
            terminalSay(ws, "sudo: ${sudoStatusLine()}", p.cDim)
            showSudoPanel()
        }
        first == "-k" -> {
            lockSudo()
            terminalSay(ws, "sudo: locked", p.cGreen)
        }
        first == "-v" -> requireSudo("Refresh sudo") {
            terminalSay(ws, "sudo: refreshed, ${sudoStatusLine()}", p.cGreen)
        }
        first == "passwordless" -> {
            when (rest.lowercase()) {
                "off" -> {
                    setSudoPasswordless(false)
                    terminalSay(ws, "sudo passwordless: off", p.cGreen)
                }
                "on" -> requireSudo("Turn on passwordless sudo") {
                    setSudoPasswordless(true)
                    terminalSay(ws, "sudo passwordless: on", p.cAnsi[1])
                }
                else -> terminalSay(ws, "usage: sudo passwordless on|off", p.cDim)
            }
        }
        else -> requireSudo("sudo $arg") { runCommand(ws, arg) }
    }
}

internal fun MainActivity.handleTrayCommand(ws: Int, cmd: String, arg: String) {
    val p = pal
    when (cmd) {
        "night" -> {
            if (!terminalAllowed(ws, "night")) return
            val on = parseOnOff(arg, isNightLightEnabled())
            if (setNightLight(on)) terminalSay(ws, "night light ${if (on) "on" else "off"}", p.cGreen)
            else terminalSay(ws, "night: opened Android's settings (secure-settings grant needed for direct switching)", p.cDim)
        }
        "awake" -> {
            if (Settings.System.canWrite(this) && !terminalAllowed(ws, "awake")) return
            setStayAwake(parseOnOff(arg, isStayAwakeOn()))
            terminalSay(ws, "stay awake ${if (isStayAwakeOn()) "on" else "off"}", p.cGreen)
        }
        "dnd" -> {
            val on = parseOnOff(arg, isDoNotDisturbEnabled())
            setDoNotDisturb(on)
            terminalSay(ws, "notifications ${if (on) "silenced" else "allowed"}", p.cGreen)
        }
        "rec" -> {
            val on = parseOnOff(arg, ScreenRecordService.recording)
            if (on) startScreenRecording() else stopScreenRecording()
            terminalSay(ws, if (on) "rec: asking Android for permission..." else "rec: stopping", p.cDim)
        }
        "remind" -> {
            val value = arg.trim()
            val minutes = value.toIntOrNull()
            if (value.equals("off", true)) {
                cancelReminder()
                terminalSay(ws, "reminder cleared", p.cGreen)
            } else if (minutes == null) {
                terminalSay(ws, "usage: remind <minutes>   (remind off clears it)", p.cDim)
            } else if (scheduleReminder(minutes)) {
                terminalSay(ws, "reminder set for $minutes min", p.cGreen)
            }
        }
    }
}
