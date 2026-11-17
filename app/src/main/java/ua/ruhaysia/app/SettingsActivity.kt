package ua.ruhaysia.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import java.util.Calendar

/** Extra settings: weekdays, smart skip, daily goal, sounds, test reminder, battery. */
class SettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var batteryText: TextView
    private lateinit var batteryButton: Button
    private lateinit var overlayButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themeRes())
        super.onCreate(savedInstanceState)
        store = Store(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        val free = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        batteryText.text =
            if (free) "Обмеження батареї знято, застосунок може працювати у фоні."
            else "Телефон може зупиняти застосунок у фоні, і нагадування не прийдуть."
        val over = Reminder.canPopup(this)
        overlayButton.isEnabled = !over
        overlayButton.alpha = if (over) 0.5f else 1f
        overlayButton.text = if (over) "Дозвіл уже надано" else "Дозволити показ поверх застосунків"
        batteryButton.isEnabled = !free
        batteryButton.alpha = if (free) 0.5f else 1f
        batteryButton.text = if (free) "Обмеження вже знято" else "Зняти обмеження батареї"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, top: Int = 0, dim: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(top), 0, 0)
            if (dim) alpha = 0.7f
        }

    private fun switchRow(parent: LinearLayout, title: String, on: Boolean, set: (Boolean) -> Unit) {
        parent.addView(Switch(this).apply {
            text = title
            textSize = 16f
            setPadding(0, dp(10), 0, dp(10))
            isChecked = on
            setOnCheckedChangeListener { _, v -> set(v) }
        })
    }

    private fun reschedule() {
        if (store.enabled && !store.pending && !Reminder.isPaused(store)) Reminder.scheduleNext(this)
    }

    private fun askSteps() {
        if (!Steps.allowed(this) && Build.VERSION.SDK_INT >= 29) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 2)
        }
    }

    private fun numberRow(parent: LinearLayout, title: String, get: () -> Int, set: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            label(title, 16f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val button = Button(this)
        button.text = get().toString()
        button.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(4))
                setText(get().toString())
                setSelection(text.length)
            }
            val box = LinearLayout(this).apply {
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(input, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Зберегти") { _, _ ->
                    val n = input.text.toString().toIntOrNull()
                    if (n == null || n < 1) {
                        Toast.makeText(this, "Введи число від 1 до 9999", Toast.LENGTH_SHORT).show()
                    } else {
                        set(n)
                        button.text = n.toString()
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
        }
        row.addView(button)
        parent.addView(row)
    }

    private fun askPermissions() {
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        root.addView(label("Налаштування", 28f).apply { setTextColor(accent()) })

        root.addView(label("Звуки", 18f, 24))
        root.addView(label("Голос нагадування грає на гучності будильника, тож його чути навіть " +
            "у беззвучному режимі. Якщо вимкнути, телефон лише вібруватиме.", 14f, 4, true))
        switchRow(root, "Голос нагадування", store.remindSound) { store.remindSound = it }
        switchRow(root, "Голос після вправи й відкладення", store.appSound) { store.appSound = it }

        root.addView(label("Тема", 18f, 24))
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((id, name) in listOf(THEME_GREEN to "Зелена", THEME_PINK to "Рожева")) {
            themeRow.addView(
                Button(this).apply {
                    text = if (store.theme == id) "✓ $name" else name
                    setOnClickListener {
                        if (store.theme != id) {
                            store.theme = id
                            applyLauncherIcon()
                            MoveWidget.refresh(this@SettingsActivity)
                            recreate()
                        }
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        root.addView(themeRow)

        root.addView(label("Дні тижня", 18f, 24))
        root.addView(label("У непозначені дні нагадувань не буде.", 14f, 4, true))
        val daysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dayNames = listOf(
            Calendar.MONDAY to "Пн", Calendar.TUESDAY to "Вт", Calendar.WEDNESDAY to "Ср",
            Calendar.THURSDAY to "Чт", Calendar.FRIDAY to "Пт", Calendar.SATURDAY to "Сб",
            Calendar.SUNDAY to "Нд"
        )
        for ((day, name) in dayNames) {
            val t = ToggleButton(this).apply {
                textOn = name
                textOff = name
                text = name
                isChecked = store.dayOn(day)
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
            }
            t.setOnCheckedChangeListener { _, on ->
                if (!on && dayNames.none { it.first != day && store.dayOn(it.first) }) {
                    t.isChecked = true
                    Toast.makeText(this, "Має лишитися хоча б один день", Toast.LENGTH_SHORT).show()
                } else {
                    store.setDayOn(day, on)
                    reschedule()
                }
            }
            daysRow.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(daysRow)

        root.addView(label("Розумний пропуск", 18f, 24))
        root.addView(label("Якщо за інтервал ти й так походив із телефоном, нагадування " +
            "не прийде, а відлік почнеться заново.", 14f, 4, true))
        switchRow(root, "Пропускати, якщо я рухався", store.smartSkip) {
            store.smartSkip = it
            if (it) askSteps()
        }
        numberRow(root, "Скільки кроків досить", { store.skipSteps }) { store.skipSteps = it }

        root.addView(label("Денна ціль", 18f, 24))
        root.addView(label("День зараховано, якщо зроблено стільки розминок. Тиждень зараховано, " +
            "якщо ціль виконано в кожен увімкнений день.", 14f, 4, true))
        numberRow(root, "Розминок на день", { store.dailyGoal }) { store.dailyGoal = it }

        root.addView(label("Перевірка", 18f, 24))
        root.addView(label("Нагадування прийде через хвилину, незалежно від інтервалу " +
            "та активних годин.", 14f, 4, true))
        root.addView(Button(this).apply {
            text = "Тест: нагадати через 1 хвилину"
            setOnClickListener {
                askPermissions()
                Reminder.scheduleTest(this@SettingsActivity)
                Toast.makeText(
                    this@SettingsActivity,
                    "Заблокуй телефон і почекай хвилину",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        root.addView(label("Поверх інших застосунків", 18f, 24))
        root.addView(label("Коли ти дивишся відео чи сидиш в іншому застосунку, Android " +
            "показує нагадування лише маленькою плашкою. З цим дозволом розминка " +
            "відкриватиметься на весь екран.", 14f, 4, true))
        switchRow(root, "Відкривати розминку на весь екран", store.popupOver) {
            store.popupOver = it
        }
        overlayButton = Button(this).apply {
            setOnClickListener {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Відкрий налаштування телефона → Застосунки → Jaba Mover → " +
                            "Показ поверх інших застосунків",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        root.addView(overlayButton)

        root.addView(label("Робота у фоні", 18f, 24))
        batteryText = label("", 14f, 4, true)
        root.addView(batteryText)
        batteryButton = Button(this).apply {
            setOnClickListener {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        root.addView(batteryButton)

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
        Ui.applyFont(root)
    }
}
