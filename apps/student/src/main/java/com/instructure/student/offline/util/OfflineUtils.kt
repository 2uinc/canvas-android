package com.instructure.student.offline.util

import android.net.Uri
import com.google.gson.Gson
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.student.offline.item.FileOfflineItem
import com.twou.offline.item.OfflineModule

object OfflineUtils {

    fun getModuleKey(type: Int, courseId: Long, moduleId: Long, moduleItemId: Long): String {
        val userId = ApiPrefs.user?.id ?: 0L
        return "${userId}_${type}_${courseId}_${moduleId}_${moduleItemId}"
    }

    fun getPageKey(courseId: Long, pageId: Long): String {
        val userId = ApiPrefs.user?.id ?: 0L
        return "${userId}_${OfflineConst.TYPE_PAGE}_${courseId}_${pageId}"
    }

    fun getModuleType(key: String): Int {
        val parsedKey = parseKey(key)
        return if (parsedKey.size == 4) {
            val type = parsedKey[1].toInt()
            if (type == OfflineConst.TYPE_PAGE) {
                OfflineConst.MODULE_TYPE_PAGES

            } else {
                OfflineConst.MODULE_TYPE_FILES
            }

        } else {
            OfflineConst.MODULE_TYPE_MODULES
        }
    }

    fun getKeyType(key: String): Int {
        val data = key.split("_")
        return data[1].toInt()
    }

    fun getCourseId(key: String): Long {
        return parseKey(key)[2].toLong()
    }

    private fun parseKey(key: String): ArrayList<String> {
        val data = key.split("_")
        return ArrayList<String>().apply { data.forEach { add(it) } }
    }

    fun convertOfflineModuleToFile(offlineModule: OfflineModule): FileOfflineItem {
        return Gson().fromJson(offlineModule.value, FileOfflineItem::class.java)
    }

    fun getContentType(type: String): Int {
        return when (type) {
            "Page" -> OfflineConst.TYPE_PAGE
            "File" -> OfflineConst.TYPE_FILE
            "ExternalTool" -> OfflineConst.TYPE_LTI
            else -> -1
        }
    }

    fun getCourseIdFromUrl(url: String): Long {
        var isNextCourseId = false
        Uri.parse(url).pathSegments.forEach { segment ->
            if (isNextCourseId) {
                try {
                    return segment.toLong()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } else if (segment == "courses") {
                isNextCourseId = true
            }
        }

        return -1L
    }
}