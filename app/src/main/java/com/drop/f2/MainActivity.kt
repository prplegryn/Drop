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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Paper = Color(0xFFF4EFE6)
private val Panel = Color(0xFFECE5DA)
private val Input = Color(0xFFE5DED2)
private val Button = Color(0xFFDCD3C5)
private val Ink = Color(0xFF27231D)
private val MutedInk = Color(0xFF7D7468)
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
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val targetDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
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
    val terminalLines = remember { mutableStateListOf<String>() }

    var mode by rememberSaveable { mutableStateOf(DropMode.One) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var cookie by rememberSaveable { mutableStateOf(prefs.getString("cookie", "") ?: "") }
    var showCookieDialog by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }

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
                    .padding(horizontal = 18.dp)
                    .background(Paper, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                InputConsoleBar(
                    mode = mode,
                    input = input,
                    running = running,
                    onModeClick = {
                        mode = if (mode == DropMode.One) DropMode.Post else DropMode.One
                    },
                    onInputChange = { input = it },
                    onPaste = {
                        clipboard.getText()?.text?.let {
                            input = TextFieldValue(it)
                        }
                    },
                    onCookie = { showCookieDialog = true },
                    onDownload = {
                        if (running) return@InputConsoleBar

                        val url = extractDouyinUrl(input.text)
                        terminalLines.clear()

                        if (url.isBlank()) {
                            terminalLines.appendTerminalLine(dashboardLine("STATUS", "未找到抖音链接"))
                            return@InputConsoleBar
                        }

                        if (cookie.isBlank()) {
                            terminalLines.appendTerminalLine(dashboardLine("STATUS", "未设置 Cookie"))
                            return@InputConsoleBar
                        }

                        terminalLines.appendTerminalLine(dashboardLine("F2 DOWNLOAD ENGINE"))
                        terminalLines.appendTerminalLine(dashboardLine("PLATFORM", "Douyin"))
                        terminalLines.appendTerminalLine(dashboardLine("MODE", mode.f2Mode))
                        terminalLines.appendTerminalLine(dashboardLine("STATE", "运行中"))
                        terminalLines.appendTerminalLine(
                            dashboardLine(
                                "COMMAND",
                                "python -m f2 dy -M ${mode.f2Mode} -u $url --cookie <COOKIE>",
                            ),
                        )

                        running = true
                        scope.launch {
                            val success = runner.run(mode.f2Mode, url, cookie) { line ->
                                terminalLines.appendTerminalLine(line)
                            }
                            running = false
                            terminalLines.appendFinalStatus(if (success) "下载完成" else "下载失败")
                        }
                    },
                )

                TerminalPane(
                    lines = terminalLines,
                    modifier = Modifier.weight(1f),
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
}

@Composable
private fun InputConsoleBar(
    mode: DropMode,
    input: TextFieldValue,
    running: Boolean,
    onModeClick: () -> Unit,
    onInputChange: (TextFieldValue) -> Unit,
    onPaste: () -> Unit,
    onCookie: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Input),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(mode = mode, onClick = onModeClick)
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            cursorBrush = SolidColor(Ink),
            textStyle = TextStyle(
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 17.dp),
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
        SquareIconButton(
            icon = Icons.Rounded.ContentPaste,
            enabled = !running,
            contentDescription = "粘贴",
            onClick = onPaste,
        )
        Spacer(Modifier.width(8.dp))
        SquareIconButton(
            icon = Icons.Rounded.Cookie,
            enabled = !running,
            contentDescription = "Cookie",
            onClick = onCookie,
        )
        Spacer(Modifier.width(8.dp))
        SquareIconButton(
            icon = Icons.Rounded.Download,
            enabled = !running,
            contentDescription = "下载",
            onClick = onDownload,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ModeButton(mode: DropMode, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(Button)
            .clickable(onClick = onClick),
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
                fontSize = if (currentMode == DropMode.One) 25.sp else 23.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SquareIconButton(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(Button)
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
private fun TerminalPane(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            item {
                Text(
                    text = "█",
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.alpha(cursorAlpha),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Paper,
                        0.72f to Paper.copy(alpha = 0.72f),
                        1f to Paper.copy(alpha = 0f),
                    ),
                ),
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
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Button)
                        .clickable { onSave(draft.text) },
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
    }
}

private fun SnapshotStateList<String>.appendTerminalLine(line: String) {
    val clean = line.trimEnd()
    if (clean.isBlank()) return

    for (label in listOf("Transfer", "Speed", "ETA")) {
        val existingIndex = indexOfLast { it.contains(label) }
        if (clean.contains(label) && existingIndex >= 0) {
            this[existingIndex] = clean
            return
        }
    }

    add(clean)
    if (size > 260) {
        removeRange(0, size - 260)
    }
}

private fun SnapshotStateList<String>.appendFinalStatus(status: String) {
    removeAll { line ->
        line.contains("STATUS") && (line.contains("下载完成") || line.contains("下载失败"))
    }
    appendTerminalLine(dashboardLine("STATUS", status))
}

private fun extractDouyinUrl(text: String): String {
    var url = DouyinUrlRegex.find(text)?.value ?: return ""
    while (url.isNotEmpty() && TrailingUrlChars.contains(url.last())) {
        url = url.dropLast(1)
    }
    return url
}

private fun dashboardLine(label: String, value: String = ""): String {
    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
    return if (value.isBlank()) {
        "[$timestamp] $label"
    } else {
        "[$timestamp] ${label.padEnd(13, ' ')}: $value"
    }
}
