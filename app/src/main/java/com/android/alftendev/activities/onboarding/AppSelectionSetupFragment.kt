package com.android.alftendev.activities.onboarding

import android.icu.text.Transliterator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.adapters.BlackWhitelistAdapter
import com.android.alftendev.models.PackageName
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.SelectedAppsStrip
import com.android.alftendev.utils.computables.AppListCache
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppSelectionSetupFragment : Fragment() {
    private val transliterator = Transliterator.getInstance("Han-Latin; Latin-ASCII")
    private lateinit var list: RecyclerView
    private lateinit var layoutManager: GridLayoutManager
    private lateinit var index: LinearLayout
    private lateinit var selectedAppsSection: View
    private lateinit var selectedAppsContainer: LinearLayout
    private var apps: List<PackageName> = emptyList()
    private var query = ""
    private var columns = 1
    private var indexEntries: List<Pair<String, Int>> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.blacklist_activity, container, false)
        list = view.findViewById(R.id.lvSettings)
        index = view.findViewById(R.id.alphabetIndex)
        selectedAppsSection = view.findViewById(R.id.selectedAppsSection)
        selectedAppsContainer = view.findViewById(R.id.selectedAppsContainer)
        layoutManager = GridLayoutManager(requireContext(), columns)
        list.layoutManager = layoutManager
        list.doOnLayout { recycler ->
            val minimum = (88 * resources.displayMetrics.density).toInt()
            val next = ((recycler.width - recycler.paddingStart - recycler.paddingEnd) / minimum).coerceAtLeast(1)
            if (next != columns) { columns = next; layoutManager.spanCount = next; list.adapter?.notifyDataSetChanged() }
        }
        view.findViewById<ImageButton>(R.id.bBlackWhitelistSettings).visibility = View.GONE
        view.findViewById<EditText>(R.id.etSearchSettings).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); render() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        index.setOnTouchListener { region, event ->
            if (indexEntries.isNotEmpty() && region.height > 0 && (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE)) {
                val position = ((event.y / region.height) * indexEntries.size).toInt().coerceIn(0, indexEntries.lastIndex)
                layoutManager.scrollToPositionWithOffset(indexEntries[position].second, 0)
            }
            true
        }
        load()
        return view
    }

    private fun load() {
        MyApplication.executor.execute {
            DBUtils.addAllInstalledPackageNames()
            val installed = AppListCache.getInstalledUserApps().map { it.packageName }.toSet()
            apps = DBUtils.allPackageName().filter { it.pkg in installed }.sortedWith(comparator)
            activity?.runOnUiThread { render() }
        }
    }

    private fun render() {
        renderSelectedApps()
        val normalized = normalize(query)
        val filtered = apps.filter { normalized.isBlank() || normalize(name(it)).contains(normalized) || it.pkg.lowercase().contains(normalized) }
        val adapter = BlackWhitelistAdapter(
            packages = filtered,
            sectionFor = ::section,
            onSelectionChanged = ::renderSelectedApps,
            onSelectionWriteFailed = ::showOperationError
        )
        list.adapter = adapter
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int) = if (adapter.getItemViewType(position) == 0) columns else 1 }
        index.removeAllViews()
        indexEntries = adapter.sectionPositions.toList()
        indexEntries.forEach { (letter, _) -> index.addView(TextView(requireContext()).apply { text = letter; gravity = Gravity.CENTER; textSize = 11f }, LinearLayout.LayoutParams(-1, 0, 1f)) }
    }

    private fun renderSelectedApps() {
        SelectedAppsStrip.render(selectedAppsSection, selectedAppsContainer, apps.filter { it.isWhiteList }, ::removeSelectedApp)
    }

    private fun removeSelectedApp(app: PackageName) {
        SelectedAppsStrip.remove(
            app = app,
            onRemoved = ::render,
            onFailure = { error -> showOperationError(error) }
        )
    }

    private fun showOperationError(error: Throwable) {
        val details = error.stackTraceToString()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.operation_failed)
            .setMessage(details)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.error_details), details))
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun name(item: PackageName) = item.name?.takeIf { it.isNotBlank() } ?: item.pkg
    private fun normalize(value: String) = transliterator.transliterate(value).replace(Regex("[^A-Za-z0-9]"), "").lowercase()
    private fun section(item: PackageName) = normalize(name(item)).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
    private val comparator = Comparator<PackageName> { a, b -> normalize(name(a)).compareTo(normalize(name(b))).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg, true) }
}
