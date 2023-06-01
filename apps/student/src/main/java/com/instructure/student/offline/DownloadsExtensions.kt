package com.instructure.student.offline

import android.net.Uri
import android.view.View
import com.instructure.canvasapi2.models.ModuleItem
import com.instructure.canvasapi2.models.ModuleObject
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils
import com.instructure.student.util.ModuleUtility
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.view.DownloadItemView

fun DownloadItemView.initWithModuleData(moduleObject: ModuleObject?, moduleItem: ModuleItem) {
    val isLocked = ModuleUtility.isGroupLocked(moduleObject)

    visibility = if (!isLocked && (moduleItem.type == "Page" || moduleItem.type == "File")) {
        val url = moduleItem.url ?: moduleObject?.itemsUrl ?: moduleItem.htmlUrl
        var courseId = -1L

        run job@{
            var isNextCourseId = false
            Uri.parse(url).pathSegments.forEach { segment ->
                if (isNextCourseId) {
                    try {
                        courseId = segment.toLong()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return@job

                } else if (segment == "courses") {
                    isNextCourseId = true
                }
            }
        }

        if (courseId == -1L) {
            View.GONE

        } else {
            val extras = mutableMapOf<String, Any>()
            extras[OfflineConst.KEY_EXTRA_MODULE_NAME] = moduleObject?.name ?: ""

            setWithRemoveAbility()
            setKeyItem(
                KeyOfflineItem(
                    OfflineUtils.getKey(courseId, moduleItem.moduleId, moduleItem.id),
                    moduleItem.title ?: "", extras
                )
            )

            View.VISIBLE
        }

    } else View.GONE
}