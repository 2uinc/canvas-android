package com.instructure.student.offline.util

import com.google.gson.Gson
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.student.offline.item.FileOfflineItem
import com.twou.offline.item.OfflineModule

object OfflineUtils {

    fun getKey(courseId: Long, moduleId: Long, moduleItemId: Long): String {
        val userId = ApiPrefs.user?.id ?: 0L
        return "${userId}_${courseId}_${moduleId}_${moduleItemId}"
    }

    fun parseKey(key: String): Array<Long> {
        val data = key.split("_")
        return arrayOf(data[0].toLong(), data[1].toLong(), data[2].toLong(), data[3].toLong())
    }

    fun getCourseId(parsedKey: Array<Long>): Long {
        return parsedKey[1]
    }

    fun getModuleId(parsedKey: Array<Long>): Long {
        return parsedKey[2]
    }

    fun getModuleItemId(parsedKey: Array<Long>): Long {
        return parsedKey[3]
    }

    fun convertOfflineModuleToFile(offlineModule: OfflineModule): FileOfflineItem {
        return Gson().fromJson(offlineModule.value, FileOfflineItem::class.java)
    }
}