package ua.ruhaysia.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Settings, status and statistics. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var fixNotifications: Button
    private lateinit var fixFullScreen: Button
    private lateinit var pauseButton: Button
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ticker.removeCallbacks(tick)
        ticker.postDelayed(tick, 1000L)
    }

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            // Live countdown only while a reminder is snoozed.
            if (Reminder.isSnoozed(store) || wasSnoozed) refresh(false)
            ticker.postDelayed(this, 1000L)
        }
    }
    private var wasSnoozed = false

    override fun onPause() {
        ticker.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh(withWidget: Boolean = true) {
        val now = System.currentTimeMillis()
        val snoozed = Reminder.isSnoozed(store)
        wasSnoozed = snoozed
        val first = if (snoozed) {
            val left = ((store.snoozeUntil - now) / 1000L).coerceAtLeast(0L)
            String.format(Locale.US, "Відкладено, лишилось %d:%02d", left / 60, left % 60)
        } else {
            Reminder.statusText(this)
        }
        startButton.text = if (snoozed) "Почати розминку зараз" else "Розім'ятись зараз"
        val sbStatus = StringBuilder(first)
        sbStatus.append("\nСьогодні: ").append(store.doneOn(Date())).append(" з ")
            .append(store.dailyGoal)
        val (goalDays, activeDays) = store.weekProgress()
        sbStatus.append(" · тиждень: ").append(goalDays).append(" з ").append(activeDays).append(" дн.")
        if (store.lastSkipAt > 0L && now - store.lastSkipAt < 3 * 3600_000L) {
            sbStatus.append("\nОстаннє нагадування пропущено о ")
                .append(SimpleDateFormat("HH:mm", Locale.US).format(Date(store.lastSkipAt)))
                .append(": ти й так пройшов ").append(store.lastSkipSteps).append(" кроків")
        }
        status.text = sbStatus.toString()
        pauseButton.text = if (Reminder.isPaused(store)) "Зняти паузу" else "Пауза"
        pauseButton.isEnabled = store.enabled
        if (withWidget) MoveWidget.refresh(this)

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

    private fun askPause() {
        val names = arrayOf("30 хвилин", "1 година", "2 години", "До завтра")
        AlertDialog.Builder(this)
            .setTitle("Пауза: не нагадувати")
            .setItems(names) { _, which ->
                val now = System.currentTimeMillis()
                if (which == 3) {
                    val c = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    Reminder.pause(this, c.timeInMillis, true)
                } else {
                    val minutes = intArrayOf(30, 60, 120)[which]
                    Reminder.pause(this, now + minutes * 60_000L, false)
                }
                refresh()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun onSettingChanged() {
        if (store.enabled && !store.pending && !Reminder.isPaused(store)) Reminder.scheduleNext(this)
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

    private fun timeRow(parent: LinearLayout, title: String, get: () -> Int, set: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(
            TextView(this).apply {
                text = title
                textSize = 16f
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val fmt = { m: Int -> String.format(Locale.US, "%02d:%02d", m / 60, m % 60) }
        val button = Button(this)
        button.text = fmt(get())
        button.setOnClickListener {
            val now = get()
            TimePickerDialog(this, { _, h, min ->
                set(h * 60 + min)
                button.text = fmt(get())
                onSettingChanged()
            }, now / 60, now % 60, true).show()
        }
        row.addView(button)
        parent.addView(row)
    }

    private fun exerciseRow(parent: LinearLayout, ex: Ex) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val box = CheckBox(this).apply {
            text = ex.title
            textSize = 16f
            isChecked = store.isOn(ex)
        }
        box.setOnCheckedChangeListener { _, on ->
            if (!on && Ex.values().none { it != ex && store.isOn(it) }) {
                box.isChecked = true
                Toast.makeText(this, "Має лишитися хоча б одна вправа", Toast.LENGTH_SHORT).show()
            } else {
                store.setOn(ex, on)
            }
        }
        row.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val button = Button(this)
        button.text = store.goal(ex).toString()
        button.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(4))
                setText(store.goal(ex).toString())
                setSelection(text.length)
            }
            val box2 = LinearLayout(this).apply {
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(input, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            AlertDialog.Builder(this)
                .setTitle(ex.title + ": скільки разів")
                .setView(box2)
                .setPositiveButton("Зберегти") { _, _ ->
                    val n = input.text.toString().toIntOrNull()
                    if (n == null || n < 1) {
                        Toast.makeText(this, "Введи число від 1 до 9999", Toast.LENGTH_SHORT).show()
                    } else {
                        store.setGoal(ex, n)
                        button.text = n.toString()
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
        }
        row.addView(button)
        parent.addView(row)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        root.addView(label("Рухайся", 32f).apply { setTextColor(getColor(R.color.accent)) })
        status = label("", 16f, 6).apply { setLineSpacing(dp(3).toFloat(), 1f) }
        root.addView(status)

        toggle = Switch(this).apply {
            text = "Нагадувати рухатись"
            textSize = 18f
            setPadding(0, dp(24), 0, dp(12))
            isChecked = store.enabled
            setOnCheckedChangeListener { _, on ->
                store.enabled = on
                store.pauseUntil = 0L
                store.snoozeUntil = 0L
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

        picker(root, "Нагадувати кожні", listOf(30, 45, 60, 90, 120),
            { "$it хв" }, store.intervalMin) { store.intervalMin = it }
        timeRow(root, "Нагадувати з", { store.startMinutes }) { store.startMinutes = it }
        timeRow(root, "Нагадувати до", { store.endMinutes }) { store.endMinutes = it }

        root.addView(label("Вправи", 18f, 20))
        root.addView(label("Познач, які вправи хочеш робити. Натисни на число, щоб змінити кількість. " +
            "Нагадування закриє будь-яка одна з них.", 14f, 4).apply { alpha = 0.7f })
        for (ex in Ex.values()) exerciseRow(root, ex)

        startButton = Button(this).apply {
            text = "Розім'ятись зараз"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ChallengeActivity::class.java))
            }
        }
        root.addView(startButton)

        pauseButton = Button(this).apply {
            text = "Пауза"
            setOnClickListener {
                if (Reminder.isPaused(store)) {
                    Reminder.resume(this@MainActivity)
                    refresh()
                } else {
                    askPause()
                }
            }
        }
        root.addView(pauseButton)

        root.addView(Button(this).apply {
            text = "Статистика"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, StatsActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = "Налаштування"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
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

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
    }
}
