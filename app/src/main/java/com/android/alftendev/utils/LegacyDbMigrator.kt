package com.android.alftendev.utils

import android.content.Context
import com.android.alftendev.MyApplication
import com.android.alftendev.models.Notifications
import com.android.alftendev.models.PackageName
import com.android.alftendev.models.PackageName_
import com.android.alftendev.utils.computables.PackageSettingsCache
import io.objectbox.query.QueryBuilder
import java.io.File

object LegacyDbMigrator {
    private data class PackageSnapshot(
        val pkg: String,
        val name: String?,
        val isBlackList: Boolean,
        val isWhiteList: Boolean,
        val isChat: Boolean
    )

    private data class NotificationSnapshot(
        val title: String,
        val time: java.util.Date,
        val text: String,
        val bigText: String?,
        val conversationTitle: String?,
        val infoText: String?,
        val peopleList: String?,
        val titleBig: String?,
        val isDeleted: Boolean,
        val packageName: String
    )

    fun migrate(context: Context) {
        val packages = MyApplication.packageNames.all.map(::snapshotPackage)
        val notifications = MyApplication.notifications.all.mapNotNull { snapshotNotification(it) }

        MyApplication.database.close()
        val store = File(context.filesDir, "objectbox")
        if (store.exists() && !store.deleteRecursively()) {
            error("无法删除旧数据库目录：${store.absolutePath}")
        }

        MyApplication.initializeDatabase(context)
        packages.forEach { item ->
            MyApplication.packageNames.put(
                PackageName(
                    name = item.name,
                    pkg = item.pkg,
                    isBlackList = item.isBlackList,
                    isWhiteList = item.isWhiteList,
                    isChat = item.isChat
                )
            )
        }
        notifications.forEach { item ->
            val packageName = MyApplication.packageNames.query()
                .equal(PackageName_.pkg, item.packageName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().findFirst() ?: return@forEach
            val notification = Notifications(
                title = NotificationTitleCleaner.removeUnreadCountSuffix(item.title),
                time = item.time,
                text = item.text,
                bigText = item.bigText,
                conversationTitle = item.conversationTitle,
                infoText = item.infoText,
                peopleList = item.peopleList,
                titleBig = item.titleBig,
                isDeleted = item.isDeleted
            )
            notification.packageName.target = packageName
            MyApplication.notifications.put(notification)
        }

        if (!File(context.filesDir, "notification_config").deleteRecursively()) {
            error("无法清理旧版配置目录：${File(context.filesDir, "notification_config").absolutePath}")
        }
        context.getSharedPreferences("NotInfo", Context.MODE_PRIVATE).edit().clear().commit()
        PackageSettingsCache.initCache()
    }

    private fun snapshotPackage(item: PackageName) = PackageSnapshot(
        item.pkg, item.name, item.isBlackList, item.isWhiteList, item.isChat
    )

    private fun snapshotNotification(item: Notifications): NotificationSnapshot? {
        val pkg = item.packageName.target?.pkg ?: return null
        return NotificationSnapshot(
            item.title, item.time, item.text, item.bigText, item.conversationTitle,
            item.infoText, item.peopleList, item.titleBig, item.isDeleted, pkg
        )
    }
}
