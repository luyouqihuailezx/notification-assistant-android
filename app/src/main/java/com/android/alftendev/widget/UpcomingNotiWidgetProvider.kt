package com.android.alftendev.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.android.alftendev.R
import com.android.alftendev.utils.CustomLog

class UpcomingNotiWidgetProvider : AppWidgetProvider() {
    companion object {
        private val LOGGER = CustomLog("upcoming-widget")

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val intent = Intent(context, UpcomingNotiWidgetService::class.java)
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setTextViewText(
                R.id.widgetTitleTv,
                context.getString(R.string.upcoming_noti_widget_title)
            )

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, UpcomingNotiWidgetProvider::class.java))
                for (appWidgetId in ids) {
                    updateAppWidget(context, manager, appWidgetId)
                }
            } catch (error: Throwable) {
                LOGGER.log("刷新最近事项小组件失败：${error.stackTraceToString()}")
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        context?.let {
            appWidgetManager?.let {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
