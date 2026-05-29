package com.drop.f2

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Paper = Color(0xFFF4EFE6)
private val Panel = Color(0xFFECE5DA)
private val Input = Color(0xFFE5DED2)
private val Ink = Color(0xFF27231D)
private val MutedInk = Color(0xFF7D7468)
private val CursorRun = Color(0xFFD9A928)
private val CursorOk = Color(0xFF407D4D)
private val CursorError = Color(0xFFB6453C)
private val DouyinUrlRegex = Regex("https?://(?:[\\w.-]+\\.)?douyin\\.com/[^\\s，。；;]+/?")
private val TrailingUrlChars = setOf(',', '，', '。', ';', '；', '、', '!', '！', '?', '？', ')', '）', ']', '】', '>', '》', '"', '\'')

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DropTheme {
                DropScreen()
            }
        }
    }
}

@Keep
class LineSink(private val deliver: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())

    @Keep
    fun onLine(line: String) {
        handler.post { deliver(line) }
    }
}

class F2Runner(context: Context) {
    private val appContext = context.applicationContext

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
    }

    suspend fun run(
        mode: String,
        text: String,
        cookie: String,
        downloadFolder: String,
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val fallbackDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        val targetDir = File(downloadFolder.ifBlank { fallbackDir.absolutePath })
        val result = Python.getInstance()
            .getModule("drop_android_runner")
            .callAttr("run", mode, text, cookie, targetDir.absolutePath, LineSink(onLine))
        result.toBoolean()
    }
}

private enum class DropMode(val f2Mode: String) {
    One("one"),
    Post("post"),
}

private enum class CursorTone {
    Idle,
    Running,
    Success,
    Error,
}

@Composable
private fun DropTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Paper,
            surface = Panel,
            primary = Ink,
            onPrimary = Paper,
            onSurface = Ink,
        ),
        content = content,
    )
}

