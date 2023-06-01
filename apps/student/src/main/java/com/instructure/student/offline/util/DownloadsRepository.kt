package com.instructure.student.offline.util

import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.item.DownloadsModuleItem
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import io.paperdb.Paper

object DownloadsRepository {

    private val mCourseItems = mutableListOf<DownloadsCourseItem>()
    private val mModuleItems = mutableListOf<DownloadsModuleItem>()
    private val mModuleItemsMap = mutableMapOf<Long, ArrayList<DownloadsModuleItem>?>()

    private var isLoaded = false

    fun getCourses(): List<DownloadsCourseItem> {
        if (!isLoaded) loadData()

        return mCourseItems
    }

    fun getModuleItems(courseId: Long): List<DownloadsModuleItem>? {
        if (!isLoaded) loadData()

        return mModuleItemsMap[courseId]
    }

    fun addModuleItem(courseItem: DownloadsCourseItem, moduleItem: DownloadsModuleItem) {
        if (!isLoaded) loadData()

        if (mCourseItems.firstOrNull { it.courseId == courseItem.courseId } == null) {
            mCourseItems.add(courseItem)
            saveCourseData()
        }

        mModuleItems.add(moduleItem)
        mModuleItemsMap.getOrPut(moduleItem.courseId) { ArrayList() }?.add(moduleItem)

        saveModuleData()
    }

    private fun loadData() {
        val userId = ApiPrefs.user?.id ?: 0L

        try {
            Paper.book("$userId").read<List<DownloadsCourseItem>>("downloads_course_items")?.let {
                mCourseItems.addAll(it.sortedBy { courseItem -> courseItem.title })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            Paper.book("$userId").read<List<DownloadsModuleItem>>("downloads_module_items")?.let {
                mModuleItems.addAll(it)
                mModuleItems.forEach { moduleItem ->
                    mModuleItemsMap.getOrPut(moduleItem.courseId) { ArrayList() }?.add(moduleItem)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Offline.getOfflineManager().addListener(object : OfflineManager.OfflineListener() {

            override fun onItemRemoved(key: String) {
                removeModuleItem(key)
            }

            override fun onItemsRemoved(keys: List<String>) {
                var isNeedSaveCourses = false

                keys.forEach { key ->
                    val parsedKey = OfflineUtils.parseKey(key)
                    val courseId = OfflineUtils.getCourseId(parsedKey)

                    removeModuleItem(key, isWithSave = true)

                    if (mModuleItemsMap[courseId]?.isEmpty() == true) {
                        mCourseItems.removeIf { it.courseId == courseId }
                        isNeedSaveCourses = true
                    }
                }

                if (isNeedSaveCourses) saveCourseData()

                saveModuleData()
            }

            private fun removeModuleItem(key: String, isWithSave: Boolean = true) {
                val parsedKey = OfflineUtils.parseKey(key)
                val courseId = OfflineUtils.getCourseId(parsedKey)
                val moduleId = OfflineUtils.getModuleId(parsedKey)
                val moduleItemId = OfflineUtils.getModuleItemId(parsedKey)

                mModuleItems.removeIf { it.moduleId == moduleId && it.moduleItemId == moduleItemId }
                mModuleItemsMap[courseId]?.removeIf {
                    it.moduleId == moduleId && it.moduleItemId == moduleItemId
                }

                if (isWithSave) {
                    if (mModuleItemsMap[courseId].isNullOrEmpty()) {
                        mCourseItems.removeIf { it.courseId == courseId }
                        mModuleItemsMap[courseId] = null
                        saveCourseData()
                    }

                    saveModuleData()
                }
            }
        })

        isLoaded = true
    }

    private fun saveCourseData() {
        val userId = ApiPrefs.user?.id ?: 0L

        Paper.book("$userId").write("downloads_course_items", mCourseItems)
    }

    private fun saveModuleData() {
        val userId = ApiPrefs.user?.id ?: 0L

        Paper.book("$userId").write("downloads_module_items", mModuleItems)
    }
}