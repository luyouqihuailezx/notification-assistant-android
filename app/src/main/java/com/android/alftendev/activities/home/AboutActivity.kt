package com.android.alftendev.activities.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.alftendev.BuildConfig
import com.android.alftendev.R
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.UiUtils
import com.android.alftendev.utils.Utils

@OptIn(ExperimentalMaterial3Api::class)
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        setContent { NotificationListenerTheme { AboutScreen() } }
    }

    @Composable
    private fun AboutScreen() {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.about)) }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item { TextButton({ Utils.openPlayStore(this@AboutActivity, BuildConfig.APPLICATION_ID) }) { Text("${getString(R.string.version)} ${BuildConfig.VERSION_NAME}\n${getString(R.string.rating)}") } }
                item { LinkItem(R.string.github) { Utils.openLink(this@AboutActivity, "https://github.com/Alfio010/notification-listener-android") } }
                item { LicenseItem(R.string.privacy_policy, "file:///android_asset/privacy_policy.html") }
                item { LicenseItem(R.string.source_notices, "file:///android_asset/source_notices.html") }
                item { Text(stringResource(R.string.licenses)) }
                item { LicenseItem(R.string.app_license, "file:///android_asset/gnu_license.html") }
                item { LicenseItem(R.string.open_source_licenses, "file:///android_asset/open_source_licenses.html") }
                item { LicenseItem(R.string.icon_licenses, "file:///android_asset/material_icon_license.html") }
            }
        }
    }

    @Composable
    private fun LinkItem(title: Int, onClick: () -> Unit) { ListItem(headlineContent = { Text(stringResource(title)) }, trailingContent = { TextButton(onClick) { Text(stringResource(R.string.more_info)) } }) }

    @Composable
    private fun LicenseItem(title: Int, path: String) { LinkItem(title) { startActivity(Intent(this@AboutActivity, WebViewActivity::class.java).putExtra("filePath", path)) } }
}
