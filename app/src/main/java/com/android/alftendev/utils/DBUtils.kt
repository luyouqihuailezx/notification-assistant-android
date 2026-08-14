package com.android.alftendev.utils

import android.content.Context
import com.android.alftendev.MyApplication
import com.android.alftendev.MyApplication.Companion.notifications
import com.android.alftendev.MyApplication.Companion.packageNames
import com.android.alftendev.R
import com.android.alftendev.models.Notifications
import com.android.alftendev.models.Notifications_
import com.android.alftendev.models.PackageName
import com.android.alftendev.models.PackageName_
import com.android.alftendev.utils.Utils.isAppInstalled
import com.android.alftendev.utils.computables.AppListCache.getInstalledApps
import com.android.alftendev.utils.computables.AppListCache.getInstalledUserApps
import io.objectbox.query.LazyList
import io.objectbox.query.QueryBuilder
import java.util.Date

object DBUtils {
    val LOGGER = CustomLog("noti-database-utils")

    fun searchDeletedNot(pkgName: String, date: Long, title: String, text: String): Notifications? {
        return notifications
            .query()
            .equal(Notifications_.time, date)
            .equal(Notifications_.title, title, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .notEqual(Notifications_.text, text, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .find()
            .firstOrNull { it.packageName.target.pkg == pkgName }
    }

    fun searchOneNot(pkgName: String, title: String, date: Long, text: String): Notifications? {
        return notifications
            .query()
            .equal(Notifications_.title, title, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(Notifications_.time, date)
            .equal(Notifications_.text, text, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .find()
            .firstOrNull { it.packageName.target.pkg == pkgName }
    }

    fun deletedNotification(): LazyList<Notifications> {
        return notifications
            .query()
            .equal(Notifications_.isDeleted, true)
            .orderDesc(Notifications_.time)
            .build()
            .findLazy()
    }

    fun deletedNotificationForWidget(): List<Notifications> {
        return notifications
            .query()
            .equal(Notifications_.isDeleted, true)
            .orderDesc(Notifications_.time)
            .build()
            .find(0, 50)
    }

    fun getChatNotifications(): List<Notifications> {
        return notifications
            .query()
            .orderDesc(Notifications_.time)
            .build()
            .findLazy()
            .filter { it.packageName.target.isChat }
    }

    fun getChatNotificationsForWidget(): List<Notifications> {
        return notifications
            .query()
            .orderDesc(Notifications_.time)
            .build()
            .find(0, 65)
            .filter { it.packageName.target.isChat }
    }

    fun notificationWithoutChat(): List<Notifications> {
        val builder = notifications.query().orderDesc(Notifications_.time)

        builder.link(Notifications_.packageName)
            .equal(PackageName_.isChat, false)

        return builder.build().findLazy()
    }

    fun notificationWithoutChatWithSearch(string: String): List<Notifications> {
        val builder = notifications
            .query()
            .contains(Notifications_.title, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .or()
            .contains(Notifications_.text, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .orderDesc(Notifications_.time)

        builder.link(Notifications_.packageName)
            .equal(PackageName_.isChat, false)

        return builder.build().findLazy()
    }

    fun advancedNotificationSearch(
        string: String,
        pkgName: String,
        isDeleted: Boolean
    ): List<Notifications> {
        if (pkgName == MyApplication.defaultSwValue || pkgName.isBlank()) {
            return notifications
                .query()
                .equal(Notifications_.isDeleted, isDeleted)
                .contains(Notifications_.title, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .or()
                .contains(Notifications_.text, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .orderDesc(Notifications_.time)
                .build()
                .find()
        }

        return notifications
            .query()
            .equal(Notifications_.isDeleted, isDeleted)
            .contains(Notifications_.title, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .or()
            .contains(Notifications_.text, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .orderDesc(Notifications_.time)
            .build()
            .find()
            .filter { it.packageName.target.pkg == pkgName }
    }

    fun advancedNotificationSearchWithoutText(
        pkgName: String,
        isDeleted: Boolean
    ): List<Notifications> {
        if (pkgName == MyApplication.defaultSwValue || pkgName.isBlank()) {
            return notifications
                .query()
                .equal(Notifications_.isDeleted, isDeleted)
                .orderDesc(Notifications_.time)
                .build()
                .find()
        }

        return notifications
            .query()
            .equal(Notifications_.isDeleted, isDeleted)
            .orderDesc(Notifications_.time)
            .build()
            .find()
            .filter { it.packageName.target.pkg == pkgName }
    }

    fun allPackageName(): List<PackageName> {
        return packageNames
            .query()
            .order(PackageName_.name)
            .build()
            .find()
    }

    fun allPackageNameLazy(): LazyList<PackageName> {
        return packageNames
            .query()
            .build()
            .findLazy()
    }

    fun allNotifications(): LazyList<Notifications> {
        return notifications
            .query()
            .build()
            .findLazy()
    }

    fun deletedNotificationSearch(string: String): List<Notifications> {
        return notifications
            .query()
            .equal(Notifications_.isDeleted, true)
            .contains(Notifications_.title, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .or()
            .contains(Notifications_.text, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .orderDesc(Notifications_.time)
            .build()
            .find()
    }

    fun lastNotification(pkgName: String): Notifications? {
        return notifications
            .query()
            .orderDesc(Notifications_.entityId)
            .build()
            .find().firstOrNull { it.packageName.target.pkg == pkgName }
    }

    fun lastNotificationWithLimit(): List<Notifications> {
        return notifications
            .query()
            .orderDesc(Notifications_.entityId)
            .build()
            .find(0, 50)
    }

    fun createNotification(
        pkgName: String,
        title: String,
        date: Long,
        text: String,
        bigText: String,
        conversationTitle: String,
        infoText: String,
        peopleList: String,
        titleBig: String
    ): Notifications? {
        val packageName = getPackageName(pkgName) ?: return null

        val notifications = Notifications(
            0,
            title,
            Date(date),
            text,
            bigText,
            conversationTitle,
            infoText,
            peopleList,
            titleBig
        )

        notifications.packageName.target = packageName

        return notifications
    }

    fun createNotificationFromJson(
        pkgName: String,
        title: String,
        date: Long,
        text: String,
        bigText: String?,
        conversationTitle: String?,
        infoText: String?,
        peopleList: String?,
        titleBig: String?,
        isDeleted: Boolean
    ) {
        val packageName = getPackageName(pkgName)

        val notification = Notifications(
            0,
            title,
            Date(date),
            text,
            bigText,
            conversationTitle,
            infoText,
            peopleList,
            titleBig,
            isDeleted
        )

        notification.packageName.target = packageName

        if (packageName != null) {
            notifications.put(notification)
        }
    }

    fun createNotificationDeleted(
        pkgName: String,
        title: String,
        date: Long,
        text: String,
        bigText: String,
        conversationTitle: String,
        infoText: String,
        peopleList: String,
        titleBig: String
    ): Notifications? {
        val packageName = getPackageName(pkgName) ?: return null

        val notification = Notifications(
            0,
            title,
            Date(date),
            text,
            bigText,
            conversationTitle,
            infoText,
            peopleList,
            titleBig,
            true
        )

        notification.packageName.target = packageName

        return notification
    }

    fun countNotifications(): Long {
        return notifications
            .query()
            .build()
            .count()
    }

    fun createPackageName(
        pkgName: String
    ): PackageName {
        return PackageName(
            0,
            Utils.getAppNameFromPackageName(pkgName),
            pkgName
        )
    }

    fun addAllInstalledPackageNames(includeSystemApps: Boolean = false) {
        val packageNames = if (includeSystemApps) getInstalledApps() else getInstalledUserApps()
        packageNames.forEach {
            if (getPackageName(it.packageName) == null) {
                MyApplication.packageNames.put(createPackageName(it.packageName))
            }
        }
    }

    fun createPackageNameFromJson(
        pkgName: String,
        name: String?,
        isBlacklist: Boolean,
        isWhiteList: Boolean,
        isChat: Boolean
    ) {
        val existing = getPackageName(pkgName)
        packageNames.put(
            (existing ?: PackageName(pkg = pkgName)).apply {
                this.name = name?.takeIf { it.isNotBlank() } ?: this.name
                this.isBlackList = isBlacklist
                this.isWhiteList = isWhiteList
                this.isChat = isChat
            }
        )
    }

    fun createBlackListPackageName(
        pkgName: String
    ): PackageName {
        return PackageName(
            0,
            Utils.getAppNameFromPackageName(pkgName),
            pkgName,
            true
        )
    }

    fun getPackageName(pkgName: String): PackageName? {
        return packageNames
            .query()
            .equal(
                PackageName_.pkg,
                pkgName,
                QueryBuilder.StringOrder.CASE_SENSITIVE
            )
            .build()
            .findFirst()
    }

    fun allPackageNameFromTable(): LazyList<PackageName> {
        return packageNames
            .query()
            .order(PackageName_.name)
            .build()
            .findLazy()
    }

    fun nameToPackageName(name: String): String {
        val packageName = packageNames
            .query()
            .equal(
                PackageName_.name,
                name,
                QueryBuilder.StringOrder.CASE_SENSITIVE
            )
            .build()
            .findFirst()

        return packageName?.pkg
            ?: (packageNames
                .query()
                .equal(
                    PackageName_.pkg,
                    name,
                    QueryBuilder.StringOrder.CASE_SENSITIVE
                )
                .build()
                .findFirst()?.pkg ?: "")
    }

    fun packageNameSearch(string: String): LazyList<PackageName> {
        return packageNames
            .query()
            .contains(PackageName_.name, string, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .orderDesc(PackageName_.entityId)
            .build()
            .findLazy()
    }

    fun deleteNotificationById(id: Long) {
        notifications
            .query()
            .equal(Notifications_.entityId, id)
            .build()
            .remove()
    }

    fun deleteMultipleNotificationByIds(id: List<Long>) {
        try {
            notifications.removeByIds(id)
        } catch (e: Exception) {
            LOGGER.log("Error while deleting multiple notification by ids: ${e.stackTraceToString()}")
        }

    }

    fun getInstalledPackageNamesFromList(packageName: List<PackageName>): MutableList<PackageName> {
        return packageName.toMutableList().apply {
            removeAll {
                !isAppInstalled(it.pkg)
            }
        }
    }
}
