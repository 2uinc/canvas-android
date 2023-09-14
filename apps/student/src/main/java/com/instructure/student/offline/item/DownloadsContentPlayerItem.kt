package com.instructure.student.offline.item

import com.google.gson.annotations.SerializedName

data class DownloadsContentPlayerItem(val segment: DownloadsSegmentItem)

data class DownloadsSegmentItem(val elements: List<DownloadsElementItem>)

data class DownloadsElementItem(
    @SerializedName("video_uuid") val videoUuid: String,
    val overlays: List<DownloadsOverlayItem>, @SerializedName("type_id") val typeId: Int
)

data class DownloadsOverlayItem(
    val deleted: Int, val timestamp: Long, val html: String,
    @SerializedName("chapter_title") val chapterTitle: String,
    @SerializedName("is_chapter") val isChapter: Int
)