package com.instructure.student.offline.activity

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.instructure.pandautils.utils.ColorKeeper
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.setupAsBackButton
import com.instructure.student.R
import com.instructure.student.databinding.ActivityDownloadsModulesBinding
import com.instructure.student.offline.adapter.DownloadsModulesAdapter
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DownloadsModulesActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityDownloadsModulesBinding

    private var mCourseId = -1L
    private var mCourseName = ""

    private var mDownloadsModuleAdapter: DownloadsModulesAdapter? = null
    private var mOfflineListener: OfflineManager.OfflineListener? = null

    private var mAlertDialog: AlertDialog? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsModulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mCourseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        mCourseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: ""

        initView()

        mOfflineListener = object : OfflineManager.OfflineListener() {

            override fun onItemRemoved(key: String) {
                if (OfflineUtils.getModuleType(key) == OfflineConst.MODULE_TYPE_MODULES &&
                    OfflineUtils.getCourseId(key) == mCourseId
                ) {
                    mDownloadsModuleAdapter?.getItems()?.indexOfFirst { it.key == key }
                        ?.let { index ->
                            if (index >= 0) {
                                mDownloadsModuleAdapter?.getItems()?.removeAt(index)
                                mDownloadsModuleAdapter?.notifyItemRemoved(index)
                                if (index < (mDownloadsModuleAdapter?.itemCount ?: 0)) {
                                    mDownloadsModuleAdapter?.notifyItemChanged(index)
                                }
                            }
                        }
                    if (mDownloadsModuleAdapter?.itemCount == 0) finish()
                }
            }

            override fun onItemsRemoved(keys: List<String>) {
                keys.forEach { onItemRemoved(it) }
            }
        }

        mOfflineListener?.let { Offline.getOfflineManager().addListener(it) }
    }

    override fun onDestroy() {
        mOfflineListener?.let { Offline.getOfflineManager().removeListener(it) }
        mAlertDialog?.dismiss()
        super.onDestroy()
    }

    private fun initView() {
        binding.toolbar.setupAsBackButton { finish() }
        binding.toolbar.title = mCourseName

        ColorKeeper.cachedThemedColors["course_$mCourseId"]?.let { themedColor ->
            ViewStyler.themeToolbarColored(
                this, binding.toolbar, themedColor.backgroundColor(), Color.WHITE
            )

            binding.recyclerView.layoutManager = LinearLayoutManager(this)

            DownloadsRepository.getModuleItems(mCourseId)?.let {
                mDownloadsModuleAdapter = DownloadsModulesAdapter(this,
                    themedColor.textAndIconColor(), it.toMutableList(),
                    object : DownloadsModulesAdapter.OnDownloadsModuleListener {
                        override fun onModuleItemClick(item: DownloadsModuleItem) {
                            if (Offline.getOfflineRepository()
                                    .getOfflineModule(item.key) == null
                            ) {
                                Snackbar.make(
                                    binding.recyclerView, R.string.download_content_not_downloaded,
                                    Snackbar.LENGTH_LONG
                                ).show()
                                return
                            }

                            startActivity(
                                DownloadsContentActivity.newIntent(
                                    this@DownloadsModulesActivity, item.key
                                )
                            )
                        }
                    })
                binding.recyclerView.adapter = mDownloadsModuleAdapter
            }
        }

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

    private fun removeAllContents() {
        launch {
            val keys = mutableListOf<String>()
            DownloadsRepository.getModuleItems(mCourseId)?.forEach {
                keys.add(it.key)
            }

            Offline.getOfflineManager().remove(keys)

            launch(Dispatchers.Main) {
                finish()
            }
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