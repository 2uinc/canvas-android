package com.instructure.student.offline.util

import com.instructure.canvasapi2.managers.CourseManager
import com.instructure.canvasapi2.managers.ModuleManager
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.canvasapi2.models.Course
import com.instructure.canvasapi2.models.ModuleItem
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.util.downloader.FileOfflineDownloader
import com.instructure.student.offline.util.downloader.PageOfflineDownloader
import com.twou.offline.base.downloader.BaseOfflineDownloader
import com.twou.offline.base.downloader.BaseOfflineDownloaderCreator
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineUnsupportedException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineQueueItem

class OfflineDownloaderCreator(offlineQueueItem: OfflineQueueItem) :
    BaseOfflineDownloaderCreator(offlineQueueItem) {

    private var mOfflineJob: WeaveJob? = null

    private var mCanvasContext: CanvasContext? = null
    private var mModuleItem: ModuleItem? = null

    private var mOfflineDownloader: BaseOfflineDownloader? = null

    override fun getKeyOfflineItem(): KeyOfflineItem {
        return offlineQueueItem.keyItem
    }

    override fun prepareOfflineDownloader(unit: (error: Throwable?) -> Unit) {
        super.prepareOfflineDownloader(unit)

        val parsedKey = OfflineUtils.parseKey(offlineQueueItem.keyItem.key)
        val courseId = OfflineUtils.getCourseId(parsedKey)
        val moduleId = OfflineUtils.getModuleId(parsedKey)
        val moduleItemId = OfflineUtils.getModuleItemId(parsedKey)

        mOfflineJob = tryWeave {
            val canvasContext = awaitApi<Course> {
                CourseManager.getCourse(courseId, it, false)
            }

            mCanvasContext = canvasContext

            val moduleItem = awaitApi<ModuleItem> {
                ModuleManager.getModuleItem(canvasContext, moduleId, moduleItemId, true, it)
            }

            mModuleItem = moduleItem

            val moduleName =
                getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_MODULE_NAME) as? String ?: ""

            DownloadsRepository.addModuleItem(
                DownloadsCourseItem(courseId, canvasContext.name),
                DownloadsModuleItem(
                    courseId, moduleId, moduleName, moduleItemId, moduleItem.title ?: "",
                    moduleItem.type ?: ""
                )
            )

            unit(null)

        } catch {
            it.printStackTrace()

            unit(OfflineDownloadException(it, message = "Failed to retrieve Content Data"))
        }
    }

    override fun createOfflineDownloader(unit: (downloader: BaseOfflineDownloader?, error: Throwable?) -> Unit) {
        if (mCanvasContext == null || mModuleItem == null) {
            unit(null, OfflineDownloadException(message = "Failed to create Offline Downloader"))
            return
        }

        when (mModuleItem?.type) {
            "Page" -> {
                mOfflineDownloader =
                    PageOfflineDownloader(mCanvasContext!!, mModuleItem!!, getKeyOfflineItem())
            }
            "File" -> {
                mOfflineDownloader =
                    FileOfflineDownloader(mModuleItem!!, getKeyOfflineItem())
            }
        }

        if (mOfflineDownloader == null) {
            unit(
                null, OfflineUnsupportedException(
                    message = "No support for ${mModuleItem?.type}"
                )
            )

        } else {
            unit(mOfflineDownloader, null)
        }
    }

    override fun destroy() {
        mOfflineJob?.cancel()
        mOfflineDownloader?.destroy()
    }
}