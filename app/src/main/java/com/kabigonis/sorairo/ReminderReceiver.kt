package com.kabigonis.sorairo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/** Delivers a locally scheduled Sorairo reminder using the selected notification tone. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sound = intent?.getStringExtra(EXTRA_SOUND) ?: SOUND_ALARM
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = channelId(sound)

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Sorairo reminders · ${soundLabel(sound)}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer reminders from Sorairo"
                enableVibration(true)
                setSound(soundUri(sound), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            context,
            8801,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sorairo reminder")
                .setContentText("Your countdown has finished.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sorairo reminder")
                .setContentText("Your countdown has finished.")
                .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_FIRE = "com.kabigonis.sorairo.REMINDER_FIRE"
        const val EXTRA_SOUND = "reminder_sound"
        const val SOUND_ALARM = "alarm"
        const val SOUND_CHIME = "chime"
        const val SOUND_SILENT = "silent"
        private const val NOTIFICATION_ID = 8801

        fun channelId(sound: String) = "sorairo_reminder_$sound"

        private fun soundLabel(sound: String) = when (sound) {
            SOUND_CHIME -> "Gentle chime"
            SOUND_SILENT -> "Silent"
            else -> "Alarm tone"
        }

        private fun soundUri(sound: String) = when (sound) {
            SOUND_CHIME -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            SOUND_SILENT -> null
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
}
