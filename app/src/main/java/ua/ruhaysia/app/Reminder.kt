package ua.ruhaysia.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One-off reading of the phone's step counter (total steps since the phone was turned on). */
object Steps {
    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /** Calls back once on the main thread with the value, or null if it cannot be read. */
    fun read(ctx: Context, cb: (Float?) -> Unit) {
        val app = ctx.applicationContext
        val sm = app.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sm == null || sensor == null || !allowed(app)) {
            cb(null)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(e: SensorEvent) {
                if (finished) return
                finished = true
                sm.unregisterListener(this)
                cb(e.values[0])
            }
        }
        if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST, handler)) {
            cb(null)
            return
        }
        handler.postDelayed({
            if (!finished) {
                finished = true
                sm.unregisterListener(listener)
                cb(null)
            }
        }, 2500L)
    }
}

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

    /** True if reminders are wanted at this moment: an enabled weekday, inside active hours. */
    fun isActive(s: Store, t: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        if (!s.dayOn(c.get(Calendar.DAY_OF_WEEK))) return false
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val start = s.startMinutes
        val end = s.endMinutes
        return when {
            start == end -> true
            start < end -> m in start until end
            else -> m >= start || m < end
        }
    }

    /** t itself if it is an active moment, otherwise the next start of active hours. */
    fun nextActive(s: Store, t: Long): Long {
        if (isActive(s, t)) return t
        val start = s.startMinutes
        val c = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, start / 60)
            set(Calendar.MINUTE, start % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while ((c.timeInMillis <= t || !s.dayOn(c.get(Calendar.DAY_OF_WEEK))) && guard < 10) {
            c.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        }
        return c.timeInMillis
    }

    /**
     * Start a fresh sitting interval from now. The step counter value at this moment is
     * remembered, so later we can tell whether the user walked during the interval.
     */
    fun scheduleNext(ctx: Context, knownSteps: Float? = null) {
        val s = Store(ctx)
        val at = nextActive(s, System.currentTimeMillis() + s.intervalMin * 60_000L)
        rememberSteps(ctx, knownSteps)
        scheduleAt(ctx, at)
    }

    private fun rememberSteps(ctx: Context, knownSteps: Float?) {
        val s = Store(ctx)
        if (knownSteps != null) {
            s.stepBase = knownSteps
        } else {
            s.stepBase = -1f
            val app = ctx.applicationContext
            Steps.read(app) { v -> if (v != null) Store(app).stepBase = v }
        }
    }

    fun scheduleAt(ctx: Context, at: Long) {
        Store(ctx).nextAt = at
        setAlarm(ctx, at)
        MoveWidget.refresh(ctx)
    }

    /** Remind again soon; used while the warm-up is still not done. */
    fun scheduleNag(ctx: Context, minutes: Int = NAG_MINUTES) {
        setAlarm(ctx, System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Manual check: a reminder in one minute, whatever the settings say. */
    fun scheduleTest(ctx: Context) {
        val s = Store(ctx)
        s.testOnce = true
        s.pending = false
        s.snoozeUsed = false
        hideNotification(ctx)
        scheduleAt(ctx, System.currentTimeMillis() + 60_000L)
    }

    /**
     * No reminders until [until]. Also drops a reminder that is waiting or was snoozed.
     * With [freshInterval] the first reminder comes one interval after the pause ends,
     * otherwise right when it ends.
     */
    fun pause(ctx: Context, until: Long, freshInterval: Boolean) {
        val s = Store(ctx)
        s.pending = false
        s.snoozeUsed = false
        s.testOnce = false
        hideNotification(ctx)
        s.pauseUntil = until
        var at = nextActive(s, until)
        if (freshInterval) at = nextActive(s, at + s.intervalMin * 60_000L)
        rememberSteps(ctx, null)
        scheduleAt(ctx, at)
    }

    fun resume(ctx: Context) {
        Store(ctx).pauseUntil = 0L
        scheduleNext(ctx)
    }

    fun isPaused(s: Store): Boolean = System.currentTimeMillis() < s.pauseUntil

    fun cancelAll(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        hideNotification(ctx)
        MoveWidget.refresh(ctx)
    }

    fun hideNotification(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    /** Short status line for the main screen and the widget. */
    fun statusText(ctx: Context): String {
        val s = Store(ctx)
        val now = System.currentTimeMillis()
        return when {
            !s.enabled -> "Нагадування вимкнено"
            now < s.pauseUntil -> "Пауза до " + timeText(s.pauseUntil)
            s.pending -> "На тебе чекає розминка!"
            else -> "Наступне нагадування: " + timeText(s.nextAt)
        }
    }

    private fun timeText(t: Long): String {
        val a = Calendar.getInstance()
        val b = Calendar.getInstance().apply { timeInMillis = t }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "EEE HH:mm"
        return SimpleDateFormat(pattern, Locale("uk")).format(Date(t))
    }

    /**
     * The alarm went off. Decides whether to show the reminder, skip it, or move it.
     * [then] is called when everything is finished (the step check takes a moment).
     */
    fun fire(ctx: Context, then: () -> Unit = {}) {
        val s = Store(ctx)
        val now = System.currentTimeMillis()
        val test = s.testOnce
        if (test) s.testOnce = false
        if (!s.enabled && !test) {
            then()
            return
        }
        if (!test && now < s.pauseUntil) {
            scheduleAt(ctx, nextActive(s, s.pauseUntil))
            then()
            return
        }
        if (!test && !isActive(s, now)) {
            s.pending = false
            s.snoozeUsed = false
            hideNotification(ctx)
            scheduleNext(ctx)
            then()
            return
        }
        val nag = s.pending
        val base = s.stepBase
        if (!test && !nag && s.smartSkip && base >= 0f) {
            Steps.read(ctx) { cur ->
                if (cur != null && cur >= base && (cur - base) >= s.skipSteps) {
                    // Walked enough during the interval: no reminder, start a new interval.
                    s.lastSkipAt = now
                    s.lastSkipSteps = (cur - base).toInt()
                    scheduleNext(ctx, cur)
                } else {
                    remind(ctx, false)
                }
                then()
            }
            return
        }
        remind(ctx, nag)
        then()
    }

    private fun remind(ctx: Context, nag: Boolean) {
        Store(ctx).pending = true
        showNotification(ctx, nag)
        scheduleNag(ctx)
        MoveWidget.refresh(ctx)
    }

    /** After reboot or app update alarms are lost, so set them again. */
    fun restore(ctx: Context, then: () -> Unit = {}) {
        val s = Store(ctx)
        if (!s.enabled) {
            then()
            return
        }
        if (!s.pending && s.nextAt > System.currentTimeMillis()) {
            scheduleAt(ctx, s.nextAt)
            then()
        } else {
            fire(ctx, then)
        }
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
        val app = ctx.applicationContext
        when (intent.action) {
            Reminder.ACTION_REMIND -> {
                val pending = goAsync()
                Reminder.fire(app) { pending.finish() }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                Reminder.restore(app) { pending.finish() }
            }
        }
    }
}
