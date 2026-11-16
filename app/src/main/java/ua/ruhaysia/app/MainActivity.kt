package ua.ruhaysia.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Settings, status and statistics. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var toggle: Switch
    private lateinit var fixNotifications: Button
    private lateinit var fixFullScreen: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        status.text = when {
            !store.enabled -> "Нагадування вимкнено"
            store.pending -> "На тебе чекає розминка!"
            else -> "Наступне нагадування о " +
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(store.nextAt))
        }

        val uk = Locale("uk")
        val dayFmt = SimpleDateFormat("EEE dd.MM", uk)
        val sb = StringBuilder()
        val c = Calendar.getInstance()
        for (i in 0 until 7) {
            val n = store.doneOn(c.time)
            sb.append(if (i == 0) "Сьогодні" else dayFmt.format(c.time))
                .append(" — ").append(n).append('\n')
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        stats.text = sb.toString().trimEnd()

        val nm = getSystemService(NotificationManager::class.java)
        fixNotifications.visibility =
            if (nm.areNotificationsEnabled()) View.GONE else View.VISIBLE
        fixFullScreen.visibility =
            if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) View.VISIBLE
            else View.GONE
    }

    private fun askPermissions() {
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        refresh()
    }

    private fun onSettingChanged() {
        if (store.enabled && !store.pending) Reminder.scheduleNext(this)
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, top: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(top), 0, 0)
        }

    private fun picker(
        parent: LinearLayout,
        title: String,
        values: List<Int>,
        show: (Int) -> String,
        current: Int,
        onPick: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(
            TextView(this).apply {
                text = title
                textSize = 16f
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values.map(show))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        var selected = values.indexOf(current).let { if (it < 0) 0 else it }
        spinner.setSelection(selected)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == selected) return
                selected = pos
                onPick(values[pos])
                onSettingChanged()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        row.addView(spinner)
        parent.addView(row)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        root.addView(label("Рухайся", 32f).apply { setTextColor(getColor(R.color.accent)) })
        status = label("", 16f, 6)
        root.addView(status)

        toggle = Switch(this).apply {
            text = "Нагадувати рухатись"
            textSize = 18f
            setPadding(0, dp(24), 0, dp(12))
            isChecked = store.enabled
            setOnCheckedChangeListener { _, on ->
                store.enabled = on
                store.pending = false
                store.snoozeUsed = false
                if (on) {
                    askPermissions()
                    Reminder.scheduleNext(this@MainActivity)
                } else {
                    Reminder.cancelAll(this@MainActivity)
                }
                refresh()
            }
        }
        root.addView(toggle)

        val hours = (0..23).toList()
        val hourText = { h: Int -> String.format(Locale.US, "%02d:00", h) }
        picker(root, "Нагадувати кожні", listOf(30, 45, 60, 90, 120),
            { "$it хв" }, store.intervalMin) { store.intervalMin = it }
        picker(root, "Активні години: з", hours, hourText, store.startHour) {
            store.startHour = it
        }
        picker(root, "Активні години: до", hours, hourText, store.endHour) {
            store.endHour = it
        }
        picker(root, "Кроків для зарахування", listOf(50, 100, 150, 200, 300, 500),
            { "$it" }, store.stepsGoal) { store.stepsGoal = it }
        picker(root, "Присідань для зарахування", listOf(10, 15, 20, 25, 30),
            { "$it" }, store.squatsGoal) { store.squatsGoal = it }

        root.addView(Button(this).apply {
            text = "Розім'ятись зараз"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ChallengeActivity::class.java))
            }
        })

        fixNotifications = Button(this).apply {
            text = "Увімкнути сповіщення (вимкнені)"
            visibility = View.GONE
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
        root.addView(fixNotifications)

        fixFullScreen = Button(this).apply {
            text = "Дозволити показ на весь екран"
            visibility = View.GONE
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        root.addView(fixFullScreen)

        root.addView(label("Розминок за останні 7 днів", 18f, 28))
        stats = label("", 15f, 8).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        root.addView(stats)

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
    }
}
