package ua.ruhaysia.app

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

/** Colour themes and the app font. */
const val THEME_GREEN = "green"
const val THEME_PINK = "pink"

fun Context.isPink(): Boolean = Store(this).theme == THEME_PINK

fun Context.themeRes(): Int = if (isPink()) R.style.AppTheme_Pink else R.style.AppTheme

/** Accent colour of the chosen theme. */
fun Context.accent(): Int = getColor(if (isPink()) R.color.accent_pink else R.color.accent)

object Ui {
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var bold: Typeface? = null

    /** Rubik everywhere: bold for big titles, medium for buttons and switches. */
    fun applyFont(view: View) {
        val ctx = view.context
        try {
            if (regular == null) {
                regular = ctx.resources.getFont(R.font.rubik_regular)
                medium = ctx.resources.getFont(R.font.rubik_medium)
                bold = ctx.resources.getFont(R.font.rubik_bold)
            }
        } catch (e: Exception) {
            return
        }
        walk(view, ctx)
    }

    private fun walk(view: View, ctx: Context) {
        if (view is TextView) {
            val sp = view.textSize / TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 1f, ctx.resources.displayMetrics
            )
            view.typeface = when {
                sp >= 26f -> bold
                view is Button || sp >= 17.5f -> medium
                else -> regular
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), ctx)
        }
    }
}
