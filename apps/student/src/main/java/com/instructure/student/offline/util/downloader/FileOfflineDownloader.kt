package com.instructure.student.offline.util.downloader

import android.net.Uri
import com.google.gson.Gson
import com.instructure.canvasapi2.managers.FileFolderManager
import com.instructure.canvasapi2.models.FileFolder
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApiResponse
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.student.offline.item.FileOfflineItem
import com.twou.offline.base.downloader.BaseOfflineDownloader
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineModule
import kotlinx.coroutines.Job

class FileOfflineDownloader(
    private val mUrl: String?, private val mKeyItem: KeyOfflineItem
) : BaseOfflineDownloader(mKeyItem) {

    private var mFetchDataJob: WeaveJob? = null
    private var mLoadHtmlJob: Job? = null

    override fun startPreparation() {
        mFetchDataJob = tryWeave(background = true) {
            val fileUrl = mUrl?.substringAfter("/api/v1/")
                ?: throw Exception("Page url/name null!")
            val response = awaitApiResponse<FileFolder> {
                FileFolderManager.getFileFolderFromURL(fileUrl, true, it)
            }

            val file = response.body()

            if (file == null) {
                processError(
                    OfflineDownloadException(message = "Failed to retrieve File - body is Null")
                )

                return@tryWeave
            }

            if (file.lockInfo != null) {
                processError(
                    OfflineDownloadException(message = "Unable to download. Content is locked")
                )

                return@tryWeave
            }

            var thumbnailPath = ""
            file.thumbnailUrl?.let { url ->
                val fileName = getFileNameFromUrl(url)

                val resourceLink = ResourceLink(url, "$filesDirPath/", fileName ?: "thumbnail")

                try {
                    downloadFileToLocalStorage(resourceLink)
                    thumbnailPath = resourceLink.getFilePath()
                } catch (e: Exception) {
                    processError(e)
                }
            }

            handler.post { updateProgress(80, 100, 2000) }

            var filePath = ""
            file.url?.let { url ->
                val resourceLink = ResourceLink(
                    url, "$filesDirPath/", file.name ?: file.displayName ?: file.fullName ?: ""
                )

                try {
                    downloadFileToLocalStorage(resourceLink)
                    filePath = resourceLink.getFilePath()
                } catch (e: Exception) {
                    processError(e)
                }
            }

            handler.post { updateProgress(100, 100, 500) }

            val value = Gson().toJson(
                FileOfflineItem(
                    mKeyItem.key, file.displayName ?: "", file.contentType ?: "",
                    thumbnailPath, filePath
                )
            )
            setAllDataDownloaded(OfflineModule(mKeyItem.key, value))


        } catch {
            it.printStackTrace()

            processError(OfflineDownloadException(it, "Failed to retrieve File"))
        }
    }

    override fun checkResourceBeforeSave(link: ResourceLink, data: String, threadId: Int): String {
        return data
    }

    override fun destroy() {
        mFetchDataJob?.cancel()
        mLoadHtmlJob?.cancel()

        super.destroy()
    }

    private fun getFileNameFromUrl(url: String): String? {
        val uri = Uri.parse(url)
        val path = uri.path
        val fileName = path?.substringAfterLast('/')
        val query = uri.query

        if (fileName != null && !query.isNullOrBlank()) {
            val fileNameWithoutQuery = fileName.substringBeforeLast('?')
            return fileNameWithoutQuery.ifBlank { null }
        }

        return fileName
    }
}