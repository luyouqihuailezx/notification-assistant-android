package com.android.alftendev.activities.adaptersactivity

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.Drawable
import android.icu.text.Transliterator
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalMaterial3Api::class)
class BlackWhiteListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContent {
            NotificationListenerTheme {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.notification_collection_apps)) }) }) { padding ->
                    AppSelectionContent(Modifier.padding(padding).fillMaxSize()) { showOperationError(it) }
                }
            }
        }
        onBackPressedDispatcher.addCallback { finishAndRemoveTask() }
    }

    private fun showOperationError(error: Throwable) {
        val details = error.stackTraceToString()
        Log.e("BlackWhiteListActivity", "应用选择操作失败", error)
        val clipboard = getSystemService(ClipboardManager::class.java)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.operation_failed)
            .setMessage(details)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details))
            }
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSelectionContent(modifier: Modifier = Modifier, onError: (Throwable) -> Unit) {
    val context = LocalContext.current
    val transliterator = remember { Transliterator.getInstance("Han-Latin; Latin-ASCII") }
    var apps by remember { mutableStateOf<List<PackageName>>(emptyList()) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var appPendingRemoval by remember { mutableStateOf<PackageName?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun normalize(value: String) = transliterator.transliterate(value).replace(Regex("[^A-Za-z0-9]"), "").lowercase()
    fun name(app: PackageName) = app.name?.takeIf { it.isNotBlank() } ?: app.pkg
    fun section(app: PackageName) = normalize(name(app)).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
    val comparator = compareBy<PackageName>({ normalize(name(it)) }, { it.pkg.lowercase() })
    val filtered = remember(apps, query, selectionRevision) {
        val normalized = normalize(query)
        apps.filter { normalized.isBlank() || normalize(name(it)).contains(normalized) || it.pkg.lowercase().contains(normalized) }.sortedWith(comparator)
    }
    val sections = remember(filtered) { filtered.map(::section).distinct() }

    fun refreshApps() {
        loading = true
        MyApplication.executor.execute {
            try {
                DBUtils.addAllInstalledPackageNames()
                val installed = AppListCache.getInstalledUserApps().map { it.packageName }.toSet()
                val result = DBUtils.allPackageName().filter { it.pkg in installed }.sortedWith(comparator)
                (context as? AppCompatActivity)?.runOnUiThread { apps = result; selectionRevision++; loading = false }
            } catch (error: Throwable) {
                Log.e("AppSelection", "应用列表加载失败", error)
                (context as? AppCompatActivity)?.runOnUiThread { loading = false; onError(error) }
            }
        }
    }
    fun update(app: PackageName, selected: Boolean) {
        val prior = app.isWhiteList
        app.isWhiteList = selected
        app.isBlackList = false
        selectionRevision++
        MyApplication.executor.execute {
            try {
                val entity = DBUtils.getPackageName(app.pkg) ?: app
                entity.isWhiteList = selected
                entity.isBlackList = false
                MyApplication.packageNames.put(entity)
                PackageSettingsCache.updatePackage(entity.pkg, false, selected)
            } catch (error: Throwable) {
                (context as? AppCompatActivity)?.runOnUiThread {
                    app.isWhiteList = prior
                    selectionRevision++
                    onError(error)
                }
            }
        }
    }
    fun setAll(selected: Boolean) {
        apps.forEach { it.isWhiteList = selected; it.isBlackList = false }
        selectionRevision++
        MyApplication.executor.execute {
            try { MyApplication.packageNames.put(apps); PackageSettingsCache.initCache() }
            catch (error: Throwable) { (context as? AppCompatActivity)?.runOnUiThread { onError(error); refreshApps() } }
        }
    }
    LaunchedEffect(Unit) { refreshApps() }

    Column(modifier) {
        val selected = apps.filter { it.isWhiteList }
        if (selected.isNotEmpty()) {
            Text(stringResource(R.string.added_apps), Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().height(56.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { app -> SelectedAppIcon(app) { appPendingRemoval = app } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.search_apps)) }, singleLine = true)
            Box {
                IconButton(onClick = { actionsExpanded = true }) { Icon(painterResource(R.drawable.baseline_settings_24), stringResource(R.string.select_all)) }
                DropdownMenu(actionsExpanded, { actionsExpanded = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.select_all)) }, { actionsExpanded = false; setAll(true) })
                    DropdownMenuItem({ Text(stringResource(R.string.deselect_all)) }, { actionsExpanded = false; setAll(false) })
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(92.dp), state = gridState,
                contentPadding = PaddingValues(start = 8.dp, end = 36.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filtered.forEachIndexed { index, app ->
                    val currentSection = section(app)
                    if (index == 0 || section(filtered[index - 1]) != currentSection) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header_$currentSection") { Text(currentSection, Modifier.padding(top = 8.dp, bottom = 2.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                    }
                    item(key = app.pkg) { AppCard(app, name(app)) { update(app, !app.isWhiteList) } }
                }
            }
            if (sections.isNotEmpty()) {
                AlphabetIndex(sections, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)) { target ->
                    val appIndex = filtered.indexOfFirst { section(it) == target }
                    if (appIndex >= 0) {
                        val gridIndex = appIndex + filtered.take(appIndex).map(::section).distinct().size
                        scope.launch { gridState.scrollToItem(gridIndex) }
                    }
                }
            }
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
    appPendingRemoval?.let { app ->
        AlertDialog(
            onDismissRequest = { appPendingRemoval = null },
            title = { Text(stringResource(R.string.remove_added_app)) },
            text = { Text(name(app)) },
            confirmButton = {
                TextButton(onClick = { update(app, false); appPendingRemoval = null }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { appPendingRemoval = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectedAppIcon(app: PackageName, onRemove: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.size(48.dp).combinedClickable(onClick = onRemove, onLongClick = onRemove), contentAlignment = Alignment.Center) {
        LazyAppIcon(app.pkg, Modifier.size(40.dp), app.pkg)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCard(app: PackageName, name: String, onToggle: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().height(104.dp).combinedClickable(onClick = onToggle, onLongClick = onToggle), colors = CardDefaults.cardColors(containerColor = if (app.isWhiteList) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxSize()) {
            Checkbox(app.isWhiteList, onCheckedChange = { onToggle() }, Modifier.align(Alignment.TopStart))
            Column(Modifier.fillMaxSize().padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    LazyAppIcon(app.pkg, Modifier.size(38.dp), app.pkg)
                }
                Spacer(Modifier.height(4.dp))
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val appIconCache = ConcurrentHashMap<String, Drawable>()

@Composable
private fun LazyAppIcon(packageName: String, modifier: Modifier, contentDescription: String?) {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = appIconCache[packageName], packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                AppIcon.compute(packageName)?.also { appIconCache[packageName] = it }
            } catch (error: Throwable) {
                Log.e("AppSelection", "应用图标加载失败：$packageName", error)
                null
            }
        }
    }
    Image(
        painter = rememberDrawablePainter(icon ?: context.getDrawable(R.drawable.baseline_android_24)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun AlphabetIndex(sections: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    fun selectAt(y: Float, height: Int) { if (height > 0) onSelect(sections[(y / height * sections.size).toInt().coerceIn(sections.indices)]) }
    Column(
        modifier
            .pointerInput(sections) { detectTapGestures { selectAt(it.y, size.height) } }
            .pointerInput(sections) { detectVerticalDragGestures(onDragStart = { selectAt(it.y, size.height) }, onVerticalDrag = { change, _ -> selectAt(change.position.y, size.height) }) },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        sections.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
