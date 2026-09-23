package com.vuvuv.framecut

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@OptIn(markerClass = [UnstableApi::class])
class ClipExporter(private val context: Context) {
    private val active = AtomicReference<Transformer?>(null)
    private val main = Handler(Looper.getMainLooper())

    fun cancel() {
        main.post {
            active.getAndSet(null)?.cancel()
        }
    }

    fun export(
        source: Uri,
        startMs: Long,
        endMs: Long,
        settings: ExportSettings,
        onState: (String) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (endMs <= startMs) {
            onError("Конец должен быть позже начала")
            return
        }

        cancel()
        val exportDir = File(context.cacheDir, "framecut_exports").apply { mkdirs() }
        val tempFile = File(exportDir, "framecut_${System.currentTimeMillis()}.mp4")
        if (tempFile.exists()) tempFile.delete()

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs.coerceAtLeast(0L))
            .setEndPositionMs(endMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(clipping)
            .build()

        val videoEffects = mutableListOf<Effect>()
        if (settings.manualCrop) {
            val c = settings.crop
            val left = (c.left * 2f - 1f).coerceIn(-1f, 0.99f)
            val right = (c.right * 2f - 1f).coerceIn(-0.99f, 1f)
            val top = (1f - c.top * 2f).coerceIn(-0.99f, 1f)
            val bottom = (1f - c.bottom * 2f).coerceIn(-1f, 0.99f)
            if (left < right && bottom < top) {
                videoEffects += Crop(left, right, bottom, top)
            }
        }

        val (outWidth, outHeight) = outputSize(settings)
        videoEffects += Presentation.createForWidthAndHeight(
            outWidth,
            outHeight,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .setRemoveAudio(!settings.keepAudio)
            .setFrameRate(settings.maxFps)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                active.set(null)
                onState("Сохраняю в Галерею…")
                Thread {
                    try {
                        val uri = publishToGallery(tempFile)
                        tempFile.delete()
                        main.post { onSuccess(uri) }
                    } catch (t: Throwable) {
                        tempFile.delete()
                        main.post { onError(t.message ?: "Не удалось сохранить видео") }
                    }
                }.start()
            }

            override fun onError(
                composition: Composition,
                result: ExportResult,
                exception: ExportException,
            ) {
                active.set(null)
                tempFile.delete()
                onError(exception.message ?: "Ошибка экспорта")
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(settings.codec.mimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()

        active.set(transformer)
        onState("Экспорт ${outWidth}×${outHeight}, до ${settings.maxFps} fps…")
        try {
            transformer.start(editedMediaItem, tempFile.absolutePath)
        } catch (t: Throwable) {
            active.set(null)
            tempFile.delete()
            onError(t.message ?: "Не удалось запустить экспорт")
        }
    }

    private fun publishToGallery(file: File): Uri {
        val resolver = context.contentResolver
        val displayName = "FrameCut_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FrameCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore не создал файл")

        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Не удалось открыть файл назначения" }
                file.inputStream().use { input -> input.copyTo(output, 1024 * 1024) }
            }
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
