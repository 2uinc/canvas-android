package com.instructure.student.offline.item

data class DownloadsCourseItem(
    var index: Int, val courseId: Long, var title: String, val courseCode: String,
    var logoPath: String, val termsName: String? = ""
)
