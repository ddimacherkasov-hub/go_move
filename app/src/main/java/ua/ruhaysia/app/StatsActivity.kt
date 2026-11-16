package ua.ruhaysia.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Everything that was done: today, this week, 30 days, all time, and day by day. */
class StatsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout

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
    }

    override fun onResume() {
        super.onResume()
        fill()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
    private fun amounts(get: (Ex) -> Int): String {
        val lines = Ex.values().map { it to get(it) }.filter { it.second > 0 }
            .joinToString("\n") { "${it.first.title}: ${it.second}" }
        return if (lines.isEmpty()) "Поки нічого" else lines
    }

    private fun lastDays(n: Int): List<Date> {
        val c = Calendar.getInstance()
        return (0 until n).map {
            val d = c.time
            c.add(Calendar.DAY_OF_YEAR, -1)
            d
        }
    }

    private fun fill() {
        root.removeAllViews()
        add("Статистика", 28f, 0, accent = true)

        val today = Date()
        add("Сьогодні", 18f, 24)
        add("Розминок: ${store.doneOn(today)} з ${store.dailyGoal}", 15f, 6)
        add(amounts { store.amountOn(today, it) }, 15f, 2, dim = true)

        val week = store.weekDays()
        val (goalDays, activeDays) = store.weekProgress()
        add("Цей тиждень", 18f, 24)
        add(
            "Розминок: ${week.sumOf { store.doneOn(it) }}\n" +
                "Днів із виконаною ціллю: $goalDays з $activeDays",
            15f, 6
        )
        add(amounts { ex -> week.sumOf { store.amountOn(it, ex) } }, 15f, 2, dim = true)

        val month = lastDays(30)
        add("Останні 30 днів", 18f, 24)
        add("Розминок: ${month.sumOf { store.doneOn(it) }}", 15f, 6)
        add(amounts { ex -> month.sumOf { store.amountOn(it, ex) } }, 15f, 2, dim = true)

        add("За весь час", 18f, 24)
        add(
            "Розминок: ${store.totalDone()}\n" +
                "Тижнів із виконаною ціллю: ${store.weeksDone}",
            15f, 6
        )
        add(amounts { store.totalAmount(it) }, 15f, 2, dim = true)

        add("По днях (14 днів)", 18f, 24)
        val fmt = SimpleDateFormat("EEE dd.MM", Locale("uk"))
        val goal = store.dailyGoal
        lastDays(14).forEachIndexed { i, d ->
            val n = store.doneOn(d)
            val mark = if (n >= goal) "  ✓" else ""
            add((if (i == 0) "Сьогодні" else fmt.format(d)) + " — розминок: $n$mark", 15f, 10)
            val parts = Ex.values().map { it to store.amountOn(d, it) }.filter { it.second > 0 }
                .joinToString(" · ") { "${it.first.title.lowercase()} ${it.second}" }
            if (parts.isNotEmpty()) add(parts, 14f, 0, dim = true)
        }
    }
}
