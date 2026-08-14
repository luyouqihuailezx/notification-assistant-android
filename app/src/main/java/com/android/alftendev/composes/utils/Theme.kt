package com.android.alftendev.composes.utils

import androidx.compose.runtime.Composable
import com.android.alftendev.ui.theme.NotificationListenerTheme

@Composable
fun MyTheme(content: @Composable () -> Unit) {
    NotificationListenerTheme(content)
}
