/*
 * Copyright (C) 2018 - present Instructure, Inc.
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
package com.instructure.annotations

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.FileProvider
import com.instructure.canvasapi2.managers.CanvaDocsManager
import com.instructure.canvasapi2.models.ApiValues
import com.instructure.canvasapi2.models.DocSession
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.extractCanvaDocsDomain
import com.instructure.canvasapi2.utils.extractSessionId
import com.instructure.canvasapi2.utils.weave.WeaveCoroutine
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.pandautils.utils.toast
import com.pspdfkit.utils.PdfUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@SuppressLint("ViewConstructor")
abstract class PdfSubmissionView(
    context: Context,
    private val studentAnnotationView: Boolean = false,
    private val courseId: Long
) : FrameLayout(context) {

    abstract var pdfContentJob: WeaveCoroutine

    abstract var pdfdownloadJob: Job
    protected lateinit var docSession: DocSession
    protected lateinit var apiValues: ApiValues

    @get:ColorRes
    abstract val progressColor: Int

    lateinit var file: File

    protected open fun onFileInitialized() {}

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pdfContentJob.cancel()
        pdfdownloadJob.cancel()
    }

    protected fun handlePdfContent(url: String) {
        pdfContentJob = tryWeave {
            if (url.contains("canvadoc")) {
                val redirectUrl =
                    getCanvaDocsRedirect(url, domain = ApiPrefs.overrideDomains[courseId])
                //extract the domain for API use
                if (redirectUrl.isNotEmpty()) {
                    docSession = awaitApi { CanvaDocsManager.getCanvaDoc(redirectUrl, it) }
                    docSession.let {
                        val canvaDocsDomain = extractCanvaDocsDomain(redirectUrl)
                        val pdfUrl = canvaDocsDomain + it.annotationUrls.pdfDownload
                        apiValues = ApiValues(
                            it.documentId,
                            pdfUrl,
                            extractSessionId(pdfUrl),
                            canvaDocsDomain
                        )
                    }

                    load(apiValues.pdfUrl, docSession.pdfjs.documentName)
                } else {
                    toast(R.string.errorOccurred)
                }
            } else {
                //keep things working if they don't have canvadocs
                load(url, docSession.pdfjs.documentName)
            }
        } catch {
            // Show error
            toast(R.string.errorOccurred)
            it.printStackTrace()
        }
    }


    protected fun load(url: String, docName: String) {
         pdfdownloadJob = CoroutineScope(Dispatchers.IO).launch {
            val fileName = URLDecoder.decode(docName, StandardCharsets.UTF_8.toString())
            file = PDFUtils.downloadPdf(url, fileName, context)
            withContext(Dispatchers.Main){
                onFileInitialized()
            }
        }
    }

    protected fun openPdf() {
        if(::file.isInitialized) {
            PDFUtils.openPdf(context, file)
        }
    }
}
