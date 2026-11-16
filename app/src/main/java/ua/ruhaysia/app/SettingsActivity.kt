package ua.ruhaysia.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Extra settings: sounds, test reminder, battery restrictions. */
class SettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var batteryText: TextView
    private lateinit var batteryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
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
        batteryButton.isEnabled = !free
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
        root.addView(label("Налаштування", 28f).apply { setTextColor(getColor(R.color.accent)) })

        root.addView(label("Звуки", 18f, 24))
        root.addView(label("Якщо вимкнути голос нагадування, телефон лише вібруватиме.", 14f, 4, true))
        switchRow(root, "Голос нагадування", store.remindSound) { store.remindSound = it }
        switchRow(root, "Голос після вправи й відкладення", store.appSound) { store.appSound = it }

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
    }
}
