package com.android.alftendev.activities.adaptersactivity

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.models.PackageName
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.computables.AppIcon
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
class IsChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContent { NotificationListenerTheme { IsChatScreen() } }
        onBackPressedDispatcher.addCallback { finishAndRemoveTask() }
    }

    @Composable
    private fun IsChatScreen() {
        var packages by remember { mutableStateOf<List<PackageName>>(emptyList()) }
        var query by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        fun load() {
            loading = true
            MyApplication.executor.execute {
                try {
                    val result = if (query.isBlank()) DBUtils.allPackageNameFromTable().toList() else DBUtils.packageNameSearch(query).toList()
                    runOnUiThread { packages = result; loading = false }
                } catch (throwable: Throwable) {
                    Log.e("IsChatActivity", "聊天标记列表加载失败", throwable)
                    runOnUiThread { loading = false; error = throwable.stackTraceToString() }
                }
            }
        }
        LaunchedEffect(query) { load() }
        Scaffold(topBar = { TopAppBar(title = { Text("聊天应用") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(8.dp), label = { Text(contextString(R.string.search)) }, singleLine = true)
                LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(packages, key = { it.pkg }) { app -> ChatRow(app) { checked ->
                        app.isChat = checked
                        MyApplication.executor.execute {
                            try { val entity = DBUtils.getPackageName(app.pkg) ?: app; entity.isChat = checked; MyApplication.packageNames.put(entity) }
                            catch (throwable: Throwable) { Log.e("IsChatActivity", "聊天标记写入失败", throwable); runOnUiThread { app.isChat = !checked; error = throwable.stackTraceToString() } }
                        }
                    } }
                }
                if (loading) Text("正在加载…", Modifier.align(Alignment.CenterHorizontally))
            }
        }
        error?.let { details ->
            val context = LocalContext.current
            AlertDialog(onDismissRequest = { error = null }, title = { Text("操作失败") }, text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) }, confirmButton = { TextButton(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("错误详情", details)) }) { Text(contextString(R.string.copy_error)) } }, dismissButton = { TextButton({ error = null }) { Text("关闭") } })
        }
    }

    @Composable private fun contextString(id: Int) = getString(id)
}

@Composable
private fun ChatRow(app: PackageName, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(rememberDrawablePainter(AppIcon.compute(app.pkg) ?: context.getDrawable(R.drawable.baseline_android_24)), app.pkg, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.name?.takeIf { it.isNotBlank() } ?: app.pkg, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Switch(app.isChat, onCheckedChange)
        }
    }
}
