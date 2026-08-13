package com.android.alftendev.activities.onboarding

import android.content.Context

class OnboardingSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    var completed: Boolean
        get() = preferences.getBoolean(COMPLETED, false)
        set(value) { preferences.edit().putBoolean(COMPLETED, value).apply() }
    var defaultsInitialized: Boolean
        get() = preferences.getBoolean(DEFAULTS_INITIALIZED, false)
        set(value) { preferences.edit().putBoolean(DEFAULTS_INITIALIZED, value).apply() }
    companion object {
        private const val NAME = "notification_onboarding_v1"
        private const val COMPLETED = "completed"
        private const val DEFAULTS_INITIALIZED = "defaults_initialized"
    }
}
