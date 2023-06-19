package com.instructure.student.offline.item

data class DownloadsModuleItem(
    val key: String, val courseId: Long, val moduleId: Long, val moduleName: String,
    val moduleItemId: Long, val moduleItemName: String, val type: Int
)
