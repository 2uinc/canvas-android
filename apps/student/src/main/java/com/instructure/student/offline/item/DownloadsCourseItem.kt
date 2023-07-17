package com.instructure.student.offline.item

data class DownloadsCourseItem(
    val courseId: Long, var title: String, val courseCode: String, val logoPath: String,
    val termsName: String? = ""
)
