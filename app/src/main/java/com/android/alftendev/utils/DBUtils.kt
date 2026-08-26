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
import com.android.alftendev.models.MessageDateReference
import com.android.alftendev.models.MessageDateReferenceEntity
import com.android.alftendev.models.MessageDateReferenceParser
import com.android.alftendev.models.MessageDatePrecision
import com.android.alftendev.models.MessageDateSearchFilter
import com.android.alftendev.models.MessageDateReferenceEntity_
import com.android.alftendev.utils.Utils.isAppInstalled
import com.android.alftendev.utils.computables.AppListCache.getInstalledApps
import com.android.alftendev.utils.computables.AppListCache.getInstalledUserApps
import com.android.alftendev.widget.UpcomingNotiWidgetProvider
import io.objectbox.query.LazyList
import io.objectbox.query.QueryBuilder
import java.util.Calendar
import java.util.Date

object DBUtils {
    const val DATE_INDEX_VERSION = MessageDateReferenceParser.INDEX_VERSION
    val LOGGER = CustomLog("noti-database-utils")

    data class CalendarEvent(val referenceId: Long, val notificationId: Long, val app: String, val title: String, val body: String, val reference: MessageDateReference)

    fun rebuildDateReferences(notification: Notifications, refreshWidget: Boolean = true) {
        MyApplication.dateReferences.query().equal(MessageDateReferenceEntity_.notificationId, notification.entityId).build().remove()
        val text = listOf(notification.title, notification.text, notification.bigText, notification.conversationTitle, notification.infoText, notification.peopleList, notification.titleBig).filterNotNull().joinToString("\n")
        val refs = MessageDateReferenceParser.referencesIn(text, notification.time)
        MyApplication.dateReferences.put(refs.map { it.toEntity(notification.entityId) })
        notification.dateIndexVersion = DATE_INDEX_VERSION
        notifications.put(notification)
        if (refreshWidget && refs.isNotEmpty()) {
            UpcomingNotiWidgetProvider.refreshAll(MyApplication.application)
        }
    }

    fun backfillDateReferences() {
        val pending = notifications.query()
            .notEqual(Notifications_.dateIndexVersion, DATE_INDEX_VERSION.toLong())
            .build()
            .find()
        pending.chunked(200).forEach { batch -> batch.forEach { notification ->
            try { rebuildDateReferences(notification, refreshWidget = false) } catch (error: Throwable) { LOGGER.log("日期索引回填失败，通知 ${notification.entityId}: ${error.stackTraceToString()}") }
        } }
        UpcomingNotiWidgetProvider.refreshAll(MyApplication.application)
    }

    fun calendarEvents(from: Date, to: Date, app: String? = null): List<CalendarEvent> {
        val refs = MyApplication.dateReferences.query().equal(MessageDateReferenceEntity_.indexVersion, DATE_INDEX_VERSION.toLong()).build().find()
            .filter { it.resolvedTimestamp != null && it.resolvedTimestamp!! >= from.time && it.resolvedTimestamp!! < to.time }
        val notificationsById = notifications.query().build().find().associateBy { it.entityId }
        return refs.mapNotNull { entity ->
            val notification = notificationsById[entity.notificationId] ?: return@mapNotNull null
            val packageName = notification.packageName.target?.name ?: notification.packageName.target?.pkg ?: "未知应用"
            if (app != null && packageName != app) return@mapNotNull null
            CalendarEvent(entity.entityId, notification.entityId, packageName, notification.title, notification.bigText ?: notification.text, entity.toReference())
        }.sortedWith(compareBy({ it.reference.resolvedDate }, { it.reference.candidateRank }, { it.notificationId }))
    }

    fun upcomingEvents(now: Date = Date()): List<CalendarEvent> {
        val lower = now.time - 86_400_000L
        val upper = now.time + 86_400_000L
        val calendar = Calendar.getInstance()
        return calendarEvents(Date(lower), Date(upper + 1)).mapNotNull { event ->
            val rawDate = event.reference.resolvedDate ?: return@mapNotNull null
            val effectiveDate = if (event.reference.precision == MessageDatePrecision.DAY) {
                endOfDay(rawDate, calendar)
            } else {
                rawDate
            }
            if (effectiveDate.after(now) && effectiveDate.time <= upper) {
                event.copy(reference = event.reference.copy(resolvedDate = effectiveDate, precision = event.reference.precision))
            } else {
                null
            }
        }.sortedWith(compareBy({ it.reference.resolvedDate }, { it.reference.candidateRank }, { it.notificationId }))
            .take(20)
    }

    private fun endOfDay(date: Date, calendar: Calendar): Date {
        val start = calendar.clone() as Calendar
        start.time = date
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        start.add(Calendar.DAY_OF_YEAR, 1)
        start.add(Calendar.SECOND, -1)
        return start.time
    }

    fun availableDateApps(): List<String> = notifications.query().build().find().mapNotNull { it.packageName.target?.name ?: it.packageName.target?.pkg }.distinct().sorted()

    fun notificationById(id: Long): Notifications? = notifications.get(id)

    fun matchingNotificationIds(filter: MessageDateSearchFilter): Set<Long> {
        val result = MyApplication.dateReferences.query().equal(MessageDateReferenceEntity_.indexVersion, DATE_INDEX_VERSION.toLong()).build().find()
        return result.filter { entity ->
            (filter.year == null || entity.year == filter.year.toLong()) && (filter.month == null || entity.month == filter.month.toLong()) &&
                (filter.day == null || entity.day == filter.day.toLong()) && (filter.hour == null || entity.hour == filter.hour.toLong()) &&
                (filter.minute == null || entity.minute == filter.minute.toLong()) && (filter.second == null || entity.second == filter.second.toLong()) &&
                (filter.weekday == null || entity.weekday == filter.weekday.toLong()) && (filter.from == null || (entity.resolvedTimestamp ?: Long.MIN_VALUE) >= filter.from.time) &&
                (filter.to == null || (entity.resolvedTimestamp ?: Long.MAX_VALUE) <= filter.to.time) && (filter.originalText.isNullOrBlank() || entity.originalText.contains(filter.originalText!!, true))
        }.map { it.notificationId }.toSet()
    }

    fun deleteDateReferences(ids: Collection<Long>) { ids.forEach { id -> MyApplication.dateReferences.query().equal(MessageDateReferenceEntity_.notificationId, id).build().remove() } }

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
            rebuildDateReferences(notification)
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
        deleteDateReferences(listOf(id))
        deleteDateReferences(listOf(id))
    }

    fun deleteMultipleNotificationByIds(id: List<Long>) {
        try {
            notifications.removeByIds(id)
            deleteDateReferences(id)
            deleteDateReferences(id)
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

private fun MessageDateReference.toEntity(notificationId: Long) = MessageDateReferenceEntity(
    notificationId = notificationId,
    originalText = originalText,
    year = year?.toLong(), month = month?.toLong(), day = day?.toLong(), hour = hour?.toLong(), minute = minute?.toLong(), second = second?.toLong(), weekday = weekday?.toLong(),
    precision = precision.name,
    resolvedTimestamp = resolvedDate?.time,
    candidateRank = candidateRank,
    weight = weight,
    ruleId = ruleId,
    indexVersion = DBUtils.DATE_INDEX_VERSION
)

private fun MessageDateReferenceEntity.toReference() = MessageDateReference(
    originalText = originalText,
    year = year?.toInt(), month = month?.toInt(), day = day?.toInt(), hour = hour?.toInt(), minute = minute?.toInt(), second = second?.toInt(), weekday = weekday?.toInt(),
    precision = MessageDatePrecision.entries.firstOrNull { it.name == precision } ?: MessageDatePrecision.DAY,
    resolvedDate = resolvedTimestamp?.let(::Date),
    candidateRank = candidateRank,
    weight = weight,
    ruleId = ruleId
)
