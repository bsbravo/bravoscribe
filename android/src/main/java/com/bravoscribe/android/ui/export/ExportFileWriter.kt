package com.bravoscribe.android.ui.export

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.ResponseBody
import java.io.File

/**
 * Saves the exported zip to the public Downloads collection and returns a shareable content
 * Uri. On API 29+ this goes through MediaStore (no storage permission needed, no OS
 * download-complete notification since we didn't route the fetch through DownloadManager
 * itself — the caller shows an in-app confirmation instead). On API 26-28 it writes to the
 * legacy public Downloads directory (WRITE_EXTERNAL_STORAGE, maxSdk 28) and registers the
 * file with DownloadManager.addCompletedDownload, which does trigger the real system
 * "download complete" notification.
 */
object ExportFileWriter {

    fun save(context: Context, body: ResponseBody, fileName: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, body, fileName)
        } else {
            saveViaLegacyDownloadManager(context, body, fileName)
        }

    private fun saveViaMediaStore(context: Context, body: ResponseBody, fileName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        val written = resolver.openOutputStream(itemUri)?.use { out ->
            body.byteStream().use { input -> input.copyTo(out) }
            true
        } ?: false
        if (!written) {
            resolver.delete(itemUri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return itemUri
    }

    // The Uri-based overload requires a Referer/originating-app Uri that doesn't apply to an
    // in-app-fetched file; the path-based overload is still fully functional pre-API 29.
    @Suppress("DEPRECATION")
    private fun saveViaLegacyDownloadManager(context: Context, body: ResponseBody, fileName: String): Uri? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        runCatching {
            body.byteStream().use { input -> file.outputStream().use { out -> input.copyTo(out) } }
        }.getOrElse { return null }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.addCompletedDownload(
            fileName,
            "Bravoscribe journal export",
            /* isMediaScannerScannable = */ true,
            "application/zip",
            file.absolutePath,
            file.length(),
            /* showNotification = */ true,
        )
        return downloadManager.getUriForDownloadedFile(downloadId)
    }
}
