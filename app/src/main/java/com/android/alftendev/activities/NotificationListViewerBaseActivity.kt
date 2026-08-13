package com.android.alftendev.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.alftendev.MyApplication
import com.android.alftendev.MyApplication.Companion.authSuccess
import com.android.alftendev.MyApplication.Companion.sharedPrefName
import com.android.alftendev.R
import com.android.alftendev.activities.home.SettingsActivity
import com.android.alftendev.adapters.NotificationsAdapter
import com.android.alftendev.models.Notifications
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ALREADY_ASKED
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ENABLED
import com.android.alftendev.utils.MySharedPref.getIsRecordNotificationsAlreadyAsked
import com.android.alftendev.utils.MySharedPref.getIsRecordNotificationsEnabled
import com.android.alftendev.utils.PermissionUtils.askNotificationServicePermission
import com.android.alftendev.utils.PermissionUtils.isNotificationServiceEnabled
import com.android.alftendev.utils.UiUtils
import com.android.alftendev.utils.UiUtils.uiDefaultSettings
import com.android.alftendev.utils.Utils
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView

abstract class NotificationListViewerBaseActivity : AppCompatActivity(),
    NotificationsAdapter.OnSelectionChangeListener {
    private companion object {
        const val STATE_SELECTED_TAB = "selected_tab"
    }

    open lateinit var recycleView: RecyclerView
    open lateinit var etSearch: EditText
    private lateinit var adapter: NotificationsAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var notificationHistoryContent: LinearLayout
    private lateinit var settingsContainer: FrameLayout
    private var isSettingsVisible = false

    private lateinit var llSelectionBar: LinearLayout
    private lateinit var cbSelectAll: MaterialCheckBox
    private lateinit var btnDeleteSelected: ImageButton

    abstract fun getNotifications(): List<Notifications>
    abstract fun getNotificationsBySearch(filter: String): List<Notifications>

    open fun refreshList(notifications: List<Notifications>) {
        runOnUiThread {
            //adapter = NotificationsAdapter(notifications, this, this)
            //recycleView.adapter = adapter

            if (!::adapter.isInitialized) {
                adapter = NotificationsAdapter(notifications, this@NotificationListViewerBaseActivity, this@NotificationListViewerBaseActivity)
                recycleView.adapter = adapter
            } else {
                adapter.updateData(notifications)
            }

            onSelectionModeChange(false)
        }
    }

    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)

        askAuth(this)

        runOnUiThread {
            uiDefaultSettings(this)
            setContentView(R.layout.activity_notification_list)
        }

        bottomNavigation = findViewById(R.id.bottomNavigation)
        notificationHistoryContent = findViewById(R.id.notificationHistoryContent)
        settingsContainer = findViewById(R.id.settingsContainer)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_history -> {
                    showNotificationHistory()
                    true
                }
                R.id.nav_settings -> {
                    showSettings()
                    true
                }
                else -> false
            }
        }
        val selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB) ?: R.id.nav_history
        bottomNavigation.selectedItemId = selectedTab

        etSearch = findViewById(R.id.etSearch)
        recycleView = findViewById(R.id.lvAll)
        linearLayoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recycleView.layoutManager = linearLayoutManager

        llSelectionBar = findViewById(R.id.llSelectionBar)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)

        cbSelectAll.setOnClickListener {
            if (::adapter.isInitialized) {
                if (cbSelectAll.isChecked) {
                    adapter.selectAll()
                } else {
                    adapter.deselectAll()
                }
            }
        }

        btnDeleteSelected.setOnClickListener {
            if (::adapter.isInitialized && adapter.selectedItemsIds.isNotEmpty()) {
                val builderDelete = MaterialAlertDialogBuilder(this)
                builderDelete.setTitle(getString(R.string.confirm_delete_noti_warning))
                builderDelete.setMessage(
                    getString(
                        R.string.are_you_sure_you_want_to_delete_the_selected_items_they_cannot_be_recovered,
                        adapter.selectedItemsIds.size.toString()
                    )
                )
                builderDelete.setIcon(R.mipmap.ic_launcher)

                builderDelete.setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    val idsToDelete = adapter.selectedItemsIds.toList()

                    MyApplication.executor.execute {
                        DBUtils.deleteMultipleNotificationByIds(idsToDelete)

                        refreshList(getNotifications())
                    }
                }

                builderDelete.setNegativeButton(R.string.cancel) { _, _ -> }
                builderDelete.setOnCancelListener { it.dismiss() }
                builderDelete.create().show()
            }
        }

        if (javaClass.simpleName == "AllNotificationsActivity") {
            if (!getIsRecordNotificationsEnabled() && !getIsRecordNotificationsAlreadyAsked()) {
                val sharedPref = this.getSharedPreferences(sharedPrefName, MODE_PRIVATE)
                sharedPref.edit()
                    .putBoolean(RECORD_NOTIFICATIONS_ALREADY_ASKED, true).commit()
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle(getString(R.string.ask_not_permission_title))
                    builder.setMessage(getString(R.string.ask_not_permission))
                    builder.setPositiveButton(
                        getString(R.string.yes)
                    ) { _, _ ->
                        sharedPref.edit()
                            .putBoolean(RECORD_NOTIFICATIONS_ENABLED, true).commit()
                        askNotificationServicePermission(this)
                    }
                    builder.setNegativeButton(getString(R.string.no)) { _, _ -> }
                    builder.setNeutralButton(getString(R.string.privacy_policy)) { _, _ ->
                        Utils.openLink(
                            this,
                            "https://alfio010.github.io/"
                        )
                    }
                    builder.setOnCancelListener { it.dismiss() }
                    builder.create()
                    builder.show()
                }
            } else if (!isNotificationServiceEnabled(this)) {
                runOnUiThread {
                    UiUtils.showToast(getString(R.string.explain_not_permission), this)
                }
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) = Unit
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                MyApplication.executor.execute { refreshList(getNotificationsBySearch(s.toString())) }
            }
        })

        //MyApplication.executor.execute { refreshList(getNotifications()) }

        onBackPressedDispatcher.addCallback(this) {
            if (::adapter.isInitialized && adapter.isSelectionMode) {
                adapter.exitSelectionMode()
            } else if (isSettingsVisible) {
                bottomNavigation.selectedItemId = R.id.nav_history
            } else {
                finishAndRemoveTask()
            }
        }

    }

    override fun onSelectionModeChange(isSelectionMode: Boolean) {
        runOnUiThread {
            if (isSelectionMode) {
                llSelectionBar.visibility = View.VISIBLE
                etSearch.visibility = View.GONE
            } else {
                llSelectionBar.visibility = View.GONE
                etSearch.visibility = View.VISIBLE
                cbSelectAll.isChecked = false
            }
        }
    }

    override fun onSelectionCountChange(count: Int) {
        runOnUiThread {
            if (::adapter.isInitialized) {
                cbSelectAll.isChecked = count > 0 && count == adapter.itemCount
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MyApplication.executor.execute { refreshList(getNotifications()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (javaClass.simpleName == "AllNotificationsActivity") {
            authSuccess.set(false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, bottomNavigation.selectedItemId)
        super.onSaveInstanceState(outState)
    }

    private fun showSettings() {
        if (isSettingsVisible) return

        notificationHistoryContent.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        isSettingsVisible = true

        if (supportFragmentManager.findFragmentById(R.id.settingsContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsActivity.SettingsFragment())
                .commit()
        }
    }

    private fun showNotificationHistory() {
        if (!isSettingsVisible) return

        settingsContainer.visibility = View.GONE
        notificationHistoryContent.visibility = View.VISIBLE
        isSettingsVisible = false
    }

}