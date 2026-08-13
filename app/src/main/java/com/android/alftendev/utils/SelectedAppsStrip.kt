package com.android.alftendev.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.models.PackageName
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.computables.AppIcon
import com.android.alftendev.utils.computables.PackageSettingsCache

object SelectedAppsStrip {
    private const val TAG = "SelectedAppsStrip"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun render(section: View, container: LinearLayout, apps: List<PackageName>, onRemove: (PackageName) -> Unit) {
        section.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        container.removeAllViews()
        apps.forEach { app ->
            val itemView = LayoutInflater.from(container.context)
                .inflate(R.layout.item_selected_app_icon, container, false)
            val iconView = itemView.findViewById<ImageView>(R.id.ivSelectedAppIcon)
            iconView.tag = app.pkg
            iconView.setImageResource(R.drawable.baseline_android_24)
            itemView.findViewById<View>(R.id.bRemoveSelectedApp).setOnClickListener { onRemove(app) }
            container.addView(itemView)
            loadIcon(iconView, app.pkg)
        }
    }

    fun remove(app: PackageName, onRemoved: () -> Unit, onFailure: (Throwable) -> Unit) {
        MyApplication.executor.execute {
            try {
                val entity = DBUtils.getPackageName(app.pkg) ?: app
                entity.isWhiteList = false
                entity.isBlackList = false
                MyApplication.packageNames.put(entity)
                PackageSettingsCache.updatePackage(entity.pkg, false, false)
                mainHandler.post {
                    app.isWhiteList = false
                    app.isBlackList = false
                    onRemoved()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to remove selected app ${app.pkg}", error)
                mainHandler.post { onFailure(error) }
            }
        }
    }

    private fun loadIcon(iconView: ImageView, packageName: String) {
        MyApplication.executor.execute {
            try {
                val icon = AppIcon.compute(packageName)
                mainHandler.post {
                    if (iconView.tag == packageName && icon != null) iconView.setImageDrawable(icon)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to load app icon for $packageName", error)
            }
        }
    }
}
