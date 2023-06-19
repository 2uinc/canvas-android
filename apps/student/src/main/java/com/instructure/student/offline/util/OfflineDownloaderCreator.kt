package com.instructure.student.offline.util

import android.net.Uri
import com.instructure.canvasapi2.managers.CourseManager
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.canvasapi2.models.Course
import com.instructure.canvasapi2.utils.weave.WeaveJob
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.item.DownloadsPageItem
import com.instructure.student.offline.util.downloader.FileOfflineDownloader
import com.instructure.student.offline.util.downloader.PageOfflineDownloader
import com.twou.offline.base.downloader.BaseOfflineDownloader
import com.twou.offline.base.downloader.BaseOfflineDownloaderCreator
import com.twou.offline.error.OfflineDownloadException
import com.twou.offline.error.OfflineUnsupportedException
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.item.OfflineQueueItem

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
    BaseOfflineDownloaderCreator(offlineQueueItem) {

    private var mOfflineJob: WeaveJob? = null

    private var mCanvasContext: CanvasContext? = null

    private var mOfflineDownloader: BaseOfflineDownloader? = null

    override fun getKeyOfflineItem(): KeyOfflineItem {
        return offlineQueueItem.keyItem
    }

    override fun prepareOfflineDownloader(unit: (error: Throwable?) -> Unit) {
        super.prepareOfflineDownloader(unit)

        mOfflineJob = tryWeave {
            val courseId = OfflineUtils.getCourseId(getKeyOfflineItem().key)

            val canvasContext = awaitApi<Course> {
                CourseManager.getCourse(courseId, it, false)
            }

            mCanvasContext = canvasContext

            val moduleType =
                getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE)
            if (moduleType == OfflineConst.MODULE_TYPE_MODULES) {
                val type = OfflineUtils.getKeyType(getKeyOfflineItem().key)

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
                    DownloadsCourseItem(courseId, canvasContext.name),
                    DownloadsModuleItem(
                        getKeyOfflineItem().key, courseId, moduleId, moduleName, moduleItemId,
                        offlineQueueItem.keyItem.title, type
                    )
                )

                unit(null)

            } else if (moduleType == OfflineConst.MODULE_TYPE_PAGES) {
                DownloadsRepository.addPageItem(
                    DownloadsCourseItem(courseId, canvasContext.name),
                    DownloadsPageItem(
                        getKeyOfflineItem().key, courseId, offlineQueueItem.keyItem.title
                    )
                )

                unit(null)
            }

        } catch {
            it.printStackTrace()

            unit(OfflineDownloadException(it, message = "Failed to retrieve Content Data"))
        }
    }

    override fun createOfflineDownloader(unit: (downloader: BaseOfflineDownloader?, error: Throwable?) -> Unit) {
        val type = OfflineUtils.getKeyType(getKeyOfflineItem().key)

        when (type) {
            OfflineConst.TYPE_PAGE -> {
                val url = Uri.parse(
                    getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_URL) as? String
                        ?: ""
                ).lastPathSegment

                if (mCanvasContext == null || url == null) {
                    unit(
                        null,
                        OfflineDownloadException(message = "Failed to create Offline Downloader")
                    )
                    return
                }

                mOfflineDownloader = PageOfflineDownloader(
                    mCanvasContext!!, url, getKeyOfflineItem()
                )
            }
            OfflineConst.TYPE_FILE -> {
                val url =
                    getKeyOfflineItem().extras?.get(OfflineConst.KEY_EXTRA_URL) as? String
                mOfflineDownloader = FileOfflineDownloader(url, getKeyOfflineItem())
            }
        }

        if (mOfflineDownloader == null) {
            unit(
                null, OfflineUnsupportedException(message = "No support for $type")
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