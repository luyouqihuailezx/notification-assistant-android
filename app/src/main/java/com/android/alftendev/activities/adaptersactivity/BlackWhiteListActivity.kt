package com.android.alftendev.activities.adaptersactivity

import android.content.ClipData
import android.content.ClipboardManager
import android.icu.text.Transliterator
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.models.PackageName
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.computables.AppIcon
import com.android.alftendev.utils.computables.AppListCache
import com.android.alftendev.utils.computables.PackageSettingsCache
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class BlackWhiteListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContent { NotificationListenerTheme { BlackWhiteListScreen() } }
        onBackPressedDispatcher.addCallback { finishAndRemoveTask() }
    }

    @Composable
    private fun BlackWhiteListScreen() {
        val context = LocalContext.current
        val transliterator = remember { Transliterator.getInstance("Han-Latin; Latin-ASCII") }
        var allApps by remember { mutableStateOf<List<PackageName>>(emptyList()) }
        var query by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var menuExpanded by remember { mutableStateOf(false) }

        fun normalize(value: String) = transliterator.transliterate(value)
            .replace(Regex("[^A-Za-z0-9]"), "").lowercase()
        fun displayName(item: PackageName) = item.name?.takeIf { it.isNotBlank() } ?: item.pkg
        fun sectionFor(item: PackageName) = normalize(displayName(item)).firstOrNull()
            ?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
        val comparator = compareBy<PackageName>({ normalize(displayName(it)) }, { it.pkg.lowercase() })
        val filtered = remember(allApps, query) {
            val normalizedQuery = normalize(query)
            allApps.filter { normalizedQuery.isBlank() || normalize(displayName(it)).contains(normalizedQuery) || it.pkg.lowercase().contains(normalizedQuery) }
                .sortedWith(comparator)
        }
        val sections = remember(filtered) {
            filtered.map { sectionFor(it) }.distinct()
        }
        val gridState = rememberLazyGridState()
        val scrollScope = rememberCoroutineScope()

        fun report(throwable: Throwable) {
            Log.e("BlackWhiteListActivity", "黑白名单操作失败", throwable)
            error = throwable.stackTraceToString()
        }
        fun loadApps() {
            loading = true
            MyApplication.executor.execute {
                try {
                    DBUtils.addAllInstalledPackageNames()
                    val installed = AppListCache.getInstalledUserApps().map { it.packageName }.toSet()
                    val result = DBUtils.allPackageName().filter { it.pkg in installed }.sortedWith(comparator)
                    runOnUiThread { allApps = result; loading = false }
                } catch (throwable: Throwable) {
                    Log.e("BlackWhiteListActivity", "应用列表加载失败", throwable)
                    runOnUiThread { loading = false; error = throwable.stackTraceToString() }
                }
            }
        }
        fun update(app: PackageName, selected: Boolean) {
            app.isWhiteList = selected
            app.isBlackList = false
            MyApplication.executor.execute {
                try {
                    val entity = DBUtils.getPackageName(app.pkg) ?: app
                    entity.isWhiteList = selected
                    entity.isBlackList = false
                    MyApplication.packageNames.put(entity)
                    PackageSettingsCache.updatePackage(entity.pkg, false, selected)
                } catch (throwable: Throwable) {
                    runOnUiThread {
                        app.isWhiteList = !selected
                        report(throwable)
                    }
                }
            }
        }
        fun setAllSelected(selected: Boolean) {
            MyApplication.executor.execute {
                try {
                    allApps.forEach { it.isWhiteList = selected; it.isBlackList = false }
                    MyApplication.packageNames.put(allApps)
                    PackageSettingsCache.initCache()
                    runOnUiThread { allApps = allApps.toList() }
                } catch (throwable: Throwable) { runOnUiThread { report(throwable) } }
            }
        }
        fun removeSelected(app: PackageName) {
            MyApplication.executor.execute {
                try {
                    val entity = DBUtils.getPackageName(app.pkg) ?: app
                    entity.isWhiteList = false
                    entity.isBlackList = false
                    MyApplication.packageNames.put(entity)
                    PackageSettingsCache.updatePackage(entity.pkg, false, false)
                    runOnUiThread { app.isWhiteList = false; allApps = allApps.toList() }
                } catch (throwable: Throwable) { runOnUiThread { report(throwable) } }
            }
        }
        LaunchedEffect(Unit) { loadApps() }

        Scaffold(topBar = { TopAppBar(
            title = { Text(context.getString(R.string.notification_collection_apps)) },
            actions = { Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(painterResource(R.drawable.baseline_settings_24), context.getString(R.string.select_all)) }
                DropdownMenu(menuExpanded, { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(context.getString(R.string.select_all)) }, onClick = { menuExpanded = false; setAllSelected(true) })
                    DropdownMenuItem(text = { Text(context.getString(R.string.deselect_all)) }, onClick = { menuExpanded = false; setAllSelected(false) })
                }
            } }
        ) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val selected = allApps.filter { it.isWhiteList }
                if (selected.isNotEmpty()) {
                    Text(context.getString(R.string.added_apps), Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.forEach { app -> SelectedAppIcon(app) { removeSelected(app) } }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), label = { Text(context.getString(R.string.search_apps)) }, singleLine = true)
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(GridCells.Adaptive(88.dp), state = gridState, contentPadding = PaddingValues(start = 8.dp, end = 36.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.pkg }) { app ->
                            val section = sectionFor(app)
                            Column {
                                if (filtered.indexOf(app) == filtered.indexOfFirst { sectionFor(it) == section }) Text(section, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge)
                                AppCard(app, displayName(app), { update(app, !app.isWhiteList) })
                            }
                        }
                    }
                    if (sections.isNotEmpty()) AlphabetIndex(sections, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)) { section ->
                        val index = filtered.indexOfFirst { sectionFor(it) == section }
                        if (index >= 0) scrollScope.launch { gridState.animateScrollToItem(index) }
                    }
                    if (loading) Text("正在加载…", Modifier.align(Alignment.Center))
                }
            }
        }
        error?.let { ErrorDialog("操作失败", it) { error = null } }
    }
}

@Composable
private fun SelectedAppIcon(app: PackageName, onRemove: () -> Unit) {
    val context = LocalContext.current
    Image(rememberDrawablePainter(AppIcon.compute(app.pkg) ?: context.getDrawable(R.drawable.baseline_android_24)), app.pkg, Modifier.size(48.dp).combinedClickable(onClick = {}, onLongClick = onRemove), contentScale = ContentScale.Fit)
}

@Composable
private fun AppCard(app: PackageName, name: String, onToggle: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onToggle), colors = CardDefaults.cardColors(containerColor = if (app.isWhiteList) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(rememberDrawablePainter(AppIcon.compute(app.pkg) ?: context.getDrawable(R.drawable.baseline_android_24)), app.pkg, Modifier.size(40.dp))
            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AlphabetIndex(sections: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    Column(modifier.pointerInput(sections) { detectTapGestures { offset -> onSelect(sections[(offset.y / size.height * sections.size).toInt().coerceIn(sections.indices)]) } }, verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        sections.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ErrorDialog(title: String, details: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) }, confirmButton = { TextButton(onClick = { (context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newPlainText("错误详情", details)) }) { Text(context.getString(R.string.copy_error)) } }, dismissButton = { TextButton(dismiss) { Text("关闭") } })
}
