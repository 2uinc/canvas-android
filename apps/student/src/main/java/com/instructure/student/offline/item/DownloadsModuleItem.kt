package com.instructure.student.offline.item

data class DownloadsModuleItem(
    val courseId: Long, val moduleId: Long, val moduleName: String, val moduleItemId: Long,
    val moduleItemName: String, val type: String
)
