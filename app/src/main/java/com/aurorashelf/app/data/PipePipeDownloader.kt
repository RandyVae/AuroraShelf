package com.aurorashelf.app.data

import com.aurorashelf.app.model.ContentSourceCatalog
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Minimal cookie-free network implementation for the vendored PipePipe extractor. */
internal class PipePipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val call = client.newCall(request.toOkHttpRequest())
        return call.execute().use { response ->
            Log.i(TAG, "GET ${request.url()} code=${response.code} bytes=${response.body?.contentLength()}")
            response.toExtractorResponse(request.url())
        }
    }

    override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall {
        val call = client.newCall(request.toOkHttpRequest())
        val cancellable = CancellableCall(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                cancellable.setFinished()
                callback.onError(error)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    response.use { callback.onSuccess(it.toExtractorResponse(request.url())) }
                } catch (error: Exception) {
                    callback.onError(error)
                } finally {
                    cancellable.setFinished()
                }
            }
        })
        return cancellable
    }

    private fun Request.toOkHttpRequest(): okhttp3.Request {
        val method = httpMethod().uppercase()
        val requestData = dataToSend()
        val body = when {
            requestData != null -> requestData.toRequestBody()
            method in METHODS_REQUIRING_BODY -> ByteArray(0).toRequestBody()
            else -> null
        }
        return okhttp3.Request.Builder()
            .url(url())
            .method(method, body)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .apply {
                headers().forEach { (name, values) ->
                    removeHeader(name)
                    values.forEach { addHeader(name, it) }
                }
            }
            .build()
    }

    private fun okhttp3.Response.toExtractorResponse(originalUrl: String): Response {
        if (code == 429) throw ReCaptchaException("站点要求进行人机验证", originalUrl)
        val bytes = body?.bytes() ?: ByteArray(0)
        return Response(
            code,
            message,
            headers.toMultimap(),
            String(bytes, StandardCharsets.UTF_8),
            bytes,
            request.url.toString(),
        )
    }

    companion object {
        private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
        private const val TAG = "PipePipeDownloader"
    }
}
