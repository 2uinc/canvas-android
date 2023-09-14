package com.instructure.student.offline

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.instructure.canvasapi2.models.ModuleItem
import com.instructure.canvasapi2.models.ModuleObject
import com.instructure.canvasapi2.models.Page
import com.instructure.interactions.router.Route
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils
import com.instructure.student.util.ModuleUtility
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.view.DownloadItemView
import java.net.URL

fun DownloadItemView.initWithModuleData(
    moduleIndex: Int, moduleObject: ModuleObject?, moduleItem: ModuleItem?, courseColor: Int
) {
    val isLocked = ModuleUtility.isGroupLocked(moduleObject)

    if (moduleItem == null) {
        visibility = View.GONE
        return
    }

    val type = OfflineUtils.getContentType(moduleItem.type ?: "")
    if (!isLocked && type != -1) {
        val url = moduleItem.url ?: moduleObject?.itemsUrl ?: moduleItem.htmlUrl
        val courseId = OfflineUtils.getCourseIdFromUrl(url ?: "")

        if (courseId == -1L) {
            visibility = View.GONE

        } else {
            val extras = mutableMapOf<String, Any>()
            extras[OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE] = OfflineConst.MODULE_TYPE_MODULES

            extras[OfflineConst.KEY_EXTRA_MODULE_INDEX] = moduleIndex
            extras[OfflineConst.KEY_EXTRA_MODULE_NAME] = moduleObject?.name ?: ""
            extras[OfflineConst.KEY_EXTRA_MODULE_ID] = moduleItem.moduleId
            extras[OfflineConst.KEY_EXTRA_MODULE_ITEM_ID] = moduleItem.id
            extras[OfflineConst.KEY_EXTRA_URL] = moduleItem.url ?: ""

            setWithRemoveAbility()
            setKeyItem(
                KeyOfflineItem(
                    OfflineUtils.getModuleKey(
                        type, courseId, moduleItem.moduleId, moduleItem.id
                    ), moduleItem.title ?: "", extras
                )
            )
            setViewColor(courseColor)
            visibility = View.VISIBLE
        }

    } else visibility = View.GONE
}

fun DownloadItemView.initWithPageData(page: Page, courseColor: Int) {
    val url = page.htmlUrl
    val courseId = OfflineUtils.getCourseIdFromUrl(url ?: "")

    visibility = if (courseId == -1L) {
        View.GONE

    } else {
        val extras = mutableMapOf<String, Any>()
        extras[OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE] = OfflineConst.MODULE_TYPE_PAGES

        extras[OfflineConst.KEY_EXTRA_PAGE_ID] = page.id
        extras[OfflineConst.KEY_EXTRA_URL] = page.url ?: ""

        setWithRemoveAbility()
        setKeyItem(
            KeyOfflineItem(
                OfflineUtils.getPageKey(courseId, page.id), page.title ?: "", extras
            )
        )
        setViewColor(courseColor)
        View.VISIBLE
    }
}

fun Route.addOfflineDataForModule(
    moduleIndex: Int, moduleItem: ModuleItem, moduleObject: ModuleObject? = null
): Route {
    arguments.putInt(OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE, OfflineConst.MODULE_TYPE_MODULES)

    arguments.putInt(OfflineConst.KEY_EXTRA_MODULE_INDEX, moduleIndex)
    arguments.putString(OfflineConst.KEY_EXTRA_MODULE_NAME, moduleObject?.name ?: "")
    arguments.putLong(OfflineConst.KEY_EXTRA_MODULE_ID, moduleItem.moduleId)
    arguments.putLong(OfflineConst.KEY_EXTRA_MODULE_ITEM_ID, moduleItem.id)
    arguments.putString(OfflineConst.KEY_EXTRA_URL, moduleItem.url ?: "")
    return this
}

fun Route.addOfflineDataForPage(page: Page): Route {
    arguments.putInt(OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE, OfflineConst.MODULE_TYPE_PAGES)

    arguments.putLong(OfflineConst.KEY_EXTRA_PAGE_ID, page.id)
    arguments.putString(OfflineConst.KEY_EXTRA_URL, page.url ?: "")
    return this
}

fun Toolbar.initWithOfflineData(
    context: Context, courseId: Long, title: String, arguments: Bundle?, type: Int = -1,
    endPadding: Int = 0
) {
    if (arguments == null) return

    if (findViewWithTag<DownloadItemView>("DownloadItemView") != null) return

    val moduleType = arguments.getInt(OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE, -1)
    if (moduleType == -1) return

    clipChildren = false

    addView(DownloadItemView(context).apply {
        layoutParams = Toolbar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END
            setPadding(0, 0, endPadding, 0)
        }

        val extras = getOfflineExtras(arguments)

        setWithRemoveAbility()

        val key = if (moduleType == OfflineConst.MODULE_TYPE_MODULES) {
            val moduleId = arguments.getLong(OfflineConst.KEY_EXTRA_MODULE_ID)
            val moduleItemId = arguments.getLong(OfflineConst.KEY_EXTRA_MODULE_ITEM_ID)

            OfflineUtils.getModuleKey(type, courseId, moduleId, moduleItemId)

        } else {
            val pageId = arguments.getLong(OfflineConst.KEY_EXTRA_PAGE_ID)

            OfflineUtils.getPageKey(courseId, pageId)
        }
        setKeyItem(KeyOfflineItem(key, title, extras))
        setViewColor(Color.WHITE)
        tag = "DownloadItemView"
    })
}

fun URL.findParameterValue(parameterName: String): String? {
    return query.split('&').map {
        val parts = it.split('=')
        val name = parts.firstOrNull() ?: ""
        val value = parts.drop(1).firstOrNull() ?: ""
        Pair(name, value)
    }.firstOrNull { it.first == parameterName }?.second
}

private fun getOfflineExtras(arguments: Bundle): MutableMap<String, Any> {
    val moduleType = arguments.getInt(OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE, -1)

    val extras = mutableMapOf<String, Any>()
    extras[OfflineConst.KEY_EXTRA_CONTENT_MODULE_TYPE] = moduleType

    if (moduleType == OfflineConst.MODULE_TYPE_MODULES) {
        val moduleIndex = arguments.getInt(OfflineConst.KEY_EXTRA_MODULE_INDEX)
        val moduleName = arguments.getString(OfflineConst.KEY_EXTRA_MODULE_NAME) ?: ""
        val moduleId = arguments.getLong(OfflineConst.KEY_EXTRA_MODULE_ID)
        val moduleItemId = arguments.getLong(OfflineConst.KEY_EXTRA_MODULE_ITEM_ID)

        extras[OfflineConst.KEY_EXTRA_MODULE_INDEX] = moduleIndex
        extras[OfflineConst.KEY_EXTRA_MODULE_NAME] = moduleName
        extras[OfflineConst.KEY_EXTRA_MODULE_ID] = moduleId
        extras[OfflineConst.KEY_EXTRA_MODULE_ITEM_ID] = moduleItemId

    } else if (moduleType == OfflineConst.MODULE_TYPE_PAGES) {
        val pageId = arguments.getLong(OfflineConst.KEY_EXTRA_PAGE_ID)

        extras[OfflineConst.KEY_EXTRA_PAGE_ID] = pageId
    }

    extras[OfflineConst.KEY_EXTRA_URL] = arguments.getString(OfflineConst.KEY_EXTRA_URL) ?: ""

    return extras
}