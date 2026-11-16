package ua.ruhaysia.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Which exercises to offer and how many repetitions each needs. */
class ExercisesActivity : Activity() {
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themeRes())
        super.onCreate(savedInstanceState)
        store = Store(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        root.addView(label("Вправи", 28f).apply { setTextColor(accent()) })
        root.addView(label("Познач, які вправи хочеш робити. Натисни на число, щоб змінити " +
            "кількість. Нагадування закриє будь-яка одна з них.", 14f, 8).apply { alpha = 0.7f })
        root.addView(label("", 6f))
        for (ex in Ex.values()) exerciseRow(root, ex)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
        Ui.applyFont(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, top: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(top), 0, 0)
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
}
