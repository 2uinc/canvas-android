package com.instructure.student.offline.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.student.offline.item.FileOfflineItem
import com.twou.offline.item.OfflineModule
import java.io.File

object OfflineUtils {

    fun getSchoolId(): String {
        return ApiPrefs.domain.substringBefore(".")
    }

    fun getModuleKey(type: Int, courseId: Long, moduleId: Long, moduleItemId: Long): String {
        val schoolId = getSchoolId()
        val userId = ApiPrefs.user?.id ?: 0L
        return "${schoolId}_${userId}_${type}_${courseId}_${moduleId}_${moduleItemId}"
    }

    fun getPageKey(courseId: Long, pageId: Long): String {
        val schoolId = getSchoolId()
        val userId = ApiPrefs.user?.id ?: 0L
        return "${schoolId}_${userId}_${OfflineConst.TYPE_PAGE}_${courseId}_${pageId}"
    }

    fun getModuleType(key: String): Int {
        val parsedKey = parseKey(key)
        return if (parsedKey.size == 5) {
            val type = parsedKey[2].toInt()
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
        return data[2].toInt()
    }

    fun getCourseId(key: String): Long {
        return parseKey(key)[3].toLong()
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

    fun logout() {
        DownloadsRepository.logout()
    }

    fun getFile(uri: String): File {
        return if (uri.contains("file:/")) {
            File(uri.substring(if (uri.contains("file:///")) 7 else 5))
        } else {
            File(uri)
        }
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context, context.applicationContext.packageName + ".provider", file
        )
    }

    fun openFile(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (intent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(intent)

            } catch (e: ActivityNotFoundException) {
                return false
            }

        } else {
            return false
        }

        return true
    }
}