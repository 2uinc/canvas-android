package com.instructure.student.offline.util

import android.net.Uri
import android.webkit.CookieManager
import com.instructure.canvasapi2.managers.CourseManager
import com.instructure.canvasapi2.models.Course
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.item.DownloadsPageItem
import com.instructure.student.offline.util.downloader.FileOfflineDownloader
import com.instructure.student.offline.util.downloader.LTIOfflineDownloader
import com.instructure.student.offline.util.downloader.PageOfflineDownloader
import com.twou.offline.base.BaseOfflineDownloaderCreator
import com.twou.offline.base.downloader.BaseOfflineDownloader
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineUnsupportedException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineQueueItem
import com.twou.offline.util.OfflineDownloaderUtils
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/***
Each KeyOfflineItem should contain in extras next fields:
KEY_EXTRA_CONTENT_MODULE_TYPE, can be one of:
MODULE_TYPE_MODULES
MODULE_TYPE_PAGES
MODULE_TYPE_FILES
depends on from where this content was downloaded

if KEY_EXTRA_CONTENT_MODULE_TYPE == MODULE_TYPE_MODULES then next fields are required:
KEY_EXTRA_MODULE_NAME
KEY_EXTRA_MODULE_ID
KEY_EXTRA_MODULE_ITEM_ID

to download the Page content call OfflineUtils.getPageKey to create the key
 */
class OfflineDownloaderCreator(offlineQueueItem: OfflineQueueItem) :
    BaseOfflineDownloaderCreator(offlineQueueItem), CoroutineScope {

    private var mCourseIndex = 0
    private var mOfflineJob: WeaveJob? = null
    private var mCanvasContext: Course? = null
    private var mOfflineDownloader: BaseOfflineDownloader? = null

    override val coroutineContext = Dispatchers.Default

    private var mBgJob: CompletableJob? = null
    private var mBgScope: CoroutineScope? = null

    override fun getKeyOfflineItem(): KeyOfflineItem {
        return offlineQueueItem.keyItem
    }

    override fun prepareOfflineDownloader(unit: (error: Throwable?) -> Unit) {
        super.prepareOfflineDownloader(unit)

        mOfflineJob = tryWeave(true) {
            val courseId = OfflineUtils.getCourseId(getKeyOfflineItem().key)

            val canvasContext = awaitApi {
                CourseManager.getCourse(courseId, it, false)
            }

            val dashboardCards = awaitApi { CourseManager.getDashboardCourses(false, it) }
            run job@{
                dashboardCards.forEachIndexed { index, dashboardCard ->
                    if (dashboardCard.id == courseId) {
                        mCourseIndex = index
                        return@job
                    }
                }
            }
            mCanvasContext = canvasContext

            unit(null)

        } catch {
            it.printStackTrace()

            unit(it)
        }
    }

    override fun createOfflineDownloader(unit: (downloader: BaseOfflineDownloader?, error: Throwable?) -> Unit) {
        if (mCanvasContext == null) {
            prepareOfflineDownloader { error ->
                if (mCanvasContext == null || error != null) {
                    unit(
                        null, error
                            ?: OfflineDownloadException(message = "Failed to create Offline Downloader")
                    )

                } else {
                    createOfflineDownloader(unit)
                }
            }
            return
        }

        if (mBgScope == null) {
            SupervisorJob().let {
                mBgJob = it
                mBgScope = CoroutineScope(context = Dispatchers.Default + it)
            }
        }
        mBgScope?.launch {
            val courseId = OfflineUtils.getCourseId(getKeyOfflineItem().key)
            var logoPath = ""
            try {
                mCanvasContext?.imageUrl?.let { logoPath = downloadCourseImage(it) }
            } catch (e: Exception) {
                e.printStackTrace()

                unit(null, e)
                return@launch
            }

            val moduleType =
                getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE)

            if (moduleType == OfflineConst.MODULE_TYPE_MODULES) {
                val type = OfflineUtils.getKeyType(getKeyOfflineItem().key)

                val moduleIndex =
                    getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_MODULE_INDEX) as? Int
                        ?: 0
                val moduleName =
                    getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_MODULE_NAME) as? String
                        ?: ""
                val moduleId =
                    getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_MODULE_ID) as? Long
                        ?: -1L
                val moduleItemId =
                    getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_MODULE_ITEM_ID) as? Long
                        ?: -1L

                DownloadsRepository.addModuleItem(
                    DownloadsCourseItem(
                        mCourseIndex, courseId, mCanvasContext?.name ?: "",
                        mCanvasContext?.courseCode ?: "", logoPath, mCanvasContext?.term?.name ?: ""
                    ),
                    DownloadsModuleItem(
                        moduleIndex, getKeyOfflineItem().key, courseId, moduleId, moduleName,
                        moduleItemId, offlineQueueItem.keyItem.title, type
                    )
                )

            } else if (moduleType == OfflineConst.MODULE_TYPE_PAGES) {
                DownloadsRepository.addPageItem(
                    DownloadsCourseItem(
                        mCourseIndex, courseId, mCanvasContext?.name ?: "",
                        mCanvasContext?.courseCode ?: "", logoPath, mCanvasContext?.term?.name ?: ""
                    ),
                    DownloadsPageItem(
                        getKeyOfflineItem().key, courseId, offlineQueueItem.keyItem.title
                    )
                )
            }

            mBgScope?.launch(Dispatchers.Main) job@{
                val type = OfflineUtils.getKeyType(getKeyOfflineItem().key)

                when (type) {
                    OfflineConst.TYPE_PAGE -> {
                        val url = Uri.parse(
                            getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_URL) as? String
                                ?: ""
                        ).lastPathSegment

                        if (url == null) {
                            unit(
                                null,
                                OfflineDownloadException(message = "Failed to create Offline Downloader")
                            )
                            return@job
                        }

                        mOfflineDownloader = PageOfflineDownloader(
                            mCanvasContext!!, url, getKeyOfflineItem()
                        )
                    }

                    OfflineConst.TYPE_FILE -> {
                        val url =
                            getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_URL) as? String
                        if (url.isNullOrEmpty()) {
                            unit(
                                null,
                                OfflineDownloadException(message = "Failed to create Offline Downloader")
                            )

                        } else {
                            mOfflineDownloader = FileOfflineDownloader(url, getKeyOfflineItem())
                        }
                    }

                    OfflineConst.TYPE_LTI -> {
                        val url =
                            getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_URL) as? String
                        if (url.isNullOrEmpty()) {
                            unit(
                                null,
                                OfflineDownloadException(message = "Failed to create Offline Downloader")
                            )

                        } else {
                            mOfflineDownloader = LTIOfflineDownloader(url, getKeyOfflineItem())
                        }
                    }
                }

                if (mOfflineDownloader == null) {
                    unit(
                        null, OfflineUnsupportedException(message = "No support for $type")
                    )

                } else if (mBgScope != null) {
                    unit(mOfflineDownloader, null)
                }
            }
        }
    }

    override fun destroy() {
        mBgJob?.cancel()
        mBgJob?.cancelChildren()
        mBgJob = null
        mBgScope = null

        mOfflineJob?.cancel()
        mOfflineDownloader?.destroy()
    }

    private fun downloadCourseImage(url: String): String {
        val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder().url(url)

        try {
            val uri = Uri.parse(url)
            val cookies = CookieManager.getInstance().getCookie(uri.scheme + "://" + uri.host)
            requestBuilder.addHeader("Cookie", cookies ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val call = client.newCall(requestBuilder.build())
        val response = call.execute()

        val body = response.body

        val schoolId = OfflineUtils.getSchoolId()
        val schoolDir = File(OfflineDownloaderUtils.getGeneralDirPath() + "/${schoolId}")
        if (!schoolDir.exists()) schoolDir.mkdirs()

        val downloadFile =
            File(OfflineDownloaderUtils.getGeneralDirPath() + "/${schoolId}/logo.png")
        if (downloadFile.exists()) {
            body?.close()
            return downloadFile.path
        }

        val outputStream = FileOutputStream(downloadFile)

        body?.let { responseBody ->
            BufferedInputStream(responseBody.byteStream()).use { inputStream ->
                val data = ByteArray(1024)

                var count: Int

                while (inputStream.read(data).also { count = it } != -1) {
                    outputStream.write(data, 0, count)
                }
            }

            responseBody.close()
        }

        outputStream.flush()
        outputStream.close()

        return downloadFile.path
    }
}