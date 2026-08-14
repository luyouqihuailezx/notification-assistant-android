package com.android.alftendev.activities.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.alftendev.activities.adaptersactivity.AppSelectionContent

@Composable
fun AppSelectionSetupFragment() {
    val activity = androidx.compose.ui.platform.LocalContext.current as OnboardingActivity
    AppSelectionContent(Modifier, activity::showError)
}
