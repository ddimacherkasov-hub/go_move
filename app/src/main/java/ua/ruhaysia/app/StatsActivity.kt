package ua.ruhaysia.app

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What was done: today, this week, all time, and a chosen period day by day. */
class StatsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout

    // Chosen period, both ends inclusive, as start-of-day times.
    private var from = 0L
    private var to = 0L
    private var allTime = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
        lastWeek()
    }

    override fun onResume() {
        super.onResume()
        fill()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun lastWeek() {
        allTime = false
        to = dayStart(System.currentTimeMillis())
        from = Calendar.getInstance().apply {
            timeInMillis = to
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
    }

    private fun everything() {
        allTime = true
        to = dayStart(System.currentTimeMillis())
        from = dayStart(store.firstDay()?.time ?: to)
    }

    /** Days of the chosen period, newest first. */
    private fun periodDays(): List<Date> {
        val out = ArrayList<Date>()
        val c = Calendar.getInstance().apply { timeInMillis = to }
        var guard = 0
        while (c.timeInMillis >= from && guard < 3660) {
            out.add(c.time)
            c.add(Calendar.DAY_OF_YEAR, -1)
            guard++
        }
        return out
    }

    private fun add(text: String, size: Float, top: Int, accent: Boolean = false, dim: Boolean = false) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(top), 0, 0)
            setLineSpacing(dp(4).toFloat(), 1f)
            if (accent) setTextColor(getColor(R.color.accent))
            if (dim) alpha = 0.7f
        })
    }

    /** One line per exercise that has anything, e.g. "Кроки: 220". */
    private fun amounts(done: Int, get: (Ex) -> Int): String {
        val lines = Ex.values().map { it to get(it) }.filter { it.second > 0 }
            .joinToString("\n") { "${it.first.title}: ${it.second}" }
        return when {
            lines.isNotEmpty() -> lines
            done > 0 -> "Деталі не записано (розминки зі старої версії)"
            else -> "Поки нічого"
        }
    }

    /** "00:12|steps=120,squats=15" becomes "00:12 — кроки 120, присідання 15". */
    private fun sessionText(line: String): String {
        val parts = line.split("|")
        val time = parts.getOrNull(0) ?: ""
        val what = (parts.getOrNull(1) ?: "").split(",").mapNotNull { item ->
            val kv = item.split("=")
            val ex = Ex.values().firstOrNull { it.key == kv.getOrNull(0) }
            val n = kv.getOrNull(1)?.toIntOrNull()
            if (ex != null && n != null) "${ex.title.lowercase()} $n" else null
        }.joinToString(", ")
        return "$time — " + what.ifEmpty { "без деталей" }
    }

    private fun pickDate(start: Long, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        val dialog = DatePickerDialog(this, { _, y, m, d ->
            val picked = Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPick(picked.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun periodButtons() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        fun button(text: String, onClick: () -> Unit) {
            row.addView(
                Button(this).apply {
                    this.text = text
                    textSize = 13f
                    setPadding(dp(4), 0, dp(4), 0)
                    setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        button("7 днів") {
            lastWeek()
            fill()
        }
        button("Увесь час") {
            everything()
            fill()
        }
        button("Обрати дати") {
            // First the start date, then the end date.
            pickDate(from) { a ->
                pickDate(to) { b ->
                    allTime = false
                    from = minOf(a, b)
                    to = maxOf(a, b)
                    fill()
                }
            }
        }
        root.addView(row)
    }

    private fun fill() {
        root.removeAllViews()
        add("Статистика", 28f, 0, accent = true)

        val today = Date()
        add("Сьогодні", 18f, 24)
        add("Розминок: ${store.doneOn(today)} з ${store.dailyGoal}", 15f, 6)
        add(amounts(store.doneOn(today)) { store.amountOn(today, it) }, 15f, 2, dim = true)

        val week = store.weekDays()
        val weekDone = week.sumOf { store.doneOn(it) }
        val (goalDays, activeDays) = store.weekProgress()
        add("Цей тиждень", 18f, 24)
        add("Розминок: $weekDone\nДнів із виконаною ціллю: $goalDays з $activeDays", 15f, 6)
        add(amounts(weekDone) { ex -> week.sumOf { store.amountOn(it, ex) } }, 15f, 2, dim = true)

        add("За весь час", 18f, 24)
        add(
            "Розминок: ${store.totalDone()}\n" +
                "Тижнів із виконаною ціллю: ${store.weeksDone}",
            15f, 6
        )
        add(amounts(store.totalDone()) { store.totalAmount(it) }, 15f, 2, dim = true)

        val short = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        val days = periodDays()
        val periodDone = days.sumOf { store.doneOn(it) }
        add("По днях", 18f, 24)
        periodButtons()
        add(
            (if (allTime) "Увесь час: " else "Період: ") +
                short.format(Date(from)) + " – " + short.format(Date(to)),
            15f, 10, accent = true
        )
        add("Розминок: $periodDone", 15f, 4)
        add(amounts(periodDone) { ex -> days.sumOf { store.amountOn(it, ex) } }, 15f, 2, dim = true)

        // In long periods empty days are hidden to keep the list readable.
        val hideEmpty = days.size > 31
        val fmt = SimpleDateFormat("EEE dd.MM", Locale("uk"))
        val goal = store.dailyGoal
        val todayStart = dayStart(System.currentTimeMillis())
        var shown = 0
        for (d in days) {
            val n = store.doneOn(d)
            if (hideEmpty && n == 0) continue
            shown++
            val mark = if (n >= goal) "  ✓" else ""
            val name = if (dayStart(d.time) == todayStart) "Сьогодні" else fmt.format(d)
            add("$name — розминок: $n$mark", 15f, 14)
            val sessions = store.sessionsOn(d)
            if (sessions.isNotEmpty()) {
                add(sessions.joinToString("\n") { sessionText(it) }, 14f, 2, dim = true)
            }
            if (n > sessions.size) {
                // Warm-ups recorded before the journal existed: show day totals instead.
                val parts = Ex.values().map { it to store.amountOn(d, it) }.filter { it.second > 0 }
                    .joinToString(", ") { "${it.first.title.lowercase()} ${it.second}" }
                val older = n - sessions.size
                add(
                    if (sessions.isEmpty() && parts.isNotEmpty()) "Разом за день: $parts"
                    else "Ще $older без деталей (стара версія)",
                    14f, 2, dim = true
                )
            }
        }
        if (shown == 0) add("У цьому періоді розминок немає", 15f, 14, dim = true)
    }
}
