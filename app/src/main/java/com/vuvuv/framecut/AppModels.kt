package com.vuvuv.framecut

import androidx.media3.common.MimeTypes
import kotlin.math.roundToInt

enum class AspectPreset(val title: String, val ratio: Float) {
    Shorts("9:16 Shorts", 9f / 16f),
    Square("1:1", 1f),
    Landscape("16:9", 16f / 9f)
}

enum class QualityPreset(val title: String, val shortSide: Int) {
    HD("720p", 720),
    FHD("1080p", 1080),
    QHD("1440p", 1440),
    UHD("4K", 2160)
}

enum class CodecPreset(val title: String, val mimeType: String) {
    H264("H.264", MimeTypes.VIDEO_H264),
    H265("H.265 / HEVC", MimeTypes.VIDEO_H265)
}

data class CropRectNorm(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun moved(dx: Float, dy: Float): CropRectNorm {
        val w = width
        val h = height
        var l = left + dx
        var t = top + dy
        l = l.coerceIn(0f, 1f - w)
        t = t.coerceIn(0f, 1f - h)
        return copy(left = l, top = t, right = l + w, bottom = t + h)
    }
}

data class ExportSettings(
    val aspect: AspectPreset = AspectPreset.Shorts,
    val quality: QualityPreset = QualityPreset.UHD,
    val maxFps: Int = 60,
    val codec: CodecPreset = CodecPreset.H265,
    val keepAudio: Boolean = true,
    val manualCrop: Boolean = false,
    val crop: CropRectNorm = CropRectNorm(),
    val cropZoom: Float = 1f,
    val cropSensitivity: Float = 0.35f,
    val autoplay: Boolean = true,
    val glowStrength: Float = 0.75f,
)

fun outputSize(settings: ExportSettings): Pair<Int, Int> {
    val shortSide = settings.quality.shortSide
    val ratio = settings.aspect.ratio
    val width: Int
    val height: Int
    if (ratio <= 1f) {
        width = shortSide
        height = even((shortSide / ratio).roundToInt())
    } else {
        height = shortSide
        width = even((shortSide * ratio).roundToInt())
    }
    return even(width) to even(height)
}

private fun even(value: Int): Int = if (value % 2 == 0) value else value + 1

fun parseTimecode(value: String): Long? {
    val clean = value.trim()
    if (clean.isEmpty()) return null
    val parts = clean.split(':')
    return try {
        val seconds = when (parts.size) {
            1 -> parts[0].toDouble()
            2 -> parts[0].toLong() * 60.0 + parts[1].toDouble()
            3 -> parts[0].toLong() * 3600.0 + parts[1].toLong() * 60.0 + parts[2].toDouble()
            else -> return null
        }
        if (seconds < 0) null else (seconds * 1000.0).roundToInt().toLong()
    } catch (_: NumberFormatException) {
        null
    }
}

fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun defaultCropFor(inputAspect: Float, targetAspect: Float, zoom: Float = 1f): CropRectNorm {
    val safeInput = inputAspect.takeIf { it > 0.05f } ?: (16f / 9f)
    val safeTarget = targetAspect.coerceAtLeast(0.1f)
    val z = zoom.coerceIn(1f, 2.5f)

    var width = 1f
    var height = 1f
    if (safeTarget < safeInput) {
        width = (safeTarget / safeInput).coerceIn(0.05f, 1f)
    } else if (safeTarget > safeInput) {
        height = (safeInput / safeTarget).coerceIn(0.05f, 1f)
    }

    width = (width / z).coerceIn(0.05f, 1f)
    height = (height / z).coerceIn(0.05f, 1f)

    val left = (1f - width) / 2f
    val top = (1f - height) / 2f
    return CropRectNorm(left, top, left + width, top + height)
}
