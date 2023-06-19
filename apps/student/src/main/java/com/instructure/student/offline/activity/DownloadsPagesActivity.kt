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
import com.instructure.student.databinding.ActivityDownloadsPagesBinding
import com.instructure.student.offline.adapter.DownloadsPagesAdapter
import com.instructure.student.offline.item.DownloadsPageItem
import com.instructure.student.offline.util.DownloadsRepository

class DownloadsPagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsPagesBinding

    private var mCourseId = -1L
    private var mCourseName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsPagesBinding.inflate(layoutInflater)
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

        DownloadsRepository.getPageItems(mCourseId)?.let {
            binding.recyclerView.adapter = DownloadsPagesAdapter(
                it, object : DownloadsPagesAdapter.OnDownloadsPagesListener {
                    override fun onPageClick(pageItem: DownloadsPageItem) {
                        startActivity(
                            DownloadsContentActivity.newIntent(
                                this@DownloadsPagesActivity, pageItem.key
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
            return Intent(context, DownloadsPagesActivity::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
                .putExtra(EXTRA_COURSE_NAME, courseName)
        }
    }
}