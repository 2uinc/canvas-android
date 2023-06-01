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
import com.instructure.student.offline.fragment.DownloadsPageFragment
import com.instructure.student.offline.util.DownloadsRepository
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

        val parsedKey = OfflineUtils.parseKey(mKey)
        val courseId = OfflineUtils.getCourseId(parsedKey)
        val moduleItemId = OfflineUtils.getModuleItemId(parsedKey)

        ColorKeeper.cachedThemedColors["course_$courseId"]?.let { themedColor ->
            ViewStyler.themeToolbarColored(
                this, binding.toolbar, themedColor.backgroundColor(), Color.WHITE
            )
        }

        DownloadsRepository.getModuleItems(courseId)?.let { moduleItems ->
            moduleItems.find { it.moduleItemId == moduleItemId }?.let { moduleItem ->
                binding.toolbar.title = moduleItem.moduleName

                supportFragmentManager.commit {
                    when (moduleItem.type) {
                        "Page" -> {
                            val fragment = DownloadsPageFragment()
                            fragment.arguments = DownloadsPageFragment.newArgs(mKey)
                            replace(R.id.containerLayout, fragment, DownloadsPageFragment.TAG)
                        }

                        "File" -> {
                            val fragment = DownloadsFileFragment()
                            fragment.arguments = DownloadsFileFragment.newArgs(mKey)
                            replace(R.id.containerLayout, fragment, DownloadsFileFragment.TAG)
                        }
                    }
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