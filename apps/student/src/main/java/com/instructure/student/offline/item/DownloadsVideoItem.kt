package com.instructure.student.offline.item

import com.google.gson.annotations.SerializedName

data class DownloadsVideoItem(
    val sources: List<DownloadsSourceItem>, val tracks: List<DownloadsTrackItem>
)

data class DownloadsSourceItem(@SerializedName("size_bytes") val size: Long, val label: String)

data class DownloadsTrackItem(
    val uuid: String?, @SerializedName("external_id") val externalId: String,
    @SerializedName("captions_project_id") val projectId: String
)
