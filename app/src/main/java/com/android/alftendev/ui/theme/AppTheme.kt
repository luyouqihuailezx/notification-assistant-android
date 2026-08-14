package com.android.alftendev.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.android.alftendev.R
import com.android.alftendev.composes.utils.Typography
import com.android.alftendev.utils.MySharedPref

@Composable
fun NotificationListenerTheme(content: @Composable () -> Unit) {
    val darkTheme = when (MySharedPref.getThemeOptions()) {
        2 -> true
        3 -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.blue_dark),
            onPrimary = Color.White,
            secondary = colorResource(R.color.blue),
            secondaryContainer = colorResource(R.color.background_dark_cardview),
            background = colorResource(R.color.background_dark),
            onBackground = colorResource(R.color.text_color_white),
            surface = colorResource(R.color.background_dark),
            onSurface = colorResource(R.color.text_color_white),
            surfaceVariant = colorResource(R.color.background_dark_cardview)
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.blue_dark),
            onPrimary = Color.White,
            secondary = colorResource(R.color.blue),
            secondaryContainer = colorResource(R.color.cyan_light),
            background = colorResource(R.color.background),
            onBackground = colorResource(R.color.text_color_black),
            surface = colorResource(R.color.background),
            onSurface = colorResource(R.color.text_color_black),
            surfaceVariant = colorResource(R.color.background_light_cardview)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
