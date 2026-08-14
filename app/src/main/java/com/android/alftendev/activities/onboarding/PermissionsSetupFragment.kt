package com.android.alftendev.activities.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.alftendev.activities.home.PermissionManagementContent
import com.android.alftendev.utils.PermissionUtils

@Composable fun PermissionsSetupFragment() = PermissionManagementContent(Modifier)
fun isReady(context: android.content.Context) = PermissionUtils.isNotificationServiceEnabled(context) && PermissionUtils.canReadInstalledApps(context)
