package com.localllm.chat.services

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import com.localllm.chat.models.Attachment
import com.localllm.chat.models.AttachmentType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Owns the on-disk attachment images. */
class AttachmentStore(private val dir: File) {
    // The whole history is re-sent each turn, so encoded images are cached.
    private val base64Cache = ConcurrentHashMap<String, String>()

    fun file(filename: String) = File(dir, filename)

    /** Downsizes and stores a picked image, returning its filename. */
    suspend fun saveImage(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = MAX_DIMENSION / maxOf(info.size.width, info.size.height).toFloat()
                if (scale < 1) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            val filename = "${UUID.randomUUID()}.jpg"
            dir.mkdirs()
            file(filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            filename
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save attachment", e)
            null
        }
    }

    suspend fun extractText(bytes: ByteArray, isPdf: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val text = if (isPdf) PDDocument.load(bytes).use { PDFTextStripper().getText(it) } else bytes.decodeToString()
            text.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read document", e)
            null
        }
    }

    fun base64Images(attachments: List<Attachment>) = attachments.mapNotNull { attachment ->
        val name = attachment.filename?.takeIf { attachment.type == AttachmentType.Image } ?: return@mapNotNull null
        base64Cache[name] ?: file(name).takeIf { it.exists() }?.let {
            Base64.getEncoder().encodeToString(it.readBytes()).also { encoded -> base64Cache[name] = encoded }
        }
    }

    suspend fun listImages(): List<File> = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().filter { it.extension == "jpg" }.sortedByDescending { it.lastModified() }
    }

    fun delete(filenames: Collection<String>) = filenames.forEach {
        file(it).delete()
        base64Cache.remove(it)
    }

    private companion object {
        const val MAX_DIMENSION = 1024f
    }
}
