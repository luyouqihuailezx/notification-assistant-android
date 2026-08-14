package com.android.alftendev.utils

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.android.alftendev.utils.MySharedPref.getAreScreenshotBlocked

class MyActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityPreCreated(activity, savedInstanceState)
        applyScreenOrientation(activity)

        if (getAreScreenshotBlocked()) {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.setRecentsScreenshotEnabled(false)
            }
        }
    }


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    private fun applyScreenOrientation(activity: Activity) {
        val configuration = activity.resources.configuration
        val shortestSideDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
        val longestSideDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
        val isPhone = shortestSideDp <= 500 && longestSideDp.toFloat() / shortestSideDp >= 16f / 9f
        activity.requestedOrientation = if (isPhone) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }
}