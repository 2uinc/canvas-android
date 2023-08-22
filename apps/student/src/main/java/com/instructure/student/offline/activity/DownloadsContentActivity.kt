package com.instructure.student.offline.activity

import android.annotation.SuppressLint
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
import com.instructure.student.offline.fragment.DownloadsBaseFragment
import com.instructure.student.offline.fragment.DownloadsFileFragment
import com.instructure.student.offline.fragment.DownloadsHtmlFragment
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.item.KeyOfflineItem

class DownloadsContentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsContentBinding

    private var mKey = ""
    private var mCurrentFragment: DownloadsBaseFragment? = null

    private var mOfflineListener: OfflineManager.OfflineListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mKey = intent.getStringExtra(EXTRA_KEY) ?: ""

        binding = ActivityDownloadsContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initView()

        mOfflineListener = object : OfflineManager.OfflineListener() {

            @SuppressLint("NotifyDataSetChanged")
            override fun onItemRemoved(key: String) {
                if (key == mKey) finish()
            }

            override fun onItemsRemoved(keys: List<String>) {
                keys.forEach { onItemRemoved(it) }
            }
        }

        mOfflineListener?.let { Offline.getOfflineManager().addListener(it) }
    }

    override fun onDestroy() {
        mOfflineListener?.let { Offline.getOfflineManager().removeListener(it) }
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (mCurrentFragment?.onBackPressed() == false) {
            super.onBackPressed()
        }
    }

    private fun initView() {
        binding.toolbar.setupAsBackButton { onBackPressed() }
        binding.downloadItemView.setWithRemoveAbility()
        binding.downloadItemView.setViewColor(Color.WHITE)
        binding.downloadItemView.setKeyItem(KeyOfflineItem(mKey, ""))

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
                    mCurrentFragment = fragment
                }

                OfflineConst.TYPE_FILE -> {
                    val fragment = DownloadsFileFragment()
                    fragment.arguments = DownloadsFileFragment.newArgs(mKey)
                    replace(R.id.containerLayout, fragment, DownloadsFileFragment.TAG)
                    mCurrentFragment = fragment
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