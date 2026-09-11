package com.aurorashelf.app.data.forum

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class ForumImageRequest(
    val imageUrl: String,
    val pageUrl: String,
    val userAgent: String,
    val cookies: String?,
)

/** Saves forum images without decoding them, preserving GIF/WebP animation and avoiding bitmap OOMs. */
internal class ForumImageSaver(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver

    suspend fun save(request: ForumImageRequest): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            when {
                request.imageUrl.startsWith("data:image/", ignoreCase = true) -> saveDataUrl(request.imageUrl)
                request.imageUrl.startsWith("https://", ignoreCase = true) -> saveRemoteImage(request)
                else -> throw IOException("仅支持 HTTPS 或页面内嵌图片")
            }
        }
    }

    private fun saveRemoteImage(request: ForumImageRequest): Uri {
        val networkRequest = Request.Builder()
            .url(request.imageUrl)
            .header("User-Agent", request.userAgent)
            .header("Referer", request.pageUrl)
            .apply { request.cookies?.takeIf(String::isNotBlank)?.let { header("Cookie", it) } }
            .build()

        return client.newCall(networkRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("图片服务器返回 ${response.code}")
            saveResponse(response, request.imageUrl)
        }
    }

    private fun saveResponse(response: Response, imageUrl: String): Uri {
        val body = response.body ?: throw IOException("图片服务器未返回内容")
        val declaredLength = body.contentLength()
        if (declaredLength > MAX_IMAGE_BYTES) throw IOException("图片超过 40 MB，已取消保存")
        val mimeType = resolveMimeType(body.contentType()?.toString(), imageUrl)
        return body.byteStream().use { input -> saveStream(input, mimeType) }
    }

    private fun saveDataUrl(dataUrl: String): Uri {
        val separator = dataUrl.indexOf(',')
        if (separator <= 0 || !dataUrl.substring(0, separator).contains(";base64", ignoreCase = true)) {
            throw IOException("无法识别页面内嵌图片")
        }
        val metadata = dataUrl.substring(5, separator)
        val mimeType = metadata.substringBefore(';').lowercase()
        if (!mimeType.startsWith("image/")) throw IOException("长按内容不是图片")
        val encoded = dataUrl.substring(separator + 1)
        if (encoded.length > MAX_BASE64_CHARACTERS) throw IOException("图片超过 40 MB，已取消保存")
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { throw IOException("页面内嵌图片已损坏", it) }
        return ByteArrayInputStream(bytes).use { input -> saveStream(input, mimeType) }
    }

    private fun saveStream(input: InputStream, mimeType: String): Uri {
        val displayName = createDisplayName(mimeType)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(input, displayName, mimeType)
        } else {
            saveToLegacyGallery(input, displayName, mimeType)
        }
    }

    private fun saveToMediaStore(input: InputStream, displayName: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AuroraShelf")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建相册文件")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> copyWithLimit(input, output) }
                ?: throw IOException("无法写入相册文件")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyGallery(input: InputStream, displayName: String, mimeType: String): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "AuroraShelf",
        )
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建相册目录")
        val imageFile = File(directory, displayName)
        FileOutputStream(imageFile).use { output -> copyWithLimit(input, output) }
        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(imageFile.absolutePath),
            arrayOf(mimeType),
            null,
        )
        return Uri.fromFile(imageFile)
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            if (totalBytes > MAX_IMAGE_BYTES) throw IOException("图片超过 40 MB，已取消保存")
            output.write(buffer, 0, read)
        }
        if (totalBytes == 0L) throw IOException("图片内容为空")
    }

    internal companion object {
        private const val MAX_IMAGE_BYTES = 40L * 1024L * 1024L
        private const val MAX_BASE64_CHARACTERS = MAX_IMAGE_BYTES * 4L / 3L + 8L

        internal fun resolveMimeType(contentType: String?, imageUrl: String): String {
            contentType?.substringBefore(';')?.trim()?.lowercase()
                ?.takeIf { it.startsWith("image/") }
                ?.let { return it }
            val extension = Uri.parse(imageUrl).lastPathSegment
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                .orEmpty()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?.takeIf { it.startsWith("image/") }
                ?: throw IOException("服务器返回的内容不是图片")
        }

        internal fun createDisplayName(mimeType: String, timestamp: Long = System.currentTimeMillis()): String {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: when (mimeType.lowercase()) {
                    "image/jpeg" -> "jpg"
                    "image/svg+xml" -> "svg"
                    else -> "img"
                }
            return "AuroraShelf_${timestamp}.$extension"
        }
    }
}