@Composable
private fun DropScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val runner = remember { F2Runner(context) }
    val prefs = remember { context.getSharedPreferences("drop", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val defaultDownloadFolder = remember {
        (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir).absolutePath
    }

    var mode by rememberSaveable { mutableStateOf(DropMode.One) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var cookie by rememberSaveable { mutableStateOf(prefs.getString("cookie", "") ?: "") }
    var downloadFolder by rememberSaveable {
        mutableStateOf(prefs.getString("download_folder", defaultDownloadFolder) ?: defaultDownloadFolder)
    }
    var showCookieDialog by rememberSaveable { mutableStateOf(false) }
    var showFolderDialog by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var cursorTone by rememberSaveable { mutableStateOf(CursorTone.Idle) }

    fun flashCursor(tone: CursorTone) {
        scope.launch {
            cursorTone = tone
            delay(2000)
            if (!running && cursorTone == tone) {
                cursorTone = CursorTone.Idle
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Paper,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper),
            contentAlignment = Alignment.Center,
        ) {
            val panelHeight = maxHeight / 3f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .padding(horizontal = 18.dp),
            ) {
                StatusCursor(tone = cursorTone)
                Spacer(Modifier.height(4.dp))
                SingleLineInputField(
                    input = input,
                    onInputChange = { input = it },
                )
                Spacer(Modifier.height(4.dp))
                ToolRow(
                    mode = mode,
                    running = running,
                    onModeClick = {
                        mode = if (mode == DropMode.One) DropMode.Post else DropMode.One
                    },
                    onPaste = {
                        clipboard.getText()?.text?.let {
                            input = TextFieldValue(it)
                        }
                    },
                    onCookie = { showCookieDialog = true },
                    onFolder = { showFolderDialog = true },
                    onDownload = {
                        if (running) return@ToolRow

                        val url = extractDouyinUrl(input.text)
                        if (url.isBlank() || cookie.isBlank()) {
                            flashCursor(CursorTone.Error)
                            return@ToolRow
                        }

                        running = true
                        cursorTone = CursorTone.Running
                        scope.launch {
                            val success = runner.run(
                                mode = mode.f2Mode,
                                text = url,
                                cookie = cookie,
                                downloadFolder = downloadFolder,
                            ) {
                                // F2 output is intentionally consumed without rendering.
                            }

                            running = false
                            flashCursor(if (success) CursorTone.Success else CursorTone.Error)
                        }
                    },
                )
            }
        }
    }

    if (showCookieDialog) {
        CookieDialog(
            initialCookie = cookie,
            onDismiss = { showCookieDialog = false },
            onSave = {
                cookie = it
                prefs.edit().putString("cookie", it).apply()
                showCookieDialog = false
            },
        )
    }

    if (showFolderDialog) {
        FolderDialog(
            initialFolder = downloadFolder,
            onDismiss = { showFolderDialog = false },
            onSave = {
                val targetFolder = it.ifBlank { defaultDownloadFolder }
                downloadFolder = targetFolder
                prefs.edit().putString("download_folder", targetFolder).apply()
                showFolderDialog = false
            },
        )
    }
}

@Composable
private fun StatusCursor(tone: CursorTone) {
    val blink = rememberInfiniteTransition(label = "cursorBlink")
    val blinkAlpha by blink.animateFloat(
        initialValue = 0.24f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    val targetColor = when (tone) {
        CursorTone.Idle -> Ink
        CursorTone.Running -> CursorRun
        CursorTone.Success -> CursorOk
        CursorTone.Error -> CursorError
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(220),
        label = "cursorColor",
    )
    val alpha = if (tone == CursorTone.Success || tone == CursorTone.Error) 1f else blinkAlpha

    Text(
        text = "█",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp)
            .alpha(alpha),
    )
}

@Composable
private fun SingleLineInputField(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
) {
    val leftFadeVisible = input.text.length > 24 && input.selection.start > 0
    val rightFadeVisible = input.text.length > 24

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Input),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            singleLine = true,
            cursorBrush = SolidColor(Ink),
            textStyle = TextStyle(
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            decorationBox = { innerTextField ->
                if (input.text.isBlank()) {
                    Text(
                        text = "https://v.douyin.com/...",
                        color = MutedInk,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    )
                }
                innerTextField()
            },
        )

        if (leftFadeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .size(width = 14.dp, height = 58.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Input,
                            1f to Input.copy(alpha = 0f),
                        ),
                    ),
            )
        }

        if (rightFadeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .size(width = 14.dp, height = 58.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Input.copy(alpha = 0f),
                            1f to Input,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ToolRow(
    mode: DropMode,
    running: Boolean,
    onModeClick: () -> Unit,
    onPaste: () -> Unit,
    onCookie: () -> Unit,
    onFolder: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIcon(mode = mode, enabled = !running, onClick = onModeClick)
        PlainIconButton(
            icon = Icons.Rounded.ContentPaste,
            enabled = !running,
            contentDescription = "粘贴",
            onClick = onPaste,
        )
        PlainIconButton(
            icon = Icons.Rounded.Cookie,
            enabled = !running,
            contentDescription = "Cookie",
            onClick = onCookie,
        )
        PlainIconButton(
            icon = Icons.Rounded.FolderOpen,
            enabled = !running,
            contentDescription = "保存文件夹",
            onClick = onFolder,
        )
        PlainIconButton(
            icon = Icons.Rounded.Download,
            enabled = !running,
            contentDescription = "下载",
            onClick = onDownload,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ModeIcon(
    mode: DropMode,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(120)) + scaleIn(initialScale = 0.78f, animationSpec = tween(120)))
                    .togetherWith(fadeOut(animationSpec = tween(80)) + scaleOut(targetScale = 1.12f, animationSpec = tween(80)))
            },
            label = "mode",
        ) { currentMode ->
            Text(
                text = if (currentMode == DropMode.One) "●" else "∴",
                color = Ink,
                fontSize = if (currentMode == DropMode.One) 22.sp else 21.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlainIconButton(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Ink,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun CookieDialog(
    initialCookie: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialCookie))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(8.dp))
                .padding(18.dp),
        ) {
            Text(
                text = "COOKIE",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                cursorBrush = SolidColor(Ink),
                textStyle = TextStyle(
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Input)
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    if (draft.text.isBlank()) {
                        Text(
                            text = "Cookie",
                            color = MutedInk,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    innerTextField()
                },
            )
            DialogActions(onDismiss = onDismiss) {
                onSave(draft.text)
            }
        }
    }
}

@Composable
private fun FolderDialog(
    initialFolder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialFolder))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(8.dp))
                .padding(18.dp),
        ) {
            Text(
                text = "SAVE FOLDER",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                cursorBrush = SolidColor(Ink),
                textStyle = TextStyle(
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Input)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
            DialogActions(onDismiss = onDismiss) {
                onSave(draft.text)
            }
        }
    }
}

@Composable
private fun DialogActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "取消",
            color = MutedInk,
            fontSize = 13.sp,
            modifier = Modifier
                .height(42.dp)
                .padding(horizontal = 14.dp)
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Done,
                contentDescription = "保存",
                tint = Ink,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun extractDouyinUrl(text: String): String {
    var url = DouyinUrlRegex.find(text)?.value ?: return ""
    while (url.isNotEmpty() && TrailingUrlChars.contains(url.last())) {
        url = url.dropLast(1)
    }
    return url
}
