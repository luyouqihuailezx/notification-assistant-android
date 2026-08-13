package com.android.alftendev.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.alftendev.R
import com.android.alftendev.models.Notifications
import com.android.alftendev.models.getParsedNoti
import com.android.alftendev.utils.DateUtils.dateFormatter
import com.android.alftendev.utils.Utils
import com.android.alftendev.utils.computables.AppIcon
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView

@SuppressLint("NotifyDataSetChanged")
class NotificationsAdapter(
    private var notifications: List<Notifications>,
    private val context: Context,
    private val selectionListener: OnSelectionChangeListener
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    var isSelectionMode = false
    val selectedItemsIds = mutableSetOf<Long>()

    interface OnSelectionChangeListener {
        fun onSelectionModeChange(isSelectionMode: Boolean)
        fun onSelectionCountChange(count: Int)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: MaterialTextView = view.findViewById(R.id.tvNome)
        val tvDescription: MaterialTextView = view.findViewById(R.id.tvDescrizione)
        val tvDate: MaterialTextView = view.findViewById(R.id.tvDate)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)

        // val linearLayout: LinearLayoutCompat = view.findViewById(R.id.llNotificationAdapter)
        val cbSelect: MaterialCheckBox = view.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.custom_notification_layout, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val notification = notifications[position]

        val parsedNoti = getParsedNoti(
            title = notification.title,
            text = notification.text,
            packageName = notification.packageName.target.pkg,
            context
        )

        viewHolder.tvName.text = parsedNoti.title
        viewHolder.tvDescription.text = parsedNoti.text
        viewHolder.tvDate.text = dateFormatter(notification.time)

        viewHolder.ivIcon.setImageIcon(parsedNoti.icon)

        viewHolder.cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        viewHolder.cbSelect.isChecked = selectedItemsIds.contains(notification.entityId)

        viewHolder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(notification.entityId)
            } else {
                showDialog(
                    notification,
                    AppIcon.compute(notification.packageName.target.pkg),
                    context
                )
            }
        }

        viewHolder.cbSelect.setOnClickListener {
            toggleSelection(notification.entityId)
        }

        viewHolder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                selectionListener.onSelectionModeChange(true)
                toggleSelection(notification.entityId)
                notifyDataSetChanged()
            }
            true
        }
    }

    override fun getItemCount() = notifications.size

    private fun toggleSelection(entityId: Long) {
        if (selectedItemsIds.contains(entityId)) {
            selectedItemsIds.remove(entityId)
        } else {
            selectedItemsIds.add(entityId)
        }
        selectionListener.onSelectionCountChange(selectedItemsIds.size)

        if (selectedItemsIds.isEmpty()) {
            exitSelectionMode()
        } else {
            notifyDataSetChanged()
        }
    }

    fun selectAll() {
        selectedItemsIds.clear()
        notifications.forEach { selectedItemsIds.add(it.entityId) }
        selectionListener.onSelectionCountChange(selectedItemsIds.size)
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedItemsIds.clear()
        selectionListener.onSelectionCountChange(0)
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItemsIds.clear()
        selectionListener.onSelectionModeChange(false)
        selectionListener.onSelectionCountChange(0)
        notifyDataSetChanged()
    }

    fun updateData(newList: List<Notifications>) {
        this.notifications = newList
        notifyDataSetChanged()
    }

    @SuppressLint("InflateParams")
    private fun showDialog(notificationItem: Notifications, icon: Drawable?, context: Context) {
        val customAlertDialogView = LayoutInflater.from(context)
            .inflate(R.layout.custom_my_dialog, null, false)

        customAlertDialogView.findViewById<TextView>(R.id.tvDialogText)
            .text = notificationItem.bigText?.trim()?.takeUnless { it.isNullOrBlank() }
                ?: notificationItem.text

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(notificationItem.title)
        builder.setView(customAlertDialogView)

        if (icon != null) {
            builder.setIcon(icon)
        } else {
            builder.setIcon(R.mipmap.ic_launcher)
        }

        builder.setPositiveButton(context.getString(R.string.open_app)) { _, _ ->
            Utils.openApp(notificationItem.packageName.target.pkg, context)
        }
        builder.setOnCancelListener { it.dismiss() }
        builder.create()
        builder.show()
    }
}