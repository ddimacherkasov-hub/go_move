package ua.ruhaysia.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.ToggleButton

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

/** Tag for the main action button on a screen. */
const val PRIMARY = "primary"

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
        val pink = ctx.isPink()
        if (view is ToggleButton) {
            // Weekday buttons: filled with the accent colour when switched on.
            view.setBackgroundResource(if (pink) R.drawable.toggle_bg_pink else R.drawable.toggle_bg)
            view.stateListAnimator = null
            view.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(0xFF0F1418.toInt(), 0xFFFFFFFF.toInt())
                )
            )
        } else if (view is Button && view !is CompoundButton) {
            // Ordinary buttons: rounded, in the theme's tone; the main action is accent-filled.
            val primary = view.tag == PRIMARY
            view.setBackgroundResource(
                when {
                    primary && pink -> R.drawable.btn_primary_pink
                    primary -> R.drawable.btn_primary
                    pink -> R.drawable.btn_bg_pink
                    else -> R.drawable.btn_bg
                }
            )
            view.stateListAnimator = null
            view.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(
                        0x66FFFFFF,
                        if (primary) 0xFF0F1418.toInt() else 0xFFFFFFFF.toInt()
                    )
                )
            )
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), ctx)
        }
    }
}
