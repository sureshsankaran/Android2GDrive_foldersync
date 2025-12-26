package com.foldersync.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChecksumCalculator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun calculateMD5(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open input stream for $uri")
        return calculateMD5(inputStream)
    }

    fun calculateMD5(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(BUFFER_SIZE)
        inputStream.use { stream ->
            var bytesRead = stream.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = stream.read(buffer)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    fun calculateBatchChecksums(uris: List<Uri>): Map<Uri, String> {
        return uris.associateWith { uri ->
            calculateMD5(uri)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
