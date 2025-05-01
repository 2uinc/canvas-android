package com.instructure.student.offline.item

import com.google.gson.annotations.SerializedName

data class DownloadsOysterVideoItem(
    val sources: List<DownloadsOysterSourceItem>, val trackURL: String, val transcription: String
)

data class DownloadsOysterSourceItem(@SerializedName("size_bytes") val size: Long, val url: String)
