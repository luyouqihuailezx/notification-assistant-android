package com.android.alftendev.activities.onboarding

import android.icu.text.Transliterator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.models.PackageName
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.computables.AppListCache

@Composable fun AppSelectionSetupFragment() {
    var apps by remember { mutableStateOf<List<PackageName>>(emptyList()) }; var query by remember { mutableStateOf("") }; val activity = LocalContext.current as OnboardingActivity; val transliterator = remember { Transliterator.getInstance("Han-Latin; Latin-ASCII") }
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { DBUtils.addAllInstalledPackageNames(); val installed = AppListCache.getInstalledUserApps().map { it.packageName }.toSet(); apps = DBUtils.allPackageName().filter { it.pkg in installed }.sortedBy { it.name ?: it.pkg } } }
    val filtered = apps.filter { val q = transliterator.transliterate(query).lowercase(); q.isBlank() || (it.name ?: it.pkg).lowercase().contains(q) || it.pkg.lowercase().contains(q) }
    Column { OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_apps)) }, modifier = Modifier.fillMaxWidth()); LazyColumn { items(filtered, key = { it.pkg }) { app -> var selected by remember(app.pkg) { mutableStateOf(app.isWhiteList) }; ListItem(headlineContent = { Text(app.name ?: app.pkg) }, supportingContent = { Text(app.pkg) }, trailingContent = { Switch(selected, { value -> selected = value; app.isWhiteList = value; MyApplication.executor.execute { try { val entity = DBUtils.getPackageName(app.pkg) ?: app; entity.isWhiteList = value; entity.isBlackList = false; MyApplication.packageNames.put(entity) } catch (cause: Throwable) { activity.runOnUiThread { activity.showError(cause) } } } }) }) } } }
}
