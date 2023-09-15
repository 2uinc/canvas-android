package com.instructure.student.offline.activity

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.instructure.pandautils.utils.*
import com.instructure.student.databinding.ActivityDownloadsCourseBinding
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.util.DisableableAppBarLayoutBehavior
import com.instructure.student.util.StudentPrefs
import kotlin.math.abs

class DownloadsCourseActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {

    private lateinit var binding: ActivityDownloadsCourseBinding

    private var mCourseId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mCourseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)

        DownloadsRepository.getCourses().find { it.courseId == mCourseId }?.let {
            initView(it)
        }
    }

    override fun onResume() {
        super.onResume()

        if (DownloadsRepository.getModuleItems(mCourseId).isNullOrEmpty()) {
            binding.modulesLayout.setGone()
        }

        if (DownloadsRepository.getPageItems(mCourseId).isNullOrEmpty()) {
            binding.pagesLayout.setGone()
        }

        if (DownloadsRepository.getFileItems(mCourseId).isNullOrEmpty()) {
            binding.filesLayout.setGone()
        }

        if (binding.modulesLayout.visibility == View.GONE &&
            binding.pagesLayout.visibility == View.GONE &&
            binding.filesLayout.visibility == View.GONE
        ) {
            finish()
        }
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
        val percentage = abs(verticalOffset).div(appBarLayout?.totalScrollRange?.toFloat() ?: 1F)

        binding.apply {
            if (percentage <= 0.3F) {
                val toolbarAnimation = ObjectAnimator.ofFloat(
                    headerInclude.courseBrowserHeader, View.ALPHA,
                    headerInclude.courseBrowserHeader.alpha, 0F
                )
                val titleAnimation = ObjectAnimator.ofFloat(
                    courseBrowserTitle, View.ALPHA, courseBrowserTitle.alpha, 1F
                )
                val subtitleAnimation = ObjectAnimator.ofFloat(
                    courseBrowserSubtitle, View.ALPHA, courseBrowserSubtitle.alpha, 0.8F
                )

                toolbarAnimation?.setAutoCancel(true)
                titleAnimation?.setAutoCancel(true)
                subtitleAnimation?.setAutoCancel(true)

                toolbarAnimation?.target = headerInclude.courseBrowserHeader
                titleAnimation?.target = courseBrowserTitle
                subtitleAnimation?.target = courseBrowserSubtitle

                toolbarAnimation?.duration = 200
                titleAnimation?.duration = 320
                subtitleAnimation?.duration = 320

                toolbarAnimation?.start()
                titleAnimation?.start()
                subtitleAnimation?.start()

            } else if (percentage > 0.7F) {
                val toolbarAnimation = ObjectAnimator.ofFloat(
                    headerInclude.courseBrowserHeader, View.ALPHA,
                    headerInclude.courseBrowserHeader.alpha, 1F
                )
                val titleAnimation = ObjectAnimator.ofFloat(
                    courseBrowserTitle, View.ALPHA, courseBrowserTitle.alpha, 0F
                )
                val subtitleAnimation = ObjectAnimator.ofFloat(
                    courseBrowserSubtitle, View.ALPHA, courseBrowserSubtitle.alpha, 0F
                )

                toolbarAnimation?.setAutoCancel(true)
                titleAnimation?.setAutoCancel(true)
                subtitleAnimation?.setAutoCancel(true)

                toolbarAnimation?.target = headerInclude.courseBrowserHeader
                titleAnimation?.target = courseBrowserTitle
                subtitleAnimation?.target = courseBrowserSubtitle

                toolbarAnimation?.duration = 200
                titleAnimation?.duration = 200
                subtitleAnimation?.duration = 200

                toolbarAnimation?.start()
                titleAnimation?.start()
                subtitleAnimation?.start()
            }
        }
    }

    private fun initView(courseItem: DownloadsCourseItem) {
        binding.apply {
            appBarLayout.addOnOffsetChangedListener(this@DownloadsCourseActivity)
            collapsingToolbarLayout.isTitleEnabled = false

            courseBrowserTitle.text = courseItem.title

            ColorKeeper.cachedThemedColors["course_${courseItem.courseId}"]?.let { themedColor ->
                ViewStyler.setStatusBarDark(
                    this@DownloadsCourseActivity, themedColor.backgroundColor()
                )

                courseImage.setCourseImage(
                    courseItem.logoPath, themedColor.backgroundColor(),
                    !StudentPrefs.hideCourseColorOverlay
                )

                collapsingToolbarLayout.setContentScrimColor(themedColor.backgroundColor())
                noOverlayToolbar.setBackgroundColor(themedColor.backgroundColor())

                modulesImageView.setColorFilter(themedColor.textAndIconColor())
                pagesImageView.setColorFilter(themedColor.textAndIconColor())
                filesImageView.setColorFilter(themedColor.textAndIconColor())
            }

            courseBrowserSubtitle.text = courseItem.termsName
            headerInclude.courseBrowserHeader.setTitleAndSubtitle(
                courseItem.title, courseItem.termsName ?: ""
            )

            overlayToolbar.setupAsBackButton { finish() }
            noOverlayToolbar.setupAsBackButton { finish() }
            noOverlayToolbar.title = courseItem.title
            noOverlayToolbar.subtitle = courseItem.termsName
            updateToolbarVisibility()

            val hasImage = courseItem.logoPath.isNotEmpty()
            val hideImagePlaceholder = StudentPrefs.hideCourseColorOverlay && !hasImage

            if (a11yManager.isSwitchAccessEnabled || hideImagePlaceholder) {
                appBarLayout.setExpanded(false, false)
                appBarLayout.isActivated = false
                appBarLayout.isFocusable = false
                val layoutParams = (appBarLayout.layoutParams as? CoordinatorLayout.LayoutParams)
                val behavior = layoutParams?.behavior as? DisableableAppBarLayoutBehavior
                behavior?.isEnabled = false
            }

            if (DownloadsRepository.getModuleItems(courseItem.courseId).isNullOrEmpty()) {
                modulesLayout.setGone()

            } else {
                modulesLayout.setOnClickListener {
                    startActivity(
                        DownloadsModulesActivity.newIntent(
                            this@DownloadsCourseActivity, courseItem.courseId, courseItem.title
                        )
                    )
                }
            }

            if (DownloadsRepository.getPageItems(courseItem.courseId).isNullOrEmpty()) {
                pagesLayout.setGone()

            } else {
                pagesLayout.setOnClickListener {
                    startActivity(
                        DownloadsPagesActivity.newIntent(
                            this@DownloadsCourseActivity, courseItem.courseId, courseItem.title
                        )
                    )
                }
            }

            if (DownloadsRepository.getFileItems(courseItem.courseId).isNullOrEmpty()) {
                filesLayout.setGone()

            } else {
                filesLayout.setOnClickListener {
                    startActivity(
                        DownloadsFilesActivity.newIntent(
                            this@DownloadsCourseActivity, courseItem.courseId, courseItem.title
                        )
                    )
                }
            }
        }
    }

    private fun updateToolbarVisibility() {
        binding.apply {
            val useOverlay = !StudentPrefs.hideCourseColorOverlay
            noOverlayToolbar.setVisible(!useOverlay)
            overlayToolbar.setVisible(useOverlay)
            courseHeader.setVisible(useOverlay)
        }
    }

    companion object {

        private const val EXTRA_COURSE_ID = "EXTRA_COURSE_ID"

        @JvmStatic
        fun newIntent(context: Context, courseId: Long): Intent {
            return Intent(context, DownloadsCourseActivity::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
        }
    }
}