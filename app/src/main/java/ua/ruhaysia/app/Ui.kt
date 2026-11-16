package ua.ruhaysia.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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

/** Show the launcher icon that matches the theme: pink background for the pink theme. */
fun Context.applyLauncherIcon() {
    try {
        val pink = isPink()
        val on = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val off = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val green = ComponentName(packageName, "ua.ruhaysia.app.LauncherGreen")
        val rose = ComponentName(packageName, "ua.ruhaysia.app.LauncherPink")
        // Switch the new one on first, so there is always one launcher entry.
        packageManager.setComponentEnabledSetting(
            if (pink) rose else green, on, PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            if (pink) green else rose, off, PackageManager.DONT_KILL_APP
        )
    } catch (ignored: Exception) {
    }
}

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
