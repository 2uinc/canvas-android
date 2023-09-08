package com.instructure.student.offline.item

import com.google.gson.annotations.SerializedName

data class DownloadsVideoItem(
    val sources: List<DownloadsSourceItem>, val trackURL: String, val transcription: String
)

data class DownloadsSourceItem(@SerializedName("size_bytes") val size: Long, val url: String)
