package com.instructure.student.offline.util

import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.item.DownloadsPageItem
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import io.paperdb.Paper

object DownloadsRepository {

    private val mCourseItems = mutableListOf<DownloadsCourseItem>()
    private val mModuleItems = mutableListOf<DownloadsModuleItem>()
    private val mModuleItemsMap = mutableMapOf<Long, ArrayList<DownloadsModuleItem>?>()

    private val mPageItems = mutableListOf<DownloadsPageItem>()
    private val mPageItemsMap = mutableMapOf<Long, ArrayList<DownloadsPageItem>?>()

    private var isLoaded = false

    fun clearAll() {
        mCourseItems.clear()
        mModuleItems.clear()
        mModuleItemsMap.clear()

        mPageItems.clear()
        mPageItemsMap.clear()

        saveCourseData()
        saveModuleData()
        savePageData()
    }

    fun getCourses(): List<DownloadsCourseItem> {
        if (!isLoaded) loadData()

        return mCourseItems
    }

    fun getModuleItems(courseId: Long): List<DownloadsModuleItem>? {
        if (!isLoaded) loadData()

        return mModuleItemsMap[courseId]
    }

    fun getPageItems(courseId: Long): List<DownloadsPageItem>? {
        if (!isLoaded) loadData()

        return mPageItemsMap[courseId]
    }

    fun addModuleItem(courseItem: DownloadsCourseItem, moduleItem: DownloadsModuleItem) {
        if (!isLoaded) loadData()

        if (mCourseItems.firstOrNull { it.courseId == courseItem.courseId } == null) {
            mCourseItems.add(courseItem)
            saveCourseData()
        }

        if (mModuleItems.firstOrNull { it.key == moduleItem.key } == null) {
            mModuleItems.add(moduleItem)
            mModuleItemsMap.getOrPut(moduleItem.courseId) { ArrayList() }?.add(moduleItem)

            saveModuleData()
        }
    }

    fun addPageItem(courseItem: DownloadsCourseItem, pageItem: DownloadsPageItem) {
        if (!isLoaded) loadData()

        if (mCourseItems.firstOrNull { it.courseId == courseItem.courseId } == null) {
            mCourseItems.add(courseItem)
            saveCourseData()
        }

        if (mPageItems.firstOrNull { it.key == pageItem.key } == null) {
            mPageItems.add(pageItem)
            mPageItemsMap.getOrPut(pageItem.courseId) { ArrayList() }?.add(pageItem)

            savePageData()
        }
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

        try {
            Paper.book("$userId").read<List<DownloadsPageItem>>("downloads_page_items")?.let {
                mPageItems.addAll(it)
                mPageItems.forEach { pageItem ->
                    mPageItemsMap.getOrPut(pageItem.courseId) { ArrayList() }?.add(pageItem)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Offline.getOfflineManager().addListener(object : OfflineManager.OfflineListener() {

            override fun onItemRemoved(key: String) {
                when (OfflineUtils.getModuleType(key)) {
                    OfflineConst.MODULE_TYPE_MODULES -> removeModuleItem(key)
                    OfflineConst.MODULE_TYPE_PAGES -> removePageItem(key)
                }
            }

            override fun onItemsRemoved(keys: List<String>) {
                var isNeedSaveCourses = false
                var isNeedSaveModules = false
                var isNeedSavePages = false

                keys.forEach { key ->
                    when (OfflineUtils.getModuleType(key)) {
                        OfflineConst.MODULE_TYPE_MODULES -> {
                            removeModuleItem(key, isWithSave = false)
                            isNeedSaveCourses = true
                            isNeedSaveModules = true
                        }

                        OfflineConst.MODULE_TYPE_PAGES -> {
                            removePageItem(key, isWithSave = false)
                            isNeedSaveCourses = true
                            isNeedSavePages = true
                        }
                    }
                }

                if (isNeedSaveCourses) saveCourseData()
                if (isNeedSaveModules) saveModuleData()
                if (isNeedSavePages) savePageData()
            }

            private fun removeModuleItem(key: String, isWithSave: Boolean = true) {
                val courseId = OfflineUtils.getCourseId(key)

                mModuleItems.removeIf { it.key == key }
                mModuleItemsMap[courseId]?.removeIf { it.key == key }

                checkIfNeedToRemoveCourse(courseId, isWithSave)

                if (isWithSave) saveModuleData()
            }

            private fun removePageItem(key: String, isWithSave: Boolean = true) {
                val courseId = OfflineUtils.getCourseId(key)

                mPageItems.removeIf { it.key == key }
                mPageItemsMap[courseId]?.removeIf { it.key == key }

                checkIfNeedToRemoveCourse(courseId, isWithSave)

                if (isWithSave) savePageData()
            }
        })

        isLoaded = true
    }

    private fun checkIfNeedToRemoveCourse(courseId: Long, isWithSave: Boolean = true) {
        if (mModuleItemsMap[courseId].isNullOrEmpty() && mPageItemsMap[courseId].isNullOrEmpty()) {
            mCourseItems.removeIf { it.courseId == courseId }
            if (isWithSave) saveCourseData()
        }
    }

    private fun saveCourseData() {
        val userId = ApiPrefs.user?.id ?: 0L

        Paper.book("$userId").write("downloads_course_items", mCourseItems)
    }

    private fun saveModuleData() {
        val userId = ApiPrefs.user?.id ?: 0L

        Paper.book("$userId").write("downloads_module_items", mModuleItems)
    }

    private fun savePageData() {
        val userId = ApiPrefs.user?.id ?: 0L

        Paper.book("$userId").write("downloads_page_items", mPageItems)
    }
}