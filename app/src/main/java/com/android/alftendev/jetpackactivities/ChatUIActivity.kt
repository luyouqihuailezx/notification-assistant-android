package com.android.alftendev.jetpackactivities

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.composes.ChatScreen
import com.android.alftendev.composes.SearchAndFilterContainer
import com.android.alftendev.composes.utils.MyTheme
import com.android.alftendev.models.Notifications
import com.android.alftendev.utils.AuthUtils
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.computables.AppIcon
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatUIActivity : ComponentActivity() {
    private val pkgName by lazy {
        intent.extras?.getString("pkgName", MyApplication.defaultSwValue) ?: MyApplication.defaultSwValue
    }
    private val chatTitle by lazy { intent.extras?.getString("title", "") ?: "" }
    private val viewModel by viewModels<ChatViewModel> {
        ChatViewModel.Factory(pkgName, chatTitle)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthUtils.askAuth(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            MyTheme {
                ChatPage(chatTitle = chatTitle, pkgName = pkgName, viewModel = viewModel)
            }
        }
    }
}

data class ChatUiState(
    val isLoading: Boolean = true,
    val messages: List<Notifications> = emptyList(),
    val errorDetails: String? = null
)

class ChatViewModel(
    private val pkgName: String,
    private val chatTitle: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorDetails = null)
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorDetails = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { DBUtils.searchChat(pkgName, chatTitle) }
            }.onSuccess { messages ->
                _uiState.value = ChatUiState(isLoading = false, messages = messages)
            }.onFailure { throwable ->
                _uiState.value = ChatUiState(
                    isLoading = false,
                    errorDetails = throwable.stackTraceToString()
                )
            }
        }
    }

    class Factory(
        private val pkgName: String,
        private val chatTitle: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(pkgName, chatTitle) as T
        }
    }
}

@Composable
private fun ChatPage(chatTitle: String, pkgName: String, viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    when {
        state.isLoading -> ChatScaffold(chatTitle, pkgName) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(LocalContext.current.getString(R.string.loading))
            }
        }
        state.errorDetails != null -> ChatScaffold(chatTitle, pkgName) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("无法加载聊天记录")
                Button(onClick = viewModel::retry) { Text("重试") }
            }
        }
        else -> ChatContent(chatTitle, pkgName, state.messages)
    }
    state.errorDetails?.let { details ->
        ChatLoadErrorDialog(details = details, dismiss = viewModel::dismissError)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScaffold(
    chatTitle: String,
    pkgName: String,
    content: @Composable (PaddingValues) -> Unit
) {
    val icon = AppIcon.compute(pkgName)
    val painterIcon = if (icon != null) {
        rememberDrawablePainter(icon)
    } else {
        painterResource(R.mipmap.ic_launcher_foreground)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = chatTitle,
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = colorScheme.tertiary
                    )
                },
                navigationIcon = {
                    Image(
                        painter = painterIcon,
                        contentDescription = pkgName,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.primaryContainer,
                    titleContentColor = colorScheme.tertiary
                )
            )
        },
        containerColor = colorScheme.background,
        content = content
    )
}

@Composable
private fun ChatContent(chatTitle: String, pkgName: String, messages: List<Notifications>) {
    ChatScaffold(chatTitle, pkgName) { innerPadding ->
        SearchAndFilterContainer(
            context = LocalContext.current,
            allNotifications = messages,
            getPackageName = { it.packageName.target.pkg },
            isDeleted = { it.isDeleted },
            notificationText = { it.text },
            modifier = Modifier.padding(innerPadding)
        ) { filteredMessages ->
            ChatScreen(messages = filteredMessages, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ChatLoadErrorDialog(details: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("聊天记录加载失败") },
        text = { Text(details) },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("错误详情", details))
            }) {
                Text(context.getString(R.string.copy_error))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(context.getString(R.string.back)) }
        }
    )
}
