package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

object ImageAndFormatUtils {
    fun createCameraCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "craft_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun encodeUriToStorageJpegBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val decoded: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = max(info.size.width, info.size.height).coerceAtLeast(1)
                    if (maxDim > 1440) {
                        val scale = 1440f / maxDim
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val sampleSize = (maxDim / 1440).coerceAtLeast(1)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null
                val ratio = minOf(1440f / raw.width, 1440f / raw.height, 1f)
                val targetW = (raw.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (raw.height * ratio).toInt().coerceAtLeast(1)
                if (targetW != raw.width || targetH != raw.height) {
                    Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                } else raw
            }

            val out = ByteArrayOutputStream()
            var quality = 84
            decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
            while (out.size() > 750_000 && quality > 40) {
                out.reset()
                quality -= 10
                decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            out.toByteArray()
        }.getOrNull()
    }

    suspend fun encodeUriToCompactBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val sampleSize = (maxDim / 640).coerceAtLeast(1)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            val ratio = minOf(640f / decoded.width, 640f / decoded.height, 1f)
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val scaled = if (targetW != decoded.width || targetH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else decoded

            val out = ByteArrayOutputStream()
            var quality = 65
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            while (out.size() > 95_000 && quality > 25) {
                out.reset()
                quality -= 10
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        }.getOrNull()
    }

    fun decodeBase64Bitmap(dataUri: String?): Bitmap? {
        if (dataUri.isNullOrBlank()) return null
        return runCatching {
            val clean = dataUri.substringAfter("base64,", dataUri)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun formatRelativeTime(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: return "Just now"
        val diffMs = System.currentTimeMillis() - date.time
        val minutes = diffMs / 60_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10_080 -> "${minutes / 1440}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    fun formatEventDateTime(millis: Long): String {
        if (millis <= 0L) return "TBD"
        return SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(millis))
    }

    fun formatEventDayBadge(millis: Long): Pair<String, String> {
        if (millis <= 0L) return "TBD" to "--"
        val date = Date(millis)
        val month = SimpleDateFormat("MMM", Locale.getDefault()).format(date).uppercase(Locale.getDefault())
        val day = SimpleDateFormat("d", Locale.getDefault()).format(date)
        return month to day
    }

    fun formatMakerUsername(displayName: String): String {
        val raw = displayName.trim().removePrefix("@")
        val handle = raw.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9_]+"), "")
            .take(24)
        return if (handle.isNotBlank()) "@$handle" else "@maker"
    }

    fun defaultCoverKeyForCategory(category: String): String {
        return when {
            category.contains("Wood", ignoreCase = true) -> "woodcraft"
            category.contains("Electron", ignoreCase = true) ||
                category.contains("3D", ignoreCase = true) -> "makertech"
            category.contains("Ceramic", ignoreCase = true) ||
                category.contains("Botanical", ignoreCase = true) -> "ceramics"
            else -> "studio"
        }
    }
}
