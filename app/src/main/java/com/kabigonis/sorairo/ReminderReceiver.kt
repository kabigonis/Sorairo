package com.kabigonis.sorairo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Delivers a quiet local Sorairo reminder notification when the countdown ends. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = CHANNEL_ID

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Sorairo reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Quiet countdown reminders from Sorairo"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        val reminderId = intent?.getIntExtra(EXTRA_REMINDER_ID, DEFAULT_NOTIFICATION_ID)
            ?.takeIf { it > 0 } ?: DEFAULT_NOTIFICATION_ID
        val openApp = PendingIntent.getActivity(
            context,
            reminderId,
            Intent(context, MainActivity::class.java).apply {
                data = android.net.Uri.parse("sorairo://reminder/open/$reminderId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sorairo reminder")
                .setContentText("Your countdown has finished.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sorairo reminder")
                .setContentText("Your countdown has finished.")
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        }
        manager.notify(reminderId, notification)
    }

    companion object {
        const val ACTION_FIRE = "com.kabigonis.sorairo.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val CHANNEL_ID = "sorairo_quiet_reminders"
        private const val DEFAULT_NOTIFICATION_ID = 8801
    }
}
