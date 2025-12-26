package com.foldersync.data.local

import android.net.Uri

data class LocalFile(
    val uri: Uri,
    val name: String,
    val path: String,
    /** Relative path from sync root folder (e.g., "subfolder/file.txt") */
    val relativePath: String,
    val size: Long,
    val mimeType: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val checksum: String?
)
