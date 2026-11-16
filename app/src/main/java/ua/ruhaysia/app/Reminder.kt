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
import android.net.Uri
import android.os.Build
import java.util.Calendar

/** Scheduling of reminders and the "time to move" notification. */
object Reminder {
    const val ACTION_REMIND = "ua.ruhaysia.app.REMIND"
    private const val CHANNEL_PREFIX = "mv3_"
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

    /** start and end are minutes from midnight; the window may cross midnight. */
    fun inWindow(t: Long, start: Int, end: Int): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return when {
            start == end -> true
            start < end -> m in start until end
            else -> m >= start || m < end
        }
    }

    /** If t is outside active hours, move it to the next start of active hours. */
    fun adjustToWindow(t: Long, start: Int, end: Int): Long {
        if (inWindow(t, start, end)) return t
        val c = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, start / 60)
            set(Calendar.MINUTE, start % 60)
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
            System.currentTimeMillis() + s.intervalMin * 60_000L, s.startMinutes, s.endMinutes
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
        if (!inWindow(System.currentTimeMillis(), s.startMinutes, s.endMinutes)) {
            s.pending = false
            s.snoozeUsed = false
            hideNotification(ctx)
            scheduleNext(ctx)
            return
        }
        val nag = s.pending
        s.pending = true
        showNotification(ctx, nag)
        scheduleNag(ctx)
    }

    /** After reboot or app update alarms are lost, so set them again. */
    fun restore(ctx: Context) {
        val s = Store(ctx)
        if (!s.enabled) return
        if (!s.pending && s.nextAt > System.currentTimeMillis()) scheduleAt(ctx, s.nextAt)
        else fire(ctx)
    }

    private fun channel(id: String, name: String, sound: Uri?): NotificationChannel =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сигнал, що час встати з-за комп'ютера"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

    /**
     * A channel's sound cannot be changed later, so every phrase has its own channel,
     * plus one silent channel that only vibrates.
     */
    private fun ensureChannels(ctx: Context, nm: NotificationManager) {
        for (old in nm.notificationChannels) {
            if (!old.id.startsWith(CHANNEL_PREFIX)) nm.deleteNotificationChannel(old.id)
        }
        nm.createNotificationChannel(channel(CHANNEL_PREFIX + "vibro", "Тільки вібрація", null))
        Sounds.REMIND.forEachIndexed { i, n ->
            nm.createNotificationChannel(
                channel(CHANNEL_PREFIX + n, "Нагадування, фраза ${i + 1}", Sounds.uri(ctx, n))
            )
        }
        Sounds.NAG.forEachIndexed { i, n ->
            nm.createNotificationChannel(
                channel(CHANNEL_PREFIX + n, "Повторне нагадування, фраза ${i + 1}", Sounds.uri(ctx, n))
            )
        }
    }

    private fun showNotification(ctx: Context, nag: Boolean) {
        val s = Store(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        ensureChannels(ctx, nm)
        val channelId = CHANNEL_PREFIX + when {
            !s.remindSound -> "vibro"
            nag -> Sounds.NAG[Sounds.pick(ctx, "nag", Sounds.NAG.size)]
            else -> Sounds.REMIND[Sounds.pick(ctx, "remind", Sounds.REMIND.size)]
        }

        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, ChallengeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ctx.getColor(R.color.accent))
            .setContentTitle(if (nag) "Досі сидиш? Час рухатись!" else "Час рухатись!")
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
        // Remove the old one first so the new phrase is played on a repeated reminder.
        nm.cancel(NOTIF_ID)
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
