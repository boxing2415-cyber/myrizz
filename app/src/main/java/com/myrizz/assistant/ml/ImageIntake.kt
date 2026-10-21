package com.myrizz.assistant.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Copies a user-chosen image into a private, downscaled JPEG so it can be OCR'd or uploaded. */
object ImageIntake {
    private const val MAX_SIDE = 1600

    /** Blocking: call from a background dispatcher. Returns the cached JPEG or null if unreadable. */
    fun prepare(context: Context, uri: Uri, outDir: File): File? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
        } ?: 0f

        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (rotation != 0f || scale < 1f) {
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rotation != 0f) m.postRotate(rotation)
            val transformed = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (transformed !== bmp) bmp.recycle()
            bmp = transformed
        }

        outDir.mkdirs()
        val file = File(outDir, "shot_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        return file
    }

    fun decode(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)

    fun toBase64(file: File): String {
        val out = ByteArrayOutputStream()
        file.inputStream().use { it.copyTo(out) }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
