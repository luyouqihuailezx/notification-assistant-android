package com.android.alftendev.services.utils

import android.service.notification.StatusBarNotification
import com.android.alftendev.BuildConfig
import com.android.alftendev.utils.computables.PackageSettingsCache

object NotiUtils {
    fun shouldDropByPackage(pkg: String): Boolean {
        return PackageSettingsCache.packagesCache[pkg]?.isWhiteList != true
    }

    fun isAutoBlacklistedNotification(sbn: StatusBarNotification): Boolean {
        return sbn.packageName == BuildConfig.APPLICATION_ID
    }
}