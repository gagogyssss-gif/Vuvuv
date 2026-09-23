package com.vuvuv.framecut

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class AppTab { Recorder, Settings }
private enum class PreviewMode { Empty, Player, Web }

@OptIn(markerClass = [UnstableApi::class])
@Composable
fun FrameCutApp() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val exporter = remember { ClipExporter(context.applicationContext) }

    var tab by remember { mutableStateOf(AppTab.Recorder) }
    var mode by remember { mutableStateOf(PreviewMode.Empty) }
    var input by remember { mutableStateOf("") }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var detectedMedia by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(ExportSettings()) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startText by remember { mutableStateOf("00:00") }
    var endText by remember { mutableStateOf("00:10") }
    var markStart by remember { mutableStateOf<Long?>(null) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var status by remember { mutableStateOf("Вставь ссылку или выбери видео") }
    var exporting by remember { mutableStateOf(false) }
    var lastOutput by remember { mutableStateOf<Uri?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height.toFloat()
                    if (!settings.manualCrop) {
                        settings = settings.copy(crop = defaultCropFor(videoAspect, settings.aspect.ratio, settings.cropZoom))
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            exporter.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            currentMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            durationMs = if (d > 0 && d < Long.MAX_VALUE) d else 0L
            delay(200)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            input = uri.toString()
            sourceUri = uri
            webUrl = null
            detectedMedia = null
            mode = PreviewMode.Player
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            if (settings.autoplay) player.play()
            status = "Локальное видео открыто"
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF68C8FF),
            secondary = Color(0xFF9DDCFF),
            background = Color(0xFF05070A),
            surface = Color(0xFF0D141D),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Color(0xFF05070A),
            bottomBar = {
                Surface(color = Color(0xF0080C12), shadowElevation = 10.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NavButton("Запись", tab == AppTab.Recorder, Modifier.weight(1f)) { tab = AppTab.Recorder }
                        NavButton("Настройки", tab == AppTab.Settings, Modifier.weight(1f)) { tab = AppTab.Settings }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF05070A))) {
                if (tab == AppTab.Recorder) {
                    RecorderScreen(
                        player = player,
                        input = input,
                        onInput = { input = it },
                        mode = mode,
                        webUrl = webUrl,
                        detectedMedia = detectedMedia,
                        onDetected = { detectedMedia = it },
                        settings = settings,
                        onSettings = { settings = it },
                        currentMs = currentMs,
                        durationMs = durationMs,
                        startText = startText,
                        endText = endText,
                        onStartText = { startText = it },
                        onEndText = { endText = it },
                        markStart = markStart,
                        status = status,
                        exporting = exporting,
                        lastOutput = lastOutput,
                        onPick = { picker.launch(arrayOf("video/*")) },
                        onOpen = {
                            val normalized = normalizeUrl(input.trim())
                            if (input.startsWith("content://")) {
                                val uri = Uri.parse(input)
                                sourceUri = uri
                                webUrl = null
                                mode = PreviewMode.Player
                                player.setMediaItem(MediaItem.fromUri(uri))
                                player.prepare()
                                if (settings.autoplay) player.play()
                                status = "Видео открыто"
                            } else if (normalized == null) {
                                status = "Некорректная ссылка"
                            } else if (looksLikeDirectMedia(normalized)) {
                                val uri = Uri.parse(normalized)
                                sourceUri = uri
                                webUrl = null
                                detectedMedia = null
                                mode = PreviewMode.Player
                                player.setMediaItem(MediaItem.fromUri(uri))
                                player.prepare()
                                if (settings.autoplay) player.play()
                                status = "Медиапоток открыт"
                            } else {
                                sourceUri = null
                                webUrl = normalized
                                detectedMedia = null
                                player.stop()
                                mode = PreviewMode.Web
                                status = "Страница открыта. Ищу прямой медиапоток."
                            }
                        },
                        onUseDetected = {
                            val found = detectedMedia
                            if (found != null) {
                                val uri = Uri.parse(found)
                                input = found
                                sourceUri = uri
                                webUrl = null
                                mode = PreviewMode.Player
                                player.setMediaItem(MediaItem.fromUri(uri))
                                player.prepare()
                                if (settings.autoplay) player.play()
                                status = "Найденный поток открыт"
                            }
                        },
                        onMark = {
                            if (sourceUri == null) {
                                status = "Сначала открой видео"
                            } else if (markStart == null) {
                                markStart = currentMs
                                startText = formatTimecode(currentMs)
                                status = "Начало: " + formatTimecode(currentMs) + ". Нажми Стоп в нужный момент."
                            } else {
                                val s = markStart ?: 0L
                                val e = currentMs.coerceAtLeast(s + 1L)
                                endText = formatTimecode(e)
                                markStart = null
                                status = "Фрагмент выбран: " + formatTimecode(s) + " → " + formatTimecode(e)
                            }
                        },
                        onExport = {
                            val src = sourceUri
                            val s = parseTimecode(startText)
                            val e = parseTimecode(endText)
                            if (src == null) {
                                status = "Нет прямого источника видео"
                            } else if (s == null || e == null || e <= s) {
                                status = "Проверь таймкоды начала и конца"
                            } else {
                                exporting = true
                                lastOutput = null
                                exporter.export(
                                    source = src,
                                    startMs = s,
                                    endMs = e,
                                    settings = settings,
                                    onState = { status = it },
                                    onSuccess = { uri ->
                                        exporting = false
                                        lastOutput = uri
                                        status = "Готово. Видео сохранено в Movies/FrameCut"
                                    },
                                    onError = { message ->
                                        exporting = false
                                        status = message
                                    }
                                )
                            }
                        },
                        onCancel = {
                            exporter.cancel()
                            exporting = false
                            status = "Экспорт отменён"
                        }
                    )
                } else {
                    SettingsScreen(settings = settings, onSettings = { settings = it })
                }
            }
        }
    }
}

