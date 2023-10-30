package com.instructure.student.offline.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import com.instructure.pandautils.utils.*
import com.instructure.student.R
import com.instructure.student.databinding.ActivityDownloadsCoursesBinding
import com.instructure.student.decorations.VerticalGridSpacingDecoration
import com.instructure.student.offline.adapter.DownloadsCoursesAdapter
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.util.StudentPrefs
import com.twou.offline.Offline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DownloadsCoursesActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityDownloadsCoursesBinding

    private var mAlertDialog: AlertDialog? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initView()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()

        (binding.recyclerView.adapter as? DownloadsCoursesAdapter)
            ?.setCourses(DownloadsRepository.getCourses())

        checkForEmptyState()
    }

    override fun onDestroy() {
        mAlertDialog?.dismiss()
        super.onDestroy()
    }

    private fun initView() {
        binding.toolbar.setupAsBackButton { finish() }
        binding.toolbar.setTitle(R.string.downloads_courses_title)

        ViewStyler.themeToolbarColored(
            this, binding.toolbar, ThemePrefs.primaryColor, ThemePrefs.primaryTextColor
        )

        val courseColumns =
            if (StudentPrefs.listDashboard) 1 else resources.getInteger(R.integer.course_card_columns)
        val groupColumns =
            if (StudentPrefs.listDashboard) 1 else resources.getInteger(R.integer.group_card_columns)

        val layoutManager = GridLayoutManager(this, courseColumns * groupColumns)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (binding.recyclerView.adapter!!.getItemViewType(position)) {
                    DownloadsCoursesAdapter.COURSE -> groupColumns
                    else -> courseColumns * groupColumns
                }
            }
        }

        binding.recyclerView.removeAllItemDecorations()
        binding.recyclerView.addItemDecoration(
            VerticalGridSpacingDecoration(this, layoutManager)
        )

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.itemAnimator = DefaultItemAnimator()
        binding.recyclerView.adapter = DownloadsCoursesAdapter(
            object : DownloadsCoursesAdapter.OnDownloadsCoursesListener {
                override fun onCourseClick(courseItem: DownloadsCourseItem) {
                    startActivity(
                        DownloadsCourseActivity.newIntent(
                            this@DownloadsCoursesActivity, courseItem.courseId
                        )
                    )
                }
            })

        val padding = resources.getDimensionPixelSize(R.dimen.courseListPadding)
        binding.recyclerView.setPaddingRelative(padding, padding, padding, padding)
        binding.recyclerView.clipToPadding = false

        binding.removeAllTextView.setOnClickListener {
            if (mAlertDialog == null) {
                mAlertDialog = AlertDialog.Builder(this)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        removeAllContents()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .setMessage(R.string.download_modules_remove_all_dialog_title)
                    .show()
            }

            mAlertDialog?.show()
        }
    }

    private fun checkForEmptyState() {
        if (DownloadsRepository.getCourses().isEmpty()) {
            binding.emptyView.setEmptyViewImage(getDrawableCompat(R.drawable.ic_panda_nomodules))
            binding.emptyView.setTitleText(R.string.download_no_courses)
            binding.emptyView.setMessageText(R.string.download_no_courses_subtext)
            binding.emptyView.setListEmpty()
            binding.emptyView.setVisible()
            binding.recyclerView.setGone()
            binding.removeAllTextView.setGone()
        }
    }

    private fun removeAllContents() {
        binding.progressBar.setVisible()

        launch {
            val keys = mutableListOf<String>()
            DownloadsRepository.getCourses().forEach { courseItem ->
                DownloadsRepository.getModuleItems(courseItem.courseId)?.forEach {
                    keys.add(it.key)
                }
                DownloadsRepository.getPageItems(courseItem.courseId)?.forEach {
                    keys.add(it.key)
                }
                DownloadsRepository.getFileItems(courseItem.courseId)?.forEach {
                    keys.add(it.key)
                }
            }
            keys.addAll(Offline.getOfflineRepository().getAllModules().map { it.key })

            Offline.getOfflineManager().remove(keys)

            launch(Dispatchers.Main) {
                checkForEmptyState()
                binding.progressBar.setGone()
            }
        }
    }

    companion object {

        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, DownloadsCoursesActivity::class.java)
        }
    }
}