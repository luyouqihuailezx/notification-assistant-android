package com.android.alftendev.adapters

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.models.PackageName
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.computables.AppIcon
import com.android.alftendev.utils.computables.PackageSettingsCache
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView

class BlackWhitelistAdapter(
    packages: List<PackageName>,
    private val sectionFor: (PackageName) -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    sealed interface Row {
        data class Header(val letter: String) : Row
        data class App(val packageName: PackageName) : Row
    }

    private val rows = buildList {
        packages.groupBy(sectionFor).forEach { (letter, apps) ->
            add(Row.Header(letter))
            apps.forEach { add(Row.App(it)) }
        }
    }

    val sectionPositions: Map<String, Int> = rows.mapIndexedNotNull { index, row ->
        (row as? Row.Header)?.let { it.letter to index }
    }.toMap()

    class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: MaterialTextView = view.findViewById(R.id.tvPackageName)
        val selected: CompoundButton = view.findViewById(R.id.swIsBlackListed)
        val icon: ImageView = view.findViewById(R.id.ivBlacklist)
        val card: MaterialCardView = view.findViewById(R.id.cardApp)
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: MaterialTextView = view.findViewById(R.id.tvSectionTitle)
    }

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Header) HEADER else APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == HEADER) R.layout.item_app_section_header else R.layout.custom_blacklist_layout
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (viewType == HEADER) HeaderHolder(view) else AppHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).title.text = row.letter
            is Row.App -> bindApp(holder as AppHolder, row.packageName)
        }
    }

    private fun bindApp(holder: AppHolder, item: PackageName) {
        holder.name.text = item.name?.takeIf { it.isNotBlank() } ?: item.pkg
        holder.selected.setOnCheckedChangeListener(null)
        holder.selected.isChecked = item.isWhiteList
        setSelectedBackground(holder, item.isWhiteList)
        holder.selected.setOnCheckedChangeListener { button, checked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            item.isWhiteList = checked
            setSelectedBackground(holder, checked)
            MyApplication.executor.execute {
                val entity = DBUtils.getPackageName(item.pkg) ?: item
                entity.isWhiteList = checked
                entity.isBlackList = false
                MyApplication.packageNames.put(entity)
                PackageSettingsCache.updatePackage(entity.pkg, false, checked)
            }
        }

        holder.icon.tag = item.pkg
        holder.icon.setImageResource(R.drawable.baseline_android_24)
        MyApplication.executor.execute {
            val icon = AppIcon.compute(item.pkg)
            Handler(Looper.getMainLooper()).post {
                if (holder.icon.tag == item.pkg && icon != null) holder.icon.setImageDrawable(icon)
            }
        }
        holder.itemView.setOnClickListener { holder.selected.performClick() }
    }

    private fun setSelectedBackground(holder: AppHolder, selected: Boolean) {
        val color = if (selected) {
            ContextCompat.getColor(holder.itemView.context, R.color.collection_app_selected)
        } else {
            com.google.android.material.color.MaterialColors.getColor(
                holder.card,
                com.google.android.material.R.attr.colorSurface
            )
        }
        holder.card.setCardBackgroundColor(color)
    }

    override fun getItemCount() = rows.size

    companion object {
        private const val HEADER = 0
        private const val APP = 1
    }
}
