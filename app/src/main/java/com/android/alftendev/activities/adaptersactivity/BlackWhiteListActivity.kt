package com.android.alftendev.activities.adaptersactivity

import android.icu.text.Transliterator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.android.alftendev.utils.SelectedAppsStrip
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.adapters.BlackWhitelistAdapter
import com.android.alftendev.models.PackageName
import com.android.alftendev.utils.AuthUtils
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.UiUtils
import com.android.alftendev.utils.UiUtils.uiDefaultSettings
import com.android.alftendev.utils.computables.AppListCache
import com.android.alftendev.utils.computables.PackageSettingsCache
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BlackWhiteListActivity : AppCompatActivity() {
    private val transliterator = Transliterator.getInstance("Han-Latin; Latin-ASCII")
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: GridLayoutManager
    private lateinit var alphabetIndex: LinearLayout
    private lateinit var selectedAppsSection: View
    private lateinit var selectedAppsContainer: LinearLayout
    private var allApps: List<PackageName> = emptyList()
    private var currentQuery = ""
    private var indexEntries: List<Pair<String, Int>> = emptyList()
    private var activeIndex = -1
    private var columnCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        AuthUtils.askAuth(this)
        setContentView(R.layout.blacklist_activity)
        uiDefaultSettings(this, true)
        title = getString(R.string.notification_collection_apps)

        recyclerView = findViewById(R.id.lvSettings)
        alphabetIndex = findViewById(R.id.alphabetIndex)
        selectedAppsSection = findViewById(R.id.selectedAppsSection)
        selectedAppsContainer = findViewById(R.id.selectedAppsContainer)
        alphabetIndex.setOnTouchListener { view, event ->
            if (
                indexEntries.isEmpty() ||
                view.height <= 0 ||
                (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE)
            ) {
                return@setOnTouchListener true
            }
            val index = ((event.y / view.height) * indexEntries.size).toInt()
                .coerceIn(0, indexEntries.lastIndex)
            if (index != activeIndex) {
                activeIndex = index
                layoutManager.scrollToPositionWithOffset(indexEntries[index].second, 0)
            }
            true
        }
        layoutManager = GridLayoutManager(this, columnCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.doOnLayout { view ->
            val minimumCardWidth = (88 * resources.displayMetrics.density).toInt()
            val availableWidth = view.width - view.paddingStart - view.paddingEnd
            val newColumnCount = (availableWidth / minimumCardWidth).coerceAtLeast(1)
            if (newColumnCount != columnCount) {
                columnCount = newColumnCount
                layoutManager.spanCount = columnCount
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
        findViewById<EditText>(R.id.etSearchSettings).apply {
            hint = getString(R.string.search_apps)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentQuery = s?.toString().orEmpty()
                    renderList()
                }
            })
        }
        findViewById<ImageButton>(R.id.bBlackWhitelistSettings).setOnClickListener { showSelectionActions() }
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (allApps.isNotEmpty()) loadApps()
    }

    private fun loadApps() {
        MyApplication.executor.execute {
            DBUtils.addAllInstalledPackageNames()
            val installed = AppListCache.getInstalledUserApps().map { it.packageName }.toSet()
            allApps = DBUtils.allPackageName().filter { it.pkg in installed }.sortedWith(appComparator)
            runOnUiThread { renderList() }
        }
    }

    private fun renderList() {
        renderSelectedApps()
        val query = normalize(currentQuery)
        val filtered = allApps.filter {
            query.isBlank() || normalize(displayName(it)).contains(query) || it.pkg.lowercase().contains(query)
        }
        val adapter = BlackWhitelistAdapter(filtered, ::sectionFor) {
            renderSelectedApps()
        }
        recyclerView.adapter = adapter
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (adapter.getItemViewType(position) == 0) columnCount else 1
        }
        buildAlphabetIndex(adapter.sectionPositions)
    }

    private fun renderSelectedApps() {
        SelectedAppsStrip.render(selectedAppsSection, selectedAppsContainer, allApps.filter { it.isWhiteList }, ::removeSelectedApp)
    }

    private fun removeSelectedApp(app: PackageName) {
        SelectedAppsStrip.remove(
            app = app,
            onRemoved = ::renderList,
            onFailure = { error -> showOperationError(error) }
        )
    }

    private fun showOperationError(error: Throwable) {
        val details = error.stackTraceToString()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operation_failed)
            .setMessage(details)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.error_details), details))
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun buildAlphabetIndex(sectionPositions: Map<String, Int>) {
        alphabetIndex.removeAllViews()
        indexEntries = sectionPositions.toList()
        activeIndex = -1
        indexEntries.forEach { (letter, _) ->
            TextView(this).apply {
                text = letter
                gravity = Gravity.CENTER
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                alphabetIndex.addView(this)
            }
        }
    }

    private fun showSelectionActions() {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf(getString(R.string.select_all), getString(R.string.deselect_all))) { _, which ->
                setAllSelected(which == 0)
            }
            .show()
    }

    private fun setAllSelected(selected: Boolean) {
        MyApplication.executor.execute {
            val updated = allApps.onEach {
                it.isWhiteList = selected
                it.isBlackList = false
            }
            MyApplication.packageNames.put(updated)
            PackageSettingsCache.initCache()
            runOnUiThread { renderList() }
        }
    }

    private fun displayName(item: PackageName) = item.name?.takeIf { it.isNotBlank() } ?: item.pkg

    private fun normalize(value: String): String =
        transliterator.transliterate(value).replace(Regex("[^A-Za-z0-9]"), "").lowercase()

    private fun sectionFor(item: PackageName): String =
        normalize(displayName(item)).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"

    private val appComparator = Comparator<PackageName> { first, second ->
        normalize(displayName(first)).compareTo(normalize(displayName(second))).takeIf { it != 0 }
            ?: first.pkg.compareTo(second.pkg, ignoreCase = true)
    }
}
