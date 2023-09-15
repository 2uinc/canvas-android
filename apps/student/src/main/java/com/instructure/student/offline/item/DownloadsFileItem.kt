package com.instructure.student.offline.item

data class DownloadsFileItem(
    val key: String, val courseId: Long, val fileName: String, val contentType: String
)
