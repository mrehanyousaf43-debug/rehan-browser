package com.example.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.example.data.local.DownloadEntity
import com.example.data.repository.BrowserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BrowserDownloadHelper {

    fun enqueueDownload(
        context: Context,
        repository: BrowserRepository,
        scope: CoroutineScope,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent ?: "RehanBrowser/1.0")
                setTitle(fileName)
                setDescription("Downloading via Rehan Browser")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val downloadId = dm.enqueue(request)
            scope.launch(Dispatchers.IO) {
                repository.recordDownload(
                    DownloadEntity(
                        systemDownloadId = downloadId,
                        fileName = fileName,
                        url = url,
                        mimeType = mimeType ?: "application/octet-stream",
                        status = "Started"
                    )
                )
            }
            "Downloading $fileName"
        } catch (e: Exception) {
            scope.launch(Dispatchers.IO) {
                repository.recordDownload(
                    DownloadEntity(
                        systemDownloadId = -1L,
                        fileName = fileName,
                        url = url,
                        mimeType = mimeType ?: "application/octet-stream",
                        status = "Failed: ${e.localizedMessage ?: "Error"}"
                    )
                )
            }
            "Download failed: ${e.localizedMessage ?: "Unsupported URL"}"
        }
    }
}
