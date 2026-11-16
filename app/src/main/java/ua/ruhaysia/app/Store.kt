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

    var startHour: Int
        get() = sp.getInt("startHour", 9)
        set(v) = sp.edit().putInt("startHour", v).apply()

    var endHour: Int
        get() = sp.getInt("endHour", 21)
        set(v) = sp.edit().putInt("endHour", v).apply()

    var stepsGoal: Int
        get() = sp.getInt("stepsGoal", 100)
        set(v) = sp.edit().putInt("stepsGoal", v).apply()

    var squatsGoal: Int
        get() = sp.getInt("squatsGoal", 15)
        set(v) = sp.edit().putInt("squatsGoal", v).apply()

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

    companion object {
        private val KEY = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
