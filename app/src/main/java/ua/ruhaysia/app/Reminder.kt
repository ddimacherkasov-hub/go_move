package ua.ruhaysia.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import java.util.Calendar

/** Scheduling of reminders and the "time to move" notification. */
object Reminder {
    const val ACTION_REMIND = "ua.ruhaysia.app.REMIND"
    private const val CHANNEL_PREFIX = "move_"
    private const val NOTIF_ID = 1
    private const val NAG_MINUTES = 5

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAlarm(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun inWindow(t: Long, start: Int, end: Int): Boolean {
        val h = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
        return when {
            start == end -> true
            start < end -> h in start until end
            else -> h >= start || h < end
        }
    }

    /** If t is outside active hours, move it to the next start of active hours. */
    fun adjustToWindow(t: Long, start: Int, end: Int): Long {
        if (inWindow(t, start, end)) return t
        val c = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, start)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= t) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Start a fresh sitting interval from now. */
    fun scheduleNext(ctx: Context) {
        val s = Store(ctx)
        val at = adjustToWindow(
            System.currentTimeMillis() + s.intervalMin * 60_000L, s.startHour, s.endHour
        )
        s.nextAt = at
        setAlarm(ctx, at)
    }

    fun scheduleAt(ctx: Context, at: Long) {
        Store(ctx).nextAt = at
        setAlarm(ctx, at)
    }

    /** Remind again soon; used while the warm-up is still not done. */
    fun scheduleNag(ctx: Context, minutes: Int = NAG_MINUTES) {
        setAlarm(ctx, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun cancelAll(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        hideNotification(ctx)
    }

    fun hideNotification(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    /** The alarm went off: show the reminder, or skip it outside active hours. */
    fun fire(ctx: Context) {
        val s = Store(ctx)
        if (!s.enabled) return
        if (!inWindow(System.currentTimeMillis(), s.startHour, s.endHour)) {
            s.pending = false
            s.snoozeUsed = false
            hideNotification(ctx)
            scheduleNext(ctx)
            return
        }
        s.pending = true
        showNotification(ctx)
        scheduleNag(ctx)
    }

    /** After reboot or app update alarms are lost, so set them again. */
    fun restore(ctx: Context) {
        val s = Store(ctx)
        if (!s.enabled) return
        if (!s.pending && s.nextAt > System.currentTimeMillis()) scheduleAt(ctx, s.nextAt)
        else fire(ctx)
    }

    private fun showNotification(ctx: Context) {
        val s = Store(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channelId = CHANNEL_PREFIX + "s" + s.soundVersion
        for (old in nm.notificationChannels) {
            if (old.id.startsWith(CHANNEL_PREFIX) && old.id != channelId) {
                nm.deleteNotificationChannel(old.id)
            }
        }
        val ch = NotificationChannel(
            channelId, "Нагадування рухатись", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            if (Sounds.has(ctx, Sounds.REMIND)) {
                val uri = Sounds.uri(Sounds.REMIND, s.soundVersion)
                try {
                    ctx.grantUriPermission(
                        "com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {
                }
                setSound(
                    uri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            description = "Сигнал, що час встати з-за комп'ютера"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)

        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, ChallengeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ctx.getColor(R.color.accent))
            .setContentTitle("Час рухатись!")
            .setContentText(
                "На вибір: " + s.enabledExercises().joinToString(", ") { it.title.lowercase() }
            )
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
        nm.notify(NOTIF_ID, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Reminder.ACTION_REMIND -> Reminder.fire(ctx)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Reminder.restore(ctx)
        }
    }
}
