/*
 * Copyright (C) 2024 - present Instructure, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package com.instructure.canvasapi2.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class StudioMediaMetadata(
    val id: Long,
    @SerializedName("lti_launch_id")
    val ltiLaunchId: String,
    val title: String,
    @SerializedName("mime_type")
    val mimeType: String,
    val size: Long,
    val captions: List<StudioCaption>,
    val url: String,
) : Parcelable

@Parcelize
data class StudioCaption(
    @SerializedName("srclang")
    val srcLang: String,
    val data: String,
    val label: String,
) : Parcelable