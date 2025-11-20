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
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.instructure.annotations.PdfSubmissionView
import com.instructure.canvasapi2.managers.CanvaDocsManager
import com.instructure.canvasapi2.models.ApiValues
import com.instructure.canvasapi2.models.DocSession
import com.instructure.canvasapi2.models.canvadocs.CanvaDocAnnotation
import com.instructure.canvasapi2.utils.Analytics
import com.instructure.canvasapi2.utils.AnalyticsEventConstants
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.Logger
import com.instructure.canvasapi2.utils.weave.WeaveCoroutine
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.loginapi.login.dialog.NoInternetConnectionDialog
import com.instructure.pandautils.utils.onClick
import com.instructure.pandautils.utils.setGone
import com.instructure.pandautils.utils.setVisible
import com.instructure.pandautils.views.ProgressiveCanvasLoadingView
import com.instructure.student.AnnotationComments.AnnotationCommentListFragment
import com.instructure.student.R
import com.instructure.student.databinding.ViewPdfStudentSubmissionBinding
import com.instructure.student.router.RouteMatcher
import com.pspdfkit.preferences.PSPDFKitPreferences
import com.pspdfkit.ui.inspector.PropertyInspectorCoordinatorLayout
import com.pspdfkit.ui.special_mode.manager.AnnotationManager
import com.pspdfkit.ui.toolbar.ToolbarCoordinatorLayout
import kotlinx.coroutines.Job
import okhttp3.ResponseBody
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@SuppressLint("ViewConstructor")
class PdfStudentSubmissionView(
    private val activity: FragmentActivity,
    private val pdfUrl: String,
    private val courseId: Long,
    private val studentAnnotationView: Boolean = false

) : PdfSubmissionView(
    activity, studentAnnotationView, courseId
){

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

    fun setup() {

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