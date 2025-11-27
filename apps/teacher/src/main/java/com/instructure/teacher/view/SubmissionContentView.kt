/*
 * Copyright (C) 2017 - present Instructure, Inc.
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
package com.instructure.teacher.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.instructure.annotations.PdfSubmissionView
import com.instructure.canvasapi2.managers.CanvaDocsManager
import com.instructure.canvasapi2.managers.EnrollmentManager
import com.instructure.canvasapi2.managers.FeaturesManager
import com.instructure.canvasapi2.managers.SubmissionManager
import com.instructure.canvasapi2.models.ApiValues
import com.instructure.canvasapi2.models.Assignee
import com.instructure.canvasapi2.models.Assignment
import com.instructure.canvasapi2.models.Assignment.SubmissionType
import com.instructure.canvasapi2.models.Attachment
import com.instructure.canvasapi2.models.Course
import com.instructure.canvasapi2.models.DocSession
import com.instructure.canvasapi2.models.Enrollment
import com.instructure.canvasapi2.models.GradeableStudentSubmission
import com.instructure.canvasapi2.models.GroupAssignee
import com.instructure.canvasapi2.models.QuizSubmission
import com.instructure.canvasapi2.models.StudentAssignee
import com.instructure.canvasapi2.models.Submission
import com.instructure.canvasapi2.models.User
import com.instructure.canvasapi2.models.canvadocs.CanvaDocAnnotation
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.canvasapi2.utils.DateHelper
import com.instructure.canvasapi2.utils.Logger
import com.instructure.canvasapi2.utils.Pronouns
import com.instructure.canvasapi2.utils.exhaustive
import com.instructure.canvasapi2.utils.validOrNull
import com.instructure.canvasapi2.utils.weave.WeaveCoroutine
import com.instructure.canvasapi2.utils.weave.awaitApi
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.interactions.router.Route
import com.instructure.interactions.router.RouteContext
import com.instructure.pandautils.activities.BaseViewMediaActivity
import com.instructure.pandautils.binding.BindableSpinnerAdapter
import com.instructure.pandautils.features.assignmentdetails.AssignmentDetailsAttemptItemViewModel
import com.instructure.pandautils.features.assignmentdetails.AssignmentDetailsAttemptViewData
import com.instructure.pandautils.interfaces.ShareableFile
import com.instructure.pandautils.utils.AssignmentUtils2
import com.instructure.pandautils.utils.PermissionUtils
import com.instructure.pandautils.utils.ProfileUtils
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.color
import com.instructure.pandautils.utils.iconRes
import com.instructure.pandautils.utils.isAccessibilityEnabled
import com.instructure.pandautils.utils.onClick
import com.instructure.pandautils.utils.orDefault
import com.instructure.pandautils.utils.setGone
import com.instructure.pandautils.utils.setVisible
import com.instructure.pandautils.utils.setupAvatarA11y
import com.instructure.pandautils.utils.toast
import com.instructure.pandautils.utils.viewExternally
import com.instructure.pandautils.views.ExpandCollapseAnimation
import com.instructure.pandautils.views.ProgressiveCanvasLoadingView
import com.instructure.pandautils.views.RecordingMediaType
import com.instructure.pandautils.views.ViewPagerNonSwipeable
import com.instructure.teacher.R
import com.instructure.teacher.activities.SpeedGraderActivity
import com.instructure.teacher.adapters.StudentContextFragment
import com.instructure.teacher.databinding.ViewSubmissionContentBinding
import com.instructure.teacher.dialog.NoInternetConnectionDialog
import com.instructure.teacher.events.RationedBusEvent
import com.instructure.teacher.fragments.SimpleWebViewFragment
import com.instructure.teacher.fragments.SpeedGraderCommentsFragment
import com.instructure.teacher.fragments.SpeedGraderEmptyFragment
import com.instructure.teacher.fragments.SpeedGraderFilesFragment
import com.instructure.teacher.fragments.SpeedGraderGradeFragment
import com.instructure.teacher.fragments.SpeedGraderLtiSubmissionFragment
import com.instructure.teacher.fragments.SpeedGraderQuizSubmissionFragment
import com.instructure.teacher.fragments.SpeedGraderTextSubmissionFragment
import com.instructure.teacher.fragments.SpeedGraderUrlSubmissionFragment
import com.instructure.teacher.fragments.ViewImageFragment
import com.instructure.teacher.fragments.ViewMediaFragment
import com.instructure.teacher.fragments.ViewUnsupportedFileFragment
import com.instructure.teacher.router.RouteMatcher
import com.instructure.teacher.utils.Const
import com.instructure.teacher.utils.getColorCompat
import com.instructure.teacher.utils.getResForSubmission
import com.instructure.teacher.utils.getState
import com.instructure.teacher.utils.iconRes
import com.instructure.teacher.utils.isTablet
import com.instructure.teacher.utils.setAnonymousAvatar
import com.instructure.teacher.utils.setupMenu
import com.instructure.teacher.utils.transformForQuizGrading
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.coroutines.Job
import okhttp3.ResponseBody
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("ViewConstructor")
class SubmissionContentView(
    context: Context,
    private val studentSubmission: GradeableStudentSubmission,
    private val assignment: Assignment,
    private val course: Course,
    var initialTabIndex: Int = 0,
) : PdfSubmissionView(context, courseId = course.id) {

    override lateinit var pdfContentJob: WeaveCoroutine
    private val binding: ViewSubmissionContentBinding

    override val progressColor: Int
        get() = R.color.login_teacherAppTheme

    private var containerId: Int = 0
    private val assignee: Assignee get() = studentSubmission.assignee
    private val rootSubmission: Submission? get() = studentSubmission.submission
    private val bottomViewPager: ViewPagerNonSwipeable

    private var initJob: Job? = null
    private var deleteJob: Job? = null
    private var studentAnnotationJob: Job? = null

    private var isCleanedUp = false
    private val activity: SpeedGraderActivity get() = context as SpeedGraderActivity
    private val gradeFragment by lazy {
        SpeedGraderGradeFragment.newInstance(
            rootSubmission,
            assignment,
            course,
            assignee
        )
    }

    val hasUnsavedChanges: Boolean
        get() = gradeFragment.hasUnsavedChanges

    private var selectedSubmission: Submission? = null
    private var assignmentEnhancementsEnabled = false


    //region view lifecycle
    init {
        binding = ViewSubmissionContentBinding.inflate(LayoutInflater.from(context), this, true)

        setLoading(true)

        containerId = View.generateViewId()
        binding.content.id = containerId
        bottomViewPager = binding.bottomViewPager.apply { id = View.generateViewId() }

        if (isAccessibilityEnabled(context)) {
            binding.slidingUpPanelLayout?.anchorPoint = 1.0f
        }
        setupExpandCollapseToggle()
    }

    private fun setupExpandCollapseToggle() {
        binding.toggleImageView?.let { toggle ->
            binding.panelContent?.let { panel ->
                val panelWidth = resources.getDimensionPixelOffset(R.dimen.speedgraderPanelWidth)
                val animation = ExpandCollapseAnimation(
                    panel,
                    panelWidth,
                    0
                ) {
                    if (panel.width > 50) {
                        binding.toggleImageView.setImageResource(R.drawable.ic_collapse_horizontal)
                        binding.toggleImageView.contentDescription =
                            context.getString(R.string.collapseGradePanel)
                    } else {
                        binding.toggleImageView.setImageResource(R.drawable.ic_expand_horizontal)
                        binding.toggleImageView.contentDescription =
                            context.getString(R.string.expandGradePanel)
                    }
                }
                animation.duration = 500
                toggle.onClick {
                    if (!animation.hasStarted() || animation.hasEnded()) {
                        panel.clearAnimation()
                        if (panel.width > 0) {
                            animation.updateValues(panelWidth, 0)
                        } else {
                            animation.updateValues(0, panelWidth)
                        }
                        panel.startAnimation(animation)
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupToolbar(assignee)
        obtainSubmissionData()
    }

    private fun setLoading(isLoading: Boolean) = with(binding) {
        retryLoadingContainer.setGone()
        loadingView.setVisible(isLoading)
        slidingUpPanelLayout?.setVisible(!isLoading)
        panelContent?.setVisible(!isLoading)
        contentRoot.setVisible(!isLoading)
        divider?.setVisible(!isLoading)
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    private fun obtainSubmissionData() {
        initJob = tryWeave {
            if (!studentSubmission.isCached) {
                // Determine if the logged in user is an Observer
                val enrollments = awaitApi<List<Enrollment>> {
                    EnrollmentManager.getObserveeEnrollments(
                        true,
                        it
                    )
                }
                val isObserver = enrollments.any { it.isObserver }
                if (isObserver) {
                    // Get the first observee associated with this course
                    val observee = enrollments.first { it.courseId == course.id }
                    studentSubmission.submission = awaitApi<Submission> {
                        SubmissionManager.getSingleSubmission(
                            course.id,
                            assignment.id,
                            studentSubmission.assigneeId,
                            it,
                            true
                        )
                    }
                } else {
                    // Get the user's submission normally
                    studentSubmission.submission = awaitApi<Submission> {
                        SubmissionManager.getSingleSubmission(
                            course.id,
                            assignment.id,
                            studentSubmission.assigneeId,
                            it,
                            true
                        )
                    }
                }
                val featureFlags = FeaturesManager.getEnabledFeaturesForCourseAsync(course.id, true)
                    .await().dataOrNull
                assignmentEnhancementsEnabled =
                    featureFlags?.contains("assignments_2_student").orDefault()
                studentSubmission.isCached = true
            }
            setup()
        } catch {
            with(binding) {
                loadingView.setGone()
                retryLoadingContainer.setVisible()
                retryLoadingButton.onClick {
                    setLoading(true)
                    obtainSubmissionData()
                }
            }
        }
    }

    fun setup() {
        if (SubmissionType.ONLINE_QUIZ in assignment.getSubmissionTypes()) rootSubmission?.transformForQuizGrading()
        setupToolbar(assignee)
        setupDueDate(assignment)
        setupSubmissionVersions(
            rootSubmission?.submissionHistory?.filterNotNull()?.filter { it.attempt > 0 })
        setSubmission(rootSubmission)
        setupSlidingPanel()
        //we must set up the sliding panel prior to registering to the event
        EventBus.getDefault().register(this)
        setLoading(false)
    }

    private fun setupDueDate(assignment: Assignment) = with(binding) {
        if (assignment.dueDate != null) {
            dueDateTextView?.setVisible(true)
            dueDateTextView?.text =
                DateHelper.getDateAtTimeString(context, R.string.due_dateTime, assignment.dueDate)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        initJob?.cancel()
        studentAnnotationJob?.cancel()
        bottomViewPager.adapter = null
        EventBus.getDefault().unregister(this)
    }

    @SuppressLint("CommitTransaction")
    fun performCleanup() {
        isCleanedUp = true
    }
    //endregion

    //region private helpers
    private fun setSubmission(submission: Submission?) {
        selectedSubmission = submission
        val content = when {
            SubmissionType.NONE.apiString in assignment.submissionTypesRaw -> NoneContent
            SubmissionType.ON_PAPER.apiString in assignment.submissionTypesRaw -> OnPaperContent
            submission?.submissionType == null -> NoSubmissionContent
            assignment.getState(submission) == AssignmentUtils2.ASSIGNMENT_STATE_MISSING ||
                    assignment.getState(submission) == AssignmentUtils2.ASSIGNMENT_STATE_GRADED_MISSING -> NoSubmissionContent

            else -> when (Assignment.getSubmissionTypeFromAPIString(submission.submissionType!!)) {

                // LTI submission
                SubmissionType.BASIC_LTI_LAUNCH -> ExternalToolContent(
                    submission.previewUrl.validOrNull() ?: assignment.url.validOrNull()
                    ?: assignment.htmlUrl ?: ""
                )

                // Text submission
                SubmissionType.ONLINE_TEXT_ENTRY -> TextContent(submission.body ?: "")

                // Media submission
                SubmissionType.MEDIA_RECORDING -> submission.mediaComment?.let {
                    MediaContent(
                        uri = Uri.parse(it.url),
                        contentType = it.contentType ?: "",
                        displayName = it.displayName
                    )
                } ?: UnsupportedContent

                // File uploads
                SubmissionType.ONLINE_UPLOAD -> getAttachmentContent(submission.attachments[0])

                // URL Submission
                SubmissionType.ONLINE_URL -> UrlContent(
                    submission.url!!,
                    submission.attachments.firstOrNull()?.url
                )

                // Quiz Submission
                SubmissionType.ONLINE_QUIZ -> handleQuizSubmissionType(submission)

                // Discussion Submission
                SubmissionType.DISCUSSION_TOPIC -> DiscussionContent(submission.previewUrl)

                SubmissionType.STUDENT_ANNOTATION -> {
                    StudentAnnotationContent(submission.id, submission.attempt)
                }

                // Unsupported type
                else -> UnsupportedContent
            }
        }
    }

    private fun handleQuizSubmissionType(submission: Submission): GradeableContent {
        return if (assignment.anonymousGrading) {
            AnonymousSubmissionContent
        } else {
            QuizContent(
                course.id,
                assignment.id,
                submission.userId,
                submission.previewUrl ?: "",
                QuizSubmission.parseWorkflowState(submission.workflowState!!) == QuizSubmission.WorkflowState.PENDING_REVIEW
            )
        }
    }

    private fun getAttachmentContent(attachment: Attachment): GradeableContent {
        var type = attachment.contentType ?: return OtherAttachmentContent(attachment)
        if (type == "*/*") {
            val fileExtension = attachment.filename?.substringAfterLast(".") ?: ""
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
                ?: MimeTypeMap.getFileExtensionFromUrl(attachment.url)
                        ?: type
        }
        return when {
            type == "application/pdf" || (attachment.previewUrl?.contains(Const.CANVADOC)
                ?: false) -> {
                if (attachment.previewUrl?.contains(Const.CANVADOC) == true) {
                    PdfContent(attachment.previewUrl ?: "")
                } else {
                    PdfContent(attachment.url ?: "")
                }
            }

            type.startsWith("audio") || type.startsWith("video") -> with(attachment) {
                MediaContent(
                    uri = Uri.parse(url),
                    thumbnailUrl = thumbnailUrl,
                    contentType = contentType,
                    displayName = displayName
                )
            }

            type.startsWith("image") -> ImageContent(attachment.url ?: "", attachment.contentType!!)
            else -> OtherAttachmentContent(attachment)
        }
    }

    private fun setupSubmissionVersions(unsortedSubmissions: List<Submission>?) =
        with(binding.submissionVersionsSpinner) {
            if (unsortedSubmissions.isNullOrEmpty()) {
                setGone()
            } else {
                unsortedSubmissions.sortedByDescending { it.submittedAt }.let { submissions ->
                    val itemViewModels = submissions.mapIndexed { index, submission ->
                        AssignmentDetailsAttemptItemViewModel(
                            AssignmentDetailsAttemptViewData(
                                context.getString(
                                    R.string.attempt,
                                    unsortedSubmissions.size - index
                                ),
                                submission.submittedAt?.let { getFormattedAttemptDate(it) }
                                    .orEmpty()
                            )
                        )
                    }
                    adapter = BindableSpinnerAdapter(
                        context,
                        R.layout.item_submission_attempt_spinner,
                        itemViewModels
                    )
                    setSelection(0, false)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            EventBus.getDefault()
                                .post(SubmissionSelectedEvent(submissions[position]))
                        }
                    }

                    if (submissions.size > 1) {
                        setVisible()
                    } else {
                        setGone()
                        binding.attemptView?.apply {
                            itemViewModel = itemViewModels.firstOrNull()
                            root.setVisible()
                        }
                    }
                }
            }
        }

    private fun getFormattedAttemptDate(date: Date): String = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault()
    ).format(date)

    private fun setupToolbar(assignee: Assignee) = with(binding) {
        val assigneeName = if (assignment.anonymousGrading) {
            resources.getString(R.string.anonymousStudentLabel)
        } else {
            Pronouns.span(assignee.name, assignee.pronouns)
        }
        titleTextView.text = assigneeName

        if (studentSubmission.isCached) {
            // get string/color resources for assignment status
            val (stringRes, colorRes) = assignment.getResForSubmission(rootSubmission)
            if (stringRes == -1 || colorRes == -1) {
                contentDescription = titleTextView.text
                subtitleTextView.setGone()
            } else {
                contentDescription = "${titleTextView.text}, ${resources.getString(stringRes)}"
                subtitleTextView.setText(stringRes)
                subtitleTextView.setTextColor(context.getColorCompat(colorRes))
            }
        }

        speedGraderToolbar.foregroundGravity = Gravity.CENTER_VERTICAL
        ViewStyler.setToolbarElevationSmall(context, speedGraderToolbar)

        when {
            assignment.anonymousGrading -> userImageView.setAnonymousAvatar()
            assignee is GroupAssignee -> userImageView.setImageResource(assignee.iconRes)
            assignee is StudentAssignee -> {
                ProfileUtils.loadAvatarForUser(
                    userImageView,
                    assignee.student.name,
                    assignee.student.avatarUrl
                )
                userImageView.setupAvatarA11y(assignee.name)
                userImageView.onClick {
                    val bundle = StudentContextFragment.makeBundle(assignee.id, course.id)
                    RouteMatcher.route(
                        activity as FragmentActivity,
                        Route(StudentContextFragment::class.java, null, bundle)
                    )
                }
            }
        }

        if (assignee is GroupAssignee && !assignment.anonymousGrading) setupGroupMemberList(assignee)
    }

    private fun setupGroupMemberList(assignee: GroupAssignee) = with(binding) {
        assigneeWrapperView.onClick {
            val popup = ListPopupWindow(context)
            popup.anchorView = it
            popup.setAdapter(object : ArrayAdapter<User>(context, 0, assignee.students) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val user = getItem(position)
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(R.layout.adapter_speed_grader_group_member, parent, false)
                    val memberAvatarView = view.findViewById<ImageView>(R.id.memberAvatarView)
                    ProfileUtils.loadAvatarForUser(memberAvatarView, user?.name, user?.avatarUrl)
                    val memberNameView = view.findViewById<TextView>(R.id.memberNameView)
                    memberNameView.text = Pronouns.span(user?.name, user?.pronouns)
                    return view
                }
            })
            popup.setContentWidth(resources.getDimensionPixelSize(R.dimen.speedgraderGroupMemberListWidth))
            popup.verticalOffset = -assigneeWrapperView.height
            popup.isModal = true // For a11y
            popup.setOnItemClickListener { _, _, position, _ ->
                val bundle =
                    StudentContextFragment.makeBundle(assignee.students[position].id, course.id)
                RouteMatcher.route(
                    activity as FragmentActivity,
                    Route(StudentContextFragment::class.java, null, bundle)
                )
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun showMessageFragment(@StringRes stringRes: Int) =
        showMessageFragment(resources.getString(stringRes))

    private fun showMessageFragment(@StringRes titleRes: Int, @StringRes messageRes: Int) =
        showMessageFragment(resources.getString(titleRes), resources.getString(messageRes))

    private fun showMessageFragment(message: String) {
        val fragment = SpeedGraderEmptyFragment.newInstance(message = message)
    }

    private fun showMessageFragment(title: String, message: String) {
        val fragment = SpeedGraderEmptyFragment.newInstance(title = title, message = message)
    }

    private fun setupSlidingPanel() = with(binding) {

        slidingUpPanelLayout?.addPanelSlideListener(object :
            SlidingUpPanelLayout.PanelSlideListener {

            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                adjustPanelHeights(slideOffset)
            }

            override fun onPanelStateChanged(
                panel: View?,
                previousState: SlidingUpPanelLayout.PanelState?,
                newState: SlidingUpPanelLayout.PanelState?
            ) {
                if (newState != previousState) {
                    // We don't want to update for all states, just these three
                    when (newState) {
                        SlidingUpPanelLayout.PanelState.ANCHORED -> {
                            submissionVersionsSpinner.isClickable = true
                            postPanelEvent(newState, 0.5f)
                            contentRoot.importantForAccessibility =
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        }

                        SlidingUpPanelLayout.PanelState.EXPANDED -> {
                            submissionVersionsSpinner.isClickable = false
                            postPanelEvent(newState, 1.0f)
                            contentRoot.importantForAccessibility =
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        }

                        SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                            submissionVersionsSpinner.isClickable = true
                            //fix for rotating when the panel is collapsed
                            postPanelEvent(newState, 0.0f)
                            contentRoot.importantForAccessibility =
                                View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }

                        else -> {}
                    }
                }
            }
        })
    }

    private fun postPanelEvent(panelState: SlidingUpPanelLayout.PanelState, offset: Float) {
        val event = SlidingPanelAnchorEvent(panelState, offset)
        EventBus.getDefault().postSticky(event)
    }

    private fun adjustPanelHeights(offset: Float) {
        //Adjusts the panel content sizes based on the position of the sliding portion of the view
        val maxHeight = binding.contentRoot.height
        if (offset < 0 || maxHeight == 0) return

        val adjustedHeight = Math.abs(maxHeight * offset)

        if (offset >= 0.50F) { //Prevents resizing views when not necessary
            bottomViewPager.layoutParams?.height = adjustedHeight.toInt()
            bottomViewPager.requestLayout()
        }
    }

    private fun showVideoCommentDialog() = with(binding) {
        activity.disableViewPager()
        floatingRecordingView.setContentType(RecordingMediaType.Video)
        floatingRecordingView.startVideoView()
        floatingRecordingView.recordingCallback = {
            it?.let {
                EventBus.getDefault().post(
                    UploadMediaCommentEvent(
                        it,
                        assignment.id,
                        assignment.courseId,
                        assignee.id,
                        selectedSubmission?.attempt
                    )
                )
            }
        }
        floatingRecordingView.stoppedCallback = {
            activity.enableViewPager()
            EventBus.getDefault().post(MediaCommentDialogClosedEvent())
        }
        floatingRecordingView.replayCallback = {
            val bundle = BaseViewMediaActivity.makeBundle(
                it,
                "video",
                context.getString(R.string.videoCommentReplay),
                true
            )
            RouteMatcher.route(activity as FragmentActivity, Route(bundle, RouteContext.MEDIA))
        }
    }

    private fun showAudioCommentDialog() = with(binding) {
        activity.disableViewPager()
        floatingRecordingView.setContentType(RecordingMediaType.Audio)
        floatingRecordingView.setVisible()
        floatingRecordingView.stoppedCallback = {
            activity.enableViewPager()
            EventBus.getDefault().post(MediaCommentDialogClosedEvent())
        }
        floatingRecordingView.recordingCallback = {
            it?.let {
                EventBus.getDefault().post(
                    UploadMediaCommentEvent(
                        it,
                        assignment.id,
                        assignment.courseId,
                        assignee.id,
                        selectedSubmission?.attempt
                    )
                )
            }
        }
    }


    class SubmissionSelectedEvent(val submission: Submission?)
    class SubmissionFileSelectedEvent(val submissionId: Long, val attachment: Attachment)
    class QuizSubmissionGradedEvent(submission: Submission) :
        RationedBusEvent<Submission>(submission)

    class SlidingPanelAnchorEvent(
        val anchorPosition: SlidingUpPanelLayout.PanelState,
        val offset: Float
    )

    class CommentTextFocusedEvent(val assigneeId: Long)
    class AnnotationCommentAdded(val annotation: CanvaDocAnnotation, val assigneeId: Long)
    class AnnotationCommentEdited(val annotation: CanvaDocAnnotation, val assigneeId: Long)
    class AnnotationCommentDeleted(
        val annotation: CanvaDocAnnotation,
        val isHeadAnnotation: Boolean,
        val assigneeId: Long
    )

    class AnnotationCommentDeleteAcknowledged(
        val annotationList: List<CanvaDocAnnotation>,
        val assigneeId: Long
    )

    class TabSelectedEvent(val selectedTabIdx: Int)
    class UploadMediaCommentEvent(
        val file: File,
        val assignmentId: Long,
        val courseId: Long,
        val assigneeId: Long,
        val attemptId: Long?
    )


    sealed class GradeableContent
    object NoSubmissionContent : GradeableContent()
    object NoneContent : GradeableContent()
    class ExternalToolContent(val url: String) : GradeableContent()
    object OnPaperContent : GradeableContent()
    object UnsupportedContent : GradeableContent()
    class OtherAttachmentContent(val attachment: Attachment) : GradeableContent()
    class PdfContent(val url: String) : GradeableContent()
    class TextContent(val text: String) : GradeableContent()
    class ImageContent(val url: String, val contentType: String) : GradeableContent()
    class UrlContent(val url: String, val previewUrl: String?) : GradeableContent()
    class DiscussionContent(val previewUrl: String?) : GradeableContent()
    class StudentAnnotationContent(val submissionId: Long, val attempt: Long) : GradeableContent()
    object AnonymousSubmissionContent : GradeableContent()

    class MediaCommentDialogClosedEvent
    class AudioPermissionGrantedEvent(val assigneeId: Long)
    class VideoPermissionGrantedEvent(val assigneeId: Long)


    class QuizContent(
        val courseId: Long,
        val assignmentId: Long,
        val studentId: Long,
        val url: String,
        val pendingReview: Boolean
    ) : GradeableContent()

    class MediaContent(
        val uri: Uri,
        val contentType: String? = null,
        val thumbnailUrl: String? = null,
        val displayName: String? = null
    ) : GradeableContent()
}
