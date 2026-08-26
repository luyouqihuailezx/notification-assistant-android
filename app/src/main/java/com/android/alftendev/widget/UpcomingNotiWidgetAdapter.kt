package com.android.alftendev.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.android.alftendev.R
import com.android.alftendev.models.getParsedNoti
import com.android.alftendev.utils.CustomLog
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.DateUtils.dateFormatter
import java.util.concurrent.Executors

class UpcomingNotiWidgetAdapter(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    companion object {
        val LOGGER = CustomLog("upcoming-widget-adapter")
    }

    private val widgetExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var itemList: List<DBUtils.CalendarEvent> = emptyList()

    @Volatile
    private var loadError: String? = null

    override fun onCreate() {
    }

    override fun onDataSetChanged() {
        val future = widgetExecutor.submit<Pair<List<DBUtils.CalendarEvent>, String?>> {
            try {
                Pair(DBUtils.upcomingEvents(), null)
            } catch (error: Throwable) {
                val detail = "读取最近事项失败：${error.localizedMessage}\n\n完整错误信息：${error.stackTraceToString()}"
                LOGGER.log(detail)
                Pair(emptyList(), detail)
            }
        }

        try {
            val result = future.get()
            itemList = result.first
            loadError = result.second
        } catch (error: Exception) {
            val detail = "读取最近事项失败：${error.localizedMessage}\n\n完整错误信息：${error.stackTraceToString()}"
            LOGGER.log(detail)
            itemList = emptyList()
            loadError = detail
        }
    }

    override fun onDestroy() {
        itemList = emptyList()
        widgetExecutor.shutdown()
    }

    override fun getCount(): Int = if (loadError != null) 1 else itemList.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_list_item)
        val error = loadError
        if (error != null) {
            views.setTextViewText(R.id.item_text, "最近事项读取失败")
            views.setTextViewText(R.id.item_description, error)
            views.setTextViewText(R.id.noti_date, "")
            return views
        }

        val event = itemList[position]
        val packageName = event.notificationPackage()
        val parsedNoti = getParsedNoti(
            title = event.title,
            text = event.body,
            packageName = packageName,
            context
        )
        views.setTextViewText(R.id.item_text, parsedNoti.title)
        views.setTextViewText(R.id.item_description, parsedNoti.text)
        views.setTextViewText(R.id.noti_date, event.reference.resolvedDate?.let(::dateFormatter).orEmpty())
        views.setImageViewIcon(R.id.item_image, parsedNoti.icon)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        if (loadError != null) return -1
        return itemList.getOrNull(position)?.referenceId ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private fun DBUtils.CalendarEvent.notificationPackage(): String {
        return try {
            val notification = com.android.alftendev.MyApplication.notifications.get(notificationId)
            notification?.packageName?.target?.pkg ?: ""
        } catch (error: Throwable) {
            LOGGER.log("读取最近事项应用包名失败，通知 $notificationId：${error.stackTraceToString()}")
            ""
        }
    }
}