@OptIn(markerClass = [UnstableApi::class])
@Composable
private fun RecorderScreen(
    player: ExoPlayer,
    input: String,
    onInput: (String) -> Unit,
    mode: PreviewMode,
    webUrl: String?,
    detectedMedia: String?,
    onDetected: (String) -> Unit,
    settings: ExportSettings,
    onSettings: (ExportSettings) -> Unit,
    currentMs: Long,
    durationMs: Long,
    startText: String,
    endText: String,
    onStartText: (String) -> Unit,
    onEndText: (String) -> Unit,
    markStart: Long?,
    status: String,
    exporting: Boolean,
    lastOutput: Uri?,
    onPick: () -> Unit,
    onOpen: () -> Unit,
    onUseDetected: () -> Unit,
    onMark: () -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FrameCut", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text("Чистый исходник → кадры → готовый MP4", fontSize = 12.sp, color = Color(0xFF8CA1B6))

        Glass {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                label = { Text("Ссылка на видео или страницу") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Открыть") }
                OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) { Text("Файл") }
            }
        }

        Glass {
            Text("Предпросмотр", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(settings.aspect.ratio)
                    .background(Color.Black, RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0x445BC9FF), RoundedCornerShape(18.dp))
            ) {
                when (mode) {
                    PreviewMode.Player -> {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    this.player = player
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (settings.manualCrop) {
                            CropOverlay(
                                crop = settings.crop,
                                sensitivity = settings.cropSensitivity,
                                onCrop = { onSettings(settings.copy(crop = it)) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    PreviewMode.Web -> {
                        WebPreview(
                            url = webUrl.orEmpty(),
                            onDetected = onDetected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    PreviewMode.Empty -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Нет видео", color = Color(0xFF6C7E91))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Позиция " + formatTimecode(currentMs) + (if (durationMs > 0) " / " + formatTimecode(durationMs) else ""),
                color = Color(0xFF8CA1B6),
                fontSize = 11.sp
            )
            if (detectedMedia != null && mode == PreviewMode.Web) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onUseDetected, modifier = Modifier.fillMaxWidth()) {
                    Text("Использовать найденный медиапоток")
                }
            }
        }

        Glass {
            Text("Фрагмент", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = onStartText,
                    label = { Text("Начало") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = onEndText,
                    label = { Text("Конец") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onMark, modifier = Modifier.fillMaxWidth()) {
                Text(if (markStart == null) "Начать с текущего кадра" else "Стоп на текущем кадре")
            }
            Text("Можно и вручную: 32:30 → 33:10", color = Color(0xFF8297AA), fontSize = 11.sp)
        }

        Glass {
            Text("Формат", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChipRow(
                items = AspectPreset.entries.map { it.title },
                selected = AspectPreset.entries.indexOf(settings.aspect)
            ) { index ->
                val aspect = AspectPreset.entries[index]
                onSettings(
                    settings.copy(
                        aspect = aspect,
                        crop = defaultCropFor(16f / 9f, aspect.ratio, settings.cropZoom)
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AutoCrop", modifier = Modifier.weight(1f))
                Switch(
                    checked = !settings.manualCrop,
                    onCheckedChange = { enabled ->
                        onSettings(settings.copy(manualCrop = !enabled))
                    }
                )
            }
            Text(
                if (settings.manualCrop) "Рамку можно двигать пальцем. Она не попадёт в видео."
                else "AutoCrop автоматически заполняет выбранный формат.",
                color = Color(0xFF8CA1B6),
                fontSize = 11.sp
            )
        }

        Glass {
            val size = outputSize(settings)
            Text(
                settings.quality.title + " • " + size.first + "×" + size.second + " • до " + settings.maxFps + " fps • " + settings.codec.title,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(status, color = if (exporting) Color(0xFFFFD477) else Color(0xFFA8E9C6), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            if (exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            } else {
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Создать видео", fontWeight = FontWeight.Bold)
                }
            }
            if (lastOutput != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, lastOutput)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Поделиться видео"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Поделиться") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: ExportSettings, onSettings: (ExportSettings) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", fontSize = 28.sp, fontWeight = FontWeight.Black)

        Glass {
            Text("Качество", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChipRow(QualityPreset.entries.map { it.title }, QualityPreset.entries.indexOf(settings.quality)) {
                onSettings(settings.copy(quality = QualityPreset.entries[it]))
            }
            Spacer(Modifier.height(14.dp))
            Text("FPS", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val fps = listOf(24, 30, 60)
            ChipRow(fps.map { it.toString() }, fps.indexOf(settings.maxFps)) {
                onSettings(settings.copy(maxFps = fps[it]))
            }
            Spacer(Modifier.height(14.dp))
            Text("Кодек", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ChipRow(CodecPreset.entries.map { it.title }, CodecPreset.entries.indexOf(settings.codec)) {
                onSettings(settings.copy(codec = CodecPreset.entries[it]))
            }
        }

        Glass {
            ToggleRow("Звук AAC", "Сохранять аудио", settings.keepAudio) {
                onSettings(settings.copy(keepAudio = it))
            }
            ToggleRow("Ручной Crop", "Двигать рамку пальцем", settings.manualCrop) {
                onSettings(settings.copy(manualCrop = it))
            }
            ToggleRow("Автовоспроизведение", "Запускать видео сразу", settings.autoplay) {
                onSettings(settings.copy(autoplay = it))
            }
        }

        Glass {
            Text("Чувствительность рамки", fontWeight = FontWeight.Bold)
            Slider(
                value = settings.cropSensitivity,
                onValueChange = { onSettings(settings.copy(cropSensitivity = it)) },
                valueRange = 0.1f..1.2f
            )
            Text("Zoom Crop", fontWeight = FontWeight.Bold)
            Slider(
                value = settings.cropZoom,
                onValueChange = { onSettings(settings.copy(cropZoom = it)) },
                valueRange = 1f..2.5f
            )
        }

        Glass {
            Text("Как создаётся видео", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "FrameCut не записывает экран. Он читает выбранный участок исходного медиапотока, обрабатывает кадры, применяет crop/масштаб и кодирует новый MP4. Поэтому интерфейс, касания и движение рамки в экспорт не попадают.",
                color = Color(0xFF9CB2C6),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WebPreview(url: String, onDetected: (String) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val scheme = request?.url?.scheme?.lowercase()
                        return scheme != null && scheme != "http" && scheme != "https"
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val candidate = request?.url?.toString().orEmpty()
                        if (looksLikeDirectMedia(candidate)) {
                            Handler(Looper.getMainLooper()).post { onDetected(candidate) }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                loadUrl(url)
            }
        },
        update = { view ->
            if (url.isNotBlank() && view.url != url) view.loadUrl(url)
        }
    )
}

@Composable
private fun CropOverlay(
    crop: CropRectNorm,
    sensitivity: Float,
    onCrop: (CropRectNorm) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.pointerInput(crop, sensitivity) {
            detectDragGestures { change, drag ->
                change.consume()
                val dx = (drag.x / size.width.toFloat()) * sensitivity
                val dy = (drag.y / size.height.toFloat()) * sensitivity
                onCrop(crop.moved(dx, dy))
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = crop.left * size.width
            val top = crop.top * size.height
            val right = crop.right * size.width
            val bottom = crop.bottom * size.height
            val shade = Color.Black.copy(alpha = 0.42f)
            drawRect(shade, size = Size(size.width, top.coerceAtLeast(0f)))
            drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, (size.height - bottom).coerceAtLeast(0f)))
            drawRect(shade, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
            drawRect(shade, topLeft = Offset(right, top), size = Size((size.width - right).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
            drawRect(
                Color(0xFF72D1FF),
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun NavButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF1B6F9E) else Color(0xFF101923),
            contentColor = Color.White
        )
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Glass(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x334CC8FF), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xE6111821),
        shadowElevation = 7.dp
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun ChipRow(items: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, label ->
            Button(
                onClick = { onSelected(index) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (index == selected) Color(0xFF1D78AB) else Color(0xFF17212B)
                )
            ) { Text(label, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF8499AC), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun normalizeUrl(raw: String): String? {
    if (raw.isBlank()) return null
    val value = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://" + raw
    return runCatching {
        val uri = Uri.parse(value)
        if (uri.host.isNullOrBlank()) null else value
    }.getOrNull()
}

private fun looksLikeDirectMedia(url: String): Boolean {
    val lower = url.lowercase()
    val path = runCatching { Uri.parse(url).path.orEmpty().lowercase() }.getOrDefault(lower)
    return path.endsWith(".mp4") ||
        path.endsWith(".m4v") ||
        path.endsWith(".webm") ||
        path.endsWith(".mkv") ||
        path.endsWith(".m3u8") ||
        path.endsWith(".mpd") ||
        lower.contains(".m3u8?") ||
        lower.contains(".mpd?") ||
        lower.contains(".mp4?")
}
