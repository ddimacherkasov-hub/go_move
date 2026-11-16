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

private const val PICK_REMIND = 10
private const val PICK_DONE = 11

/** Settings, status and statistics. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var toggle: Switch
    private lateinit var fixNotifications: Button
    private lateinit var fixFullScreen: Button
    private val soundLabels = HashMap<String, () -> Unit>()

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
                .append(" — розминок: ").append(n).append('\n')
            val parts = Ex.values()
                .map { it to store.amountOn(c.time, it) }
                .filter { it.second > 0 }
                .joinToString(" · ") { "${it.first.title.lowercase()} ${it.second}" }
            if (parts.isNotEmpty()) sb.append("      ").append(parts).append('\n')
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

    private fun soundRow(parent: LinearLayout, kind: String, title: String, none: String, code: Int) {
        val name = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(14), 0, dp(2))
        }
        fun show() {
            name.text = title + ": " + (if (Sounds.has(this, kind)) store.soundName(kind) ?: "свій звук" else none)
        }
        show()
        soundLabels[kind] = { show() }
        parent.addView(name)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Обрати файл"
            setOnClickListener {
                try {
                    startActivityForResult(
                        Intent(Intent.ACTION_GET_CONTENT)
                            .setType("audio/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                        code
                    )
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Не знайшов програму для вибору файлів", Toast.LENGTH_SHORT).show()
                }
            }
        })
        row.addView(Button(this).apply {
            text = "Прослухати"
            setOnClickListener { Sounds.play(this@MainActivity, kind) }
        })
        row.addView(Button(this).apply {
            text = "Скинути"
            setOnClickListener {
                Sounds.stop()
                Sounds.clear(this@MainActivity, kind)
                store.setSoundName(kind, null)
                store.soundVersion = store.soundVersion + 1
                show()
            }
        })
        parent.addView(row)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val kind = when (requestCode) {
            PICK_REMIND -> Sounds.REMIND
            PICK_DONE -> Sounds.DONE
            else -> return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        val name = Sounds.import(this, kind, uri)
        if (name == null) {
            Toast.makeText(this, "Не вдалося прочитати файл", Toast.LENGTH_SHORT).show()
            store.setSoundName(kind, null)
        } else {
            store.setSoundName(kind, name)
            Sounds.play(this, kind)
        }
        store.soundVersion = store.soundVersion + 1
        soundLabels[kind]?.invoke()
    }

    private fun exerciseRow(parent: LinearLayout, ex: Ex) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
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

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ex.goals.map { "$it" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(ex.goals.indexOf(store.goal(ex)).let { if (it < 0) 0 else it })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                store.setGoal(ex, ex.goals[pos])
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

        root.addView(label("Вправи", 18f, 20))
        root.addView(label("Познач, які вправи хочеш робити, і скільки разів. " +
            "Нагадування закриє будь-яка одна з них.", 14f, 4).apply { alpha = 0.7f })
        for (ex in Ex.values()) exerciseRow(root, ex)

        root.addView(label("Звуки", 18f, 20))
        root.addView(label("Можна обрати свій mp3 або інший аудіофайл із телефона.", 14f, 4)
            .apply { alpha = 0.7f })
        soundRow(root, Sounds.REMIND, "Нагадування", "стандартний звук", PICK_REMIND)
        soundRow(root, Sounds.DONE, "Вправу завершено", "без звуку", PICK_DONE)

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

        root.addView(label("Статистика за 7 днів", 18f, 28))
        stats = label("", 15f, 8).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        root.addView(stats)

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
    }
}
