package com.android.alftendev.widget

import android.content.Intent
import android.widget.RemoteViewsService

class UpcomingNotiWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return UpcomingNotiWidgetAdapter(this.applicationContext)
    }
}
