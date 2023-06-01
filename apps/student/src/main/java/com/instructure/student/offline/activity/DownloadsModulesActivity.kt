package com.instructure.student.offline.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.instructure.pandautils.utils.ColorKeeper
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.setupAsBackButton
import com.instructure.student.databinding.ActivityDownloadsModulesBinding
import com.instructure.student.offline.adapter.DownloadsModuleAdapter
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.offline.util.OfflineUtils

class DownloadsModulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsModulesBinding

    private var mCourseId = -1L
    private var mCourseName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsModulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mCourseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        mCourseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: ""

        initView()
    }

    private fun initView() {
        binding.toolbar.setupAsBackButton { finish() }
        binding.toolbar.title = mCourseName

        ColorKeeper.cachedThemedColors["course_$mCourseId"]?.let { themedColor ->
            ViewStyler.themeToolbarColored(
                this, binding.toolbar, themedColor.backgroundColor(), Color.WHITE
            )
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        DownloadsRepository.getModuleItems(mCourseId)?.let {
            binding.recyclerView.adapter = DownloadsModuleAdapter(it,
                object : DownloadsModuleAdapter.OnDownloadsModuleListener {
                    override fun onModuleItemClick(item: DownloadsModuleItem) {
                        startActivity(
                            DownloadsContentActivity.newIntent(
                                this@DownloadsModulesActivity,
                                OfflineUtils.getKey(item.courseId, item.moduleId, item.moduleItemId)
                            )
                        )
                    }
                })
        }
    }

    companion object {

        private const val EXTRA_COURSE_ID = "EXTRA_COURSE_ID"
        private const val EXTRA_COURSE_NAME = "EXTRA_COURSE_NAME"

        @JvmStatic
        fun newIntent(context: Context, courseId: Long, courseName: String): Intent {
            return Intent(context, DownloadsModulesActivity::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
                .putExtra(EXTRA_COURSE_NAME, courseName)
        }
    }
}