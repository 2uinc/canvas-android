package com.instructure.student.offline.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.instructure.pandautils.utils.ThemePrefs
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.setupAsBackButton
import com.instructure.student.R
import com.instructure.student.databinding.ActivityDownloadsCoursesBinding
import com.instructure.student.offline.adapter.DownloadsCoursesAdapter
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.offline.util.DownloadsRepository

class DownloadsCoursesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsCoursesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initView()
    }

    private fun initView() {
        binding.toolbar.setupAsBackButton { finish() }
        binding.toolbar.setTitle(R.string.downloads_courses_title)

        ViewStyler.themeToolbarColored(
            this, binding.toolbar, ThemePrefs.primaryColor, ThemePrefs.primaryTextColor
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = DownloadsCoursesAdapter(
            DownloadsRepository.getCourses(),
            object : DownloadsCoursesAdapter.OnDownloadsCoursesListener {
                override fun onCourseClick(courseItem: DownloadsCourseItem) {
                    startActivity(
                        DownloadsModulesActivity.newIntent(
                            this@DownloadsCoursesActivity, courseItem.courseId, courseItem.title
                        )
                    )
                }
            })
    }

    companion object {

        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, DownloadsCoursesActivity::class.java)
        }
    }
}