package ua.ruhaysia.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** All settings and state, kept in SharedPreferences. */
class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("ruhaysia", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var intervalMin: Int
        get() = sp.getInt("intervalMin", 60)
        set(v) = sp.edit().putInt("intervalMin", v).apply()

    /** Active hours as minutes from midnight, e.g. 8:30 is 510. */
    var startMinutes: Int
        get() = sp.getInt("startMinutes", sp.getInt("startHour", 9) * 60)
        set(v) = sp.edit().putInt("startMinutes", v).apply()

    var endMinutes: Int
        get() = sp.getInt("endMinutes", sp.getInt("endHour", 21) * 60)
        set(v) = sp.edit().putInt("endMinutes", v).apply()

    /** Voice phrase with the reminder; when off, the phone only vibrates. */
    var remindSound: Boolean
        get() = sp.getBoolean("remindSound", true)
        set(v) = sp.edit().putBoolean("remindSound", v).apply()

    /** Voice phrase after a finished warm-up and after snoozing. */
    var appSound: Boolean
        get() = sp.getBoolean("appSound", true)
        set(v) = sp.edit().putBoolean("appSound", v).apply()

    /** Skip a reminder if the user already walked enough during the interval. */
    var smartSkip: Boolean
        get() = sp.getBoolean("smartSkip", true)
        set(v) = sp.edit().putBoolean("smartSkip", v).apply()

    var skipSteps: Int
        get() = sp.getInt("skipSteps", 200)
        set(v) = sp.edit().putInt("skipSteps", v).apply()

    /** Step counter value when the current sitting interval started; -1 if unknown. */
    var stepBase: Float
        get() = sp.getFloat("stepBase", -1f)
        set(v) = sp.edit().putFloat("stepBase", v).apply()

    var lastSkipAt: Long
        get() = sp.getLong("lastSkipAt", 0L)
        set(v) = sp.edit().putLong("lastSkipAt", v).apply()

    var lastSkipSteps: Int
        get() = sp.getInt("lastSkipSteps", 0)
        set(v) = sp.edit().putInt("lastSkipSteps", v).apply()

    /** day is Calendar.DAY_OF_WEEK: 1 is Sunday, 2 is Monday and so on. */
    fun dayOn(day: Int): Boolean = sp.getBoolean("day_$day", true)

    fun setDayOn(day: Int, v: Boolean) = sp.edit().putBoolean("day_$day", v).apply()

    /** How many warm-ups a day the user aims for. */
    var dailyGoal: Int
        get() = sp.getInt("dailyGoal", 6)
        set(v) = sp.edit().putInt("dailyGoal", v).apply()

    var pauseUntil: Long
        get() = sp.getLong("pauseUntil", 0L)
        set(v) = sp.edit().putLong("pauseUntil", v).apply()

    /** Monday to Sunday of the current week. */
    fun weekDays(): List<Date> {
        val c = Calendar.getInstance()
        var guard = 0
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY && guard < 7) {
            c.add(Calendar.DAY_OF_YEAR, -1)
            guard++
        }
        return (0 until 7).map {
            val d = c.time
            c.add(Calendar.DAY_OF_YEAR, 1)
            d
        }
    }

    private fun isOnDay(d: Date): Boolean =
        dayOn(Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK))

    /** This week: days with the daily goal reached, out of switched-on weekdays. */
    fun weekProgress(): Pair<Int, Int> {
        val goal = dailyGoal
        val days = weekDays().filter { isOnDay(it) }
        return days.count { doneOn(it) >= goal } to days.size
    }

    /**
     * True exactly once per week: today is the last switched-on day of the week and the
     * daily goal has now been reached on every switched-on day.
     */
    fun claimWeekDone(): Boolean {
        val days = weekDays().filter { isOnDay(it) }
        if (days.isEmpty()) return false
        if (KEY.format(days.last()) != KEY.format(Date())) return false
        val goal = dailyGoal
        if (days.any { doneOn(it) < goal }) return false
        val weekKey = KEY.format(weekDays().first())
        if (sp.getString("weekPraised", null) == weekKey) return false
        sp.edit().putString("weekPraised", weekKey).apply()
        return true
    }

    /** How many weeks were completed; also decides which "week" phrase is next. */
    var weeksDone: Int
        get() = sp.getInt("weeksDone", 0)
        set(v) = sp.edit().putInt("weeksDone", v).apply()

    fun totalDone(): Int =
        sp.all.filter { it.key.startsWith("done_") }.values.sumOf { (it as? Int) ?: 0 }

    fun totalAmount(ex: Ex): Int =
        sp.all.filter { it.key.startsWith("ex_") && it.key.endsWith("_" + ex.key) }
            .values.sumOf { (it as? Int) ?: 0 }

    /** The next alarm is a manual test: show it even if reminders are off or it is late. */
    var testOnce: Boolean
        get() = sp.getBoolean("testOnce", false)
        set(v) = sp.edit().putBoolean("testOnce", v).apply()

    fun lastSound(kind: String): Int = sp.getInt("lastSound_$kind", -1)

    fun setLastSound(kind: String, i: Int) = sp.edit().putInt("lastSound_$kind", i).apply()

    fun goal(ex: Ex): Int = sp.getInt(ex.key + "Goal", ex.defaultGoal)

    fun setGoal(ex: Ex, v: Int) = sp.edit().putInt(ex.key + "Goal", v).apply()

    fun isOn(ex: Ex): Boolean = sp.getBoolean("on_" + ex.key, true)

    fun setOn(ex: Ex, v: Boolean) = sp.edit().putBoolean("on_" + ex.key, v).apply()

    /** Exercises the user has chosen; never empty. */
    fun enabledExercises(): List<Ex> =
        Ex.values().filter { isOn(it) }.ifEmpty { listOf(Ex.STEPS) }

    /** A reminder has fired and the warm-up has not been done yet. */
    var pending: Boolean
        get() = sp.getBoolean("pending", false)
        set(v) = sp.edit().putBoolean("pending", v).apply()

    var snoozeUsed: Boolean
        get() = sp.getBoolean("snoozeUsed", false)
        set(v) = sp.edit().putBoolean("snoozeUsed", v).apply()

    var nextAt: Long
        get() = sp.getLong("nextAt", 0L)
        set(v) = sp.edit().putLong("nextAt", v).apply()

    fun doneOn(day: Date): Int = sp.getInt("done_" + KEY.format(day), 0)

    fun addDoneToday() {
        val k = "done_" + KEY.format(Date())
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply()
    }

    /** How much of an exercise was done on that day, over all warm-ups. */
    fun amountOn(day: Date, ex: Ex): Int = sp.getInt("ex_" + KEY.format(day) + "_" + ex.key, 0)

    fun addAmountToday(ex: Ex, n: Int) {
        val k = "ex_" + KEY.format(Date()) + "_" + ex.key
        sp.edit().putInt(k, sp.getInt(k, 0) + n).apply()
    }

    companion object {
        private val KEY = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
