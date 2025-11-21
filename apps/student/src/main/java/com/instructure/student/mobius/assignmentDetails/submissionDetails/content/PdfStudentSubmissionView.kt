/*
 * Copyright (C) 2019 - present Instructure, Inc.
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
package com.instructure.student.mobius.assignmentDetails.submissionDetails.content

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.fragment.app.FragmentActivity
import com.instructure.annotations.PdfSubmissionView
import com.instructure.canvasapi2.utils.weave.WeaveCoroutine
import com.instructure.pandautils.utils.setVisible
import com.instructure.student.R
import com.instructure.student.databinding.ViewPdfStudentSubmissionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class PdfStudentSubmissionView(
    private val activity: FragmentActivity,
    private val pdfUrl: String,
    private val courseId: Long,
    private val studentAnnotationView: Boolean = false

) : PdfSubmissionView(
    activity, studentAnnotationView, courseId
) {

    private val binding: ViewPdfStudentSubmissionBinding

    override lateinit var pdfContentJob: WeaveCoroutine

    private var initJob: Job? = null
    override val progressColor: Int
        get() = R.color.login_studentAppTheme

    init {

        binding = ViewPdfStudentSubmissionBinding.inflate(LayoutInflater.from(context), this, true)
        setLoading(true)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingView.setVisible(isLoading)
        binding.contentRoot.setVisible(!isLoading)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setup()
    }

    override fun onFileInitialized() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.openExternallyButton.setText(R.string.utils_openWithAnotherApp)
            binding.openExternallyButton.isEnabled = true
        }
    }

    fun setup() {

        binding.openExternallyButton.setText(R.string.downloadingFile)
        binding.openExternallyButton.isEnabled = false
        binding.openExternallyButton.setOnClickListener {
            openPdf()
        }

        handlePdfContent(pdfUrl)
        setLoading(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        initJob?.cancel()
    }

    override fun showNoInternetDialog() {
        TODO("Not yet implemented")
    }
}