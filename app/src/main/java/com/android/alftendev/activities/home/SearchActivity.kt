package com.android.alftendev.activities.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.adaptersactivity.ListSearchActivity
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DBUtils.allPackageName

class SearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContent { NotificationListenerTheme { SearchScreen(::runSearch) } }
        onBackPressedDispatcher.addCallback { finishAndRemoveTask() }
    }

    private fun runSearch(text: String, packageName: String, deleted: Boolean) {
        startActivity(Intent(this, ListSearchActivity::class.java).setAction(Intent.ACTION_MAIN).apply {
            putExtra("text", text)
            putExtra("appName", packageName)
            putExtra("pkgName", packageName)
            putExtra("isDeleted", deleted)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(onSearch: (String, String, Boolean) -> Unit) {
    val apps = remember { listOf(MyApplication.defaultSwValue to MyApplication.defaultSwValue) + allPackageName().map { (it.name?.takeIf(String::isNotBlank) ?: it.pkg) to it.pkg } }
    var text by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(apps.first()) }
    var expanded by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.search)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search)) }, minLines = 3)
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(selected.first, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text(stringResource(R.string.app_name)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded, { expanded = false }) { apps.forEach { app -> androidx.compose.material3.DropdownMenuItem(text = { Text(app.first) }, onClick = { selected = app; expanded = false }) } }
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(stringResource(R.string.deleted)); Switch(deleted, { deleted = it }) }
            Button({ onSearch(text, selected.second, deleted) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.search)) }
        }
    }
}
