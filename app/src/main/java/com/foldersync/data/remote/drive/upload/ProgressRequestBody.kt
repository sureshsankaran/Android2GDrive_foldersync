package com.foldersync.data.remote.drive.upload

import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

class ProgressRequestBody(
    private val contentType: MediaType?,
    private val contentLength: Long,
    private val inputStreamProvider: () -> InputStream,
    private val onProgress: (uploaded: Long, total: Long) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val bufferSize = DEFAULT_BUFFER_SIZE
        var uploaded = 0L
        val inputStream = inputStreamProvider.invoke()
        inputStream.use { stream ->
            val source = stream.source()
            val buffer = okio.Buffer()
            while (true) {
                val read = source.read(buffer, bufferSize.toLong())
                if (read == -1L) break
                sink.write(buffer, read)
                uploaded += read
                runBlocking {
                    withContext(dispatcher) {
                        onProgress(uploaded, contentLength)
                    }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
