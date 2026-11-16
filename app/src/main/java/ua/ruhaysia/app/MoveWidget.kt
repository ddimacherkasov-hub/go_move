package ua.ruhaysia.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Date

/** Home screen widget: next reminder and today's progress. Tap opens the warm-up. */
class MoveWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        refresh(ctx)
    }

    companion object {
        fun refresh(ctx: Context) {
            try {
                val app = ctx.applicationContext
                val mgr = AppWidgetManager.getInstance(app)
                val ids = mgr.getAppWidgetIds(ComponentName(app, MoveWidget::class.java))
                if (ids.isEmpty()) return
                val s = Store(app)
                val views = RemoteViews(app.packageName, R.layout.widget)
                views.setTextViewText(R.id.w_line1, Reminder.statusText(app))
                views.setTextViewText(
                    R.id.w_line2,
                    "Сьогодні: ${s.doneOn(Date())} з ${s.dailyGoal} · тиждень: " +
                        s.weekProgress().let { "${it.first} з ${it.second} дн." }
                )
                views.setTextColor(R.id.w_line1, app.accent())
                views.setInt(
                    R.id.w_root, "setBackgroundResource",
                    if (app.isPink()) R.drawable.widget_bg_pink else R.drawable.widget_bg
                )
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    PendingIntent.getActivity(
                        app, 2,
                        Intent(app, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                mgr.updateAppWidget(ids, views)
            } catch (ignored: Exception) {
            }
        }
    }
}
