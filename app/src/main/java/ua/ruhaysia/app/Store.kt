package ua.ruhaysia.app

import android.content.Context
import java.text.SimpleDateFormat
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
