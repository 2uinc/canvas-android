package com.instructure.student.offline.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.instructure.pandautils.utils.ColorKeeper
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.setupAsBackButton
import com.instructure.student.R
import com.instructure.student.databinding.ActivityDownloadsContentBinding
import com.instructure.student.offline.fragment.DownloadsFileFragment
import com.instructure.student.offline.fragment.DownloadsHtmlFragment
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils

class DownloadsContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsContentBinding

    private var mKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mKey = intent.getStringExtra(EXTRA_KEY) ?: ""

        binding = ActivityDownloadsContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initView()
    }

    private fun initView() {
        binding.toolbar.setupAsBackButton { finish() }

        val courseId = OfflineUtils.getCourseId(mKey)

        ColorKeeper.cachedThemedColors["course_$courseId"]?.let { themedColor ->
            ViewStyler.themeToolbarColored(
                this, binding.toolbar, themedColor.backgroundColor(), Color.WHITE
            )
        }

        when (OfflineUtils.getModuleType(mKey)) {
            OfflineConst.MODULE_TYPE_MODULES -> {
                DownloadsRepository.getModuleItems(courseId)?.let { moduleItems ->
                    moduleItems.find { it.key == mKey }?.let { moduleItem ->
                        binding.toolbar.title = moduleItem.moduleName

                        showContent(moduleItem.type)
                    }
                }
            }

            OfflineConst.MODULE_TYPE_PAGES -> {
                DownloadsRepository.getPageItems(courseId)?.let { pageItems ->
                    pageItems.find { it.key == mKey }?.let { pageItem ->
                        binding.toolbar.title = pageItem.pageName

                        showContent(OfflineConst.TYPE_PAGE)
                    }
                }
            }
        }
    }

    private fun showContent(type: Int) {
        supportFragmentManager.commit {
            when (type) {
                OfflineConst.TYPE_PAGE, OfflineConst.TYPE_LTI -> {
                    val fragment = DownloadsHtmlFragment()
                    fragment.arguments = DownloadsHtmlFragment.newArgs(mKey)
                    replace(R.id.containerLayout, fragment, DownloadsHtmlFragment.TAG)
                }

                OfflineConst.TYPE_FILE -> {
                    val fragment = DownloadsFileFragment()
                    fragment.arguments = DownloadsFileFragment.newArgs(mKey)
                    replace(R.id.containerLayout, fragment, DownloadsFileFragment.TAG)
                }
            }
        }
    }

    companion object {

        private const val EXTRA_KEY = "EXTRA_KEY"

        @JvmStatic
        fun newIntent(context: Context, key: String): Intent {
            return Intent(context, DownloadsContentActivity::class.java)
                .putExtra(EXTRA_KEY, key)
        }
    }
}