/*
 * Copyright (C) 2016 - present Instructure, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.instructure.student.util

import android.content.Context
import android.net.Uri
import androidx.annotation.IntegerRes
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.pandautils.loaders.OpenMediaAsyncTaskLoader
import com.instructure.student.R
import com.instructure.student.activity.CandroidPSPDFActivity
import com.instructure.pandautils.features.shareextension.ShareFileSubmissionTarget
import com.pspdfkit.PSPDFKit
import com.pspdfkit.annotations.AnnotationType
import com.pspdfkit.configuration.activity.PdfActivityConfiguration
import com.pspdfkit.configuration.activity.ThumbnailBarMode
import com.pspdfkit.configuration.page.PageFitMode
import com.pspdfkit.configuration.page.PageScrollDirection
import com.pspdfkit.preferences.PSPDFKitPreferences
import com.pspdfkit.ui.PdfActivityIntentBuilder
import com.pspdfkit.ui.special_mode.controller.AnnotationTool

object FileUtils {

    fun showPdfDocument(
        uri: Uri,
        loadedMedia: OpenMediaAsyncTaskLoader.LoadedMedia,
        context: Context,
        submissionTarget: ShareFileSubmissionTarget? = null
    ) {
        context.startActivity(loadedMedia.intent)
    }

    @IntegerRes
    fun getFileIcon(filename: String, contentType: String): Int {
        return when {
            contentType.startsWith("image") -> R.drawable.ic_image
            contentType.startsWith("video") -> R.drawable.ic_media
            contentType.startsWith("audio") -> R.drawable.ic_audio
            else -> when (filename.substringAfterLast(".")) {
                "doc", "docx", "txt", "rtf", "pdf", "xls" -> R.drawable.ic_document
                "zip", "tar", "7z", "apk", "jar", "rar" -> R.drawable.ic_attachment
                else -> R.drawable.ic_attachment
            }
        }
    }


}
