package com.kabigonis.sorairo

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast

/**
 * "sudo" for Android.
 *
 * Sorairo can change real system state (Night Light, Battery Saver, Private DNS, the system-wide
 * screen timeout). Those actions are the launcher's equivalent of root commands, so they sit
 * behind a sudo gate:
 *
 *  - The "password" is the device screen lock (PIN, pattern, password or biometric).
 *  - A successful unlock lasts 5 minutes and is refreshed by each privileged action, like sudo's
 *    credential timestamp.
 *  - "Passwordless sudo" skips the prompt entirely. It is the same convenience-vs-risk trade as on
 *    Linux, which is why the glyph is red while sudo is live.
 *  - No screen lock set means there is nothing to ask for, so actions simply run.
 *
 * Terminal: `sudo` (panel), `sudo -k` (lock now), `sudo -v` (refresh), `sudo passwordless on|off`,
 * and `sudo <command>` to run a command after unlocking.
 */
private const val SUDO_TIMEOUT_MS = 5 * 60_000L
private const val KEY_PASSWORDLESS = "sudo_passwordless"

/** In memory on purpose: a restart or process death always locks sudo again. */
@Volatile
private var sudoUntil = 0L
private var pendingSudo: (() -> Unit)? = null

internal fun MainActivity.sudoPasswordless(): Boolean = prefs.getBoolean(KEY_PASSWORDLESS, false)

internal fun MainActivity.sudoActive(): Boolean =
    sudoPasswordless() || SystemClock.elapsedRealtime() < sudoUntil

private fun MainActivity.deviceHasLock(): Boolean = try {
    (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
} catch (_: Exception) { false }

/** Runs [action] right away, or after the screen-lock check when [needed] and sudo is locked. */
internal fun MainActivity.sudoIf(needed: Boolean, reason: String, action: () -> Unit) {
    if (needed) requireSudo(reason, action) else action()
}

internal fun MainActivity.requireSudo(reason: String, action: () -> Unit) {
    if (sudoActive()) {
        if (!sudoPasswordless()) sudoUntil = SystemClock.elapsedRealtime() + SUDO_TIMEOUT_MS
        action()
        return
    }
    val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    @Suppress("DEPRECATION")
    val intent: Intent? = if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("Sorairo sudo", reason) else null
    if (intent == null) {
        action()
        return
    }
    pendingSudo = action
    try {
        sudoAuth.launch(intent)
    } catch (_: Exception) {
        pendingSudo = null
        Toast.makeText(this, "sudo: couldn't open the screen-lock prompt", Toast.LENGTH_LONG).show()
    }
}

/** Result of the screen-lock prompt, wired up in MainActivity. */
internal fun MainActivity.onSudoResult(ok: Boolean) {
    val action = pendingSudo
    pendingSudo = null
    if (!ok || action == null) {
        Toast.makeText(this, "sudo: authentication failed", Toast.LENGTH_SHORT).show()
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
    sudoPasswordless() -> "passwordless: always unlocked"
    sudoActive() -> {
        val left = ((sudoUntil - SystemClock.elapsedRealtime()) / 60_000L + 1L).coerceAtLeast(1L)
        "unlocked, locks in about $left min"
    }
    !deviceHasLock() -> "locked, but this device has no screen lock, so nothing is asked"
    else -> "locked"
}

internal fun MainActivity.showSudoPanel() {
    val p = pal
    val live = sudoActive()
    val root = trayDialogRoot(p.cBg, if (live) p.cAnsi[1] else p.cBlue)
    root.addView(txt(13f, if (live) p.cAnsi[1] else p.cBlue, "sudo"))
    root.addView(txt(12f, p.cFg, sudoStatusLine()).apply { setPadding(0, dp(8), 0, dp(10)) })
    root.addView(txt(
        11f,
        p.cDim,
        "Changing system settings asks for your screen lock first. Unlocking lasts 5 minutes."
    ).apply { setPadding(0, 0, 0, dp(10)) })

    fun grant(ok: Boolean, name: String, what: String) {
        root.addView(txt(11f, if (ok) p.cGreen else p.cDim, (if (ok) "[x] " else "[ ] ") + name + "  " + what))
    }
    val secure = hasSecureSettings()
    grant(secure, "secure settings", "night light, battery saver, DNS")
    grant(Settings.System.canWrite(this), "modify system settings", "system-wide stay awake")
    grant(
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .isNotificationPolicyAccessGranted,
        "do not disturb access", "silence notifications"
    )

    if (!secure) {
        val cmd = batterySaverGrantCommand()
        root.addView(txt(10f, p.cDim, "One-time adb grant for secure settings:").apply { setPadding(0, dp(10), 0, dp(2)) })
        root.addView(txt(10f, p.cFg, cmd).apply { setTextIsSelectable(true) })
    }

    val actions = ArrayList<Pair<String, () -> Unit>>()
    if (!secure) {
        actions.add("COPY ADB" to {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("adb", batterySaverGrantCommand()))
            Toast.makeText(this, "adb command copied", Toast.LENGTH_SHORT).show()
        })
    }
    if (sudoPasswordless()) {
        actions.add("PASSWORDLESS: ON" to {
            setSudoPasswordless(false)
            showSudoPanel()
        })
    } else {
        if (live) actions.add("LOCK" to {
            lockSudo()
            showSudoPanel()
        })
        actions.add("PASSWORDLESS: OFF" to {
            requireSudo("Turn on passwordless sudo") {
                setSudoPasswordless(true)
                showSudoPanel()
            }
        })
    }
    actions.add("CLOSE" to { activeDialog?.dismiss() })
    root.addView(trayDialogActions(*actions.toTypedArray()))
    showTrayDialog(root, 520)
}

// ---------------------------------------------------------------- terminal

/** Prints a line into a workspace's terminal from async code (after a prompt). */
internal fun MainActivity.terminalSay(ws: Int, text: String, color: Int? = null) {
    history[ws].add(text + "\n", color ?: termFg())
    refreshOut(ws)
}

/** Privileged commands need unlocked sudo unless the device has no screen lock. */
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
    val first = parts[0].lowercase()
    val rest = parts.getOrElse(1) { "" }.trim()
    when {
        arg.isEmpty() -> {
            terminalSay(ws, "sudo: ${sudoStatusLine()}", p.cDim)
            showSudoPanel()
        }
        first == "-k" -> {
            lockSudo()
            terminalSay(ws, "sudo: locked", p.cGreen)
        }
        first == "-v" -> requireSudo("Refresh sudo") { terminalSay(ws, "sudo: refreshed, ${sudoStatusLine()}", p.cGreen) }
        first == "passwordless" -> {
            if (rest.isEmpty()) {
                terminalSay(ws, "sudo passwordless: ${if (sudoPasswordless()) "on" else "off"}", p.cDim)
            } else if (parseOnOff(rest, sudoPasswordless())) {
                requireSudo("Turn on passwordless sudo") {
                    setSudoPasswordless(true)
                    terminalSay(ws, "sudo passwordless: on (the sudo icon stays red)", p.cAnsi[1])
                }
            } else {
                setSudoPasswordless(false)
                terminalSay(ws, "sudo passwordless: off", p.cGreen)
            }
        }
        else -> requireSudo("sudo $arg") { runCommand(ws, arg) }
    }
}

/** Terminal twins of the tray actions: night, awake, dnd, rec, remind. */
internal fun MainActivity.handleTrayCommand(ws: Int, cmd: String, arg: String) {
    val p = pal
    when (cmd) {
        "night" -> {
            if (!terminalAllowed(ws, "night")) return
            val on = parseOnOff(arg, isNightLightEnabled())
            if (setNightLight(on)) terminalSay(ws, "night light ${if (on) "on" else "off"}", p.cGreen)
            else terminalSay(ws, "night: opened Android's settings (secure settings grant needed for direct switching)", p.cDim)
        }
        "awake" -> {
            if (Settings.System.canWrite(this) && !terminalAllowed(ws, "awake")) return
            val on = parseOnOff(arg, isStayAwakeOn())
            setStayAwake(on)
            terminalSay(ws, "stay awake ${if (on) "on" else "off"}", p.cGreen)
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
            val minutes = arg.trim().toIntOrNull()
            if (arg.trim().lowercase() == "off") {
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
