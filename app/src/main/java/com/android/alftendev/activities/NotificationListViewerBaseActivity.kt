package com.android.alftendev.activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
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
import com.android.alftendev.agent.AgentSessionMenuAdapter
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
import java.util.concurrent.atomic.AtomicInteger

abstract class NotificationListViewerBaseActivity : AppCompatActivity(),
    NotificationsAdapter.OnSelectionChangeListener {
    private companion object {
        const val STATE_SELECTED_TAB = "selected_tab"
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    open lateinit var recycleView: RecyclerView
    open lateinit var etSearch: EditText
    private lateinit var adapter: NotificationsAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var notificationHistoryContent: LinearLayout
    private lateinit var settingsContainer: FrameLayout
    private lateinit var agentContainer: ComposeView
    private var agentController: AgentController? = null
    private lateinit var agentActionBarView: View
    private lateinit var agentSessionTitle: TextView
    private var agentSessionPopup: PopupWindow? = null
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadGeneration = AtomicInteger(0)
    private var pendingSearch: Runnable? = null
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

    private fun loadNotifications(load: () -> List<Notifications>, showRefreshing: Boolean = false) {
        val generation = loadGeneration.incrementAndGet()
        if (showRefreshing) swipeRefresh.isRefreshing = true
        MyApplication.executor.execute {
            runCatching(load)
                .onSuccess { notifications ->
                    if (generation == loadGeneration.get()) refreshList(notifications)
                }
                .onFailure { throwable ->
                    if (generation == loadGeneration.get()) showLoadError(throwable)
                }
            if (showRefreshing) runOnUiThread { swipeRefresh.isRefreshing = false }
        }
    }

    private fun showLoadError(throwable: Throwable) {
        val details = throwable.stackTraceToString()
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle("通知加载失败")
                .setMessage(throwable.message ?: throwable.javaClass.name)
                .setPositiveButton("复制完整错误信息") { _, _ ->
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("通知加载错误", details))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
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
        agentActionBarView = layoutInflater.inflate(R.layout.actionbar_agent, null)
        agentSessionTitle = agentActionBarView.findViewById(R.id.agentSessionTitle)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            loadNotifications(::getNotifications, showRefreshing = true)
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
        linearLayoutManager.initialPrefetchItemCount = 4
        recycleView.layoutManager = linearLayoutManager
        recycleView.setItemViewCacheSize(8)

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
                pendingSearch?.let(mainHandler::removeCallbacks)
                val filter = s.toString()
                pendingSearch = Runnable {
                    loadNotifications(load = { getNotificationsBySearch(filter) })
                }
                mainHandler.postDelayed(pendingSearch!!, SEARCH_DEBOUNCE_MS)
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
        loadNotifications(::getNotifications)
    }

    override fun onDestroy() {
        pendingSearch?.let(mainHandler::removeCallbacks)
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
        resetAgentKeyboardLayout()
        bottomNavigation.visibility = View.VISIBLE
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
        resetAgentKeyboardLayout()
        bottomNavigation.visibility = View.VISIBLE
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
        bottomNavigation.visibility = View.VISIBLE
        notificationHistoryContent.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        agentContainer.visibility = View.VISIBLE
        isSettingsVisible = false
        isAgentVisible = true
        ensureAgentInitialized()
        showAgentActionBar()
    }

    private fun ensureAgentInitialized() {
        if (agentController != null) return
        val controller = AgentController(this)
        agentController = controller
        agentSessionTitle.setOnClickListener { showAgentSessionMenu(it) }
        agentActionBarView.findViewById<View>(R.id.agentNewSession).setOnClickListener {
            controller.createSession()
        }
        controller.observe { refreshAgentActionBar() }
        refreshAgentActionBar()
        agentContainer.bindNotificationAgent(controller) { imeVisible ->
            bottomNavigation.post { updateAgentKeyboardLayout(imeVisible) }
        }
    }

    private fun updateAgentKeyboardLayout(imeVisible: Boolean) {
        if (!isAgentVisible) return

        val bottomNavigationHeight = bottomNavigation.height.toFloat()
        val translation = if (imeVisible) bottomNavigationHeight else 0f
        agentContainer.translationY = translation
        bottomNavigation.translationY = translation
    }

    private fun resetAgentKeyboardLayout() {
        agentContainer.translationY = 0f
        bottomNavigation.translationY = 0f
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
        agentSessionPopup?.dismiss()
        supportActionBar?.apply {
            setDisplayShowCustomEnabled(false)
            setDisplayShowTitleEnabled(true)
        }
    }

    private fun refreshAgentActionBar() {
        val controller = agentController ?: return
        agentSessionTitle.text = getString(R.string.agent_session_title, controller.active.title)
    }

    private fun showAgentSessionMenu(anchor: View) {
        val controller = agentController ?: return
        agentSessionPopup?.dismiss()
        val sessions = controller.sessions
        val listView = ListView(this).apply {
            adapter = AgentSessionMenuAdapter(this@NotificationListViewerBaseActivity, sessions, controller.activeId)
            divider = null
            setOnItemClickListener { _, _, position, _ ->
                controller.switchTo(sessions[position].id)
                agentSessionPopup?.dismiss()
            }
            setOnItemLongClickListener { _, _, position, _ ->
                agentSessionPopup?.dismiss()
                confirmDeleteSession(sessions[position].id, sessions[position].title)
                true
            }
        }
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val minWidth = (280 * resources.displayMetrics.density).toInt()
        agentSessionPopup = PopupWindow(
            listView,
            maxOf(anchor.width, minWidth),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f
            setBackgroundDrawable(ColorDrawable(typedValue.data))
            isOutsideTouchable = true
            showAsDropDown(anchor)
        }
    }

    private fun confirmDeleteSession(id: String, title: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agent_delete_session)
            .setMessage(getString(R.string.agent_delete_session_confirm, title))
            .setPositiveButton(R.string.confirm) { _, _ -> agentController?.deleteSession(id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

}