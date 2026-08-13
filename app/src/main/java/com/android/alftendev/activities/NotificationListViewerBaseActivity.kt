package com.android.alftendev.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.alftendev.MyApplication
import com.android.alftendev.MyApplication.Companion.authSuccess
import com.android.alftendev.MyApplication.Companion.sharedPrefName
import com.android.alftendev.R
import com.android.alftendev.activities.home.SettingsActivity
import com.android.alftendev.agent.AgentController
import com.android.alftendev.agent.bindNotificationAgent
import com.android.alftendev.adapters.NotificationsAdapter
import com.android.alftendev.models.Notifications
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ALREADY_ASKED
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ENABLED
import com.android.alftendev.services.NotificationListenerKeepAlive
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
    private lateinit var agentContainer: ComposeView
    private lateinit var agentController: AgentController
    private lateinit var agentActionBarView: View
    private lateinit var agentSessionTitle: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var isSettingsVisible = false
    private var isAgentVisible = false

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
        agentContainer = findViewById(R.id.agentContainer)
        agentController = AgentController(this)
        agentActionBarView = layoutInflater.inflate(R.layout.actionbar_agent, null)
        agentSessionTitle = agentActionBarView.findViewById(R.id.agentSessionTitle)
        agentSessionTitle.setOnClickListener { showAgentSessionMenu(it) }
        agentActionBarView.findViewById<View>(R.id.agentNewSession).setOnClickListener {
            agentController.createSession()
        }
        agentController.observe { refreshAgentActionBar() }
        refreshAgentActionBar()
        agentContainer.bindNotificationAgent(agentController)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            MyApplication.executor.execute {
                refreshList(getNotifications())
                runOnUiThread { swipeRefresh.isRefreshing = false }
            }
        }
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
                R.id.nav_agent -> {
                    showAgent()
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
                        NotificationListenerKeepAlive.scheduleHealthCheckIfActive(this)
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
            } else if (isSettingsVisible || isAgentVisible) {
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

        hideAgentActionBar()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        supportActionBar?.title = getString(R.string.settings)
        notificationHistoryContent.visibility = View.GONE
        agentContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        isSettingsVisible = true
        isAgentVisible = false

        if (supportFragmentManager.findFragmentById(R.id.settingsContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsActivity.SettingsFragment())
                .commit()
        }
    }

    private fun showNotificationHistory() {
        if (!isSettingsVisible && !isAgentVisible) return

        hideAgentActionBar()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        supportActionBar?.title = getString(R.string.notification_history)
        settingsContainer.visibility = View.GONE
        agentContainer.visibility = View.GONE
        notificationHistoryContent.visibility = View.VISIBLE
        isSettingsVisible = false
        isAgentVisible = false
    }

    private fun showAgent() {
        if (isAgentVisible) return

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        notificationHistoryContent.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        agentContainer.visibility = View.VISIBLE
        isSettingsVisible = false
        isAgentVisible = true
        showAgentActionBar()
    }

    private fun showAgentActionBar() {
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayShowCustomEnabled(true)
            setCustomView(
                agentActionBarView,
                ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT,
                    ActionBar.LayoutParams.MATCH_PARENT
                )
            )
        }
        refreshAgentActionBar()
    }

    private fun hideAgentActionBar() {
        supportActionBar?.apply {
            setDisplayShowCustomEnabled(false)
            setDisplayShowTitleEnabled(true)
        }
    }

    private fun refreshAgentActionBar() {
        if (!::agentSessionTitle.isInitialized) return
        agentSessionTitle.text = getString(R.string.agent_session_title, agentController.active.title)
    }

    private fun showAgentSessionMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            agentController.sessions.forEach { session ->
                menu.add(session.title).setOnMenuItemClickListener {
                    agentController.switchTo(session.id)
                    true
                }
            }
            menu.add(getString(R.string.agent_new_session)).setOnMenuItemClickListener {
                agentController.createSession()
                true
            }
            if (agentController.sessions.size > 1) {
                menu.add(getString(R.string.agent_delete_session)).setOnMenuItemClickListener {
                    agentController.deleteActive()
                    true
                }
            }
            show()
        }
    }

}