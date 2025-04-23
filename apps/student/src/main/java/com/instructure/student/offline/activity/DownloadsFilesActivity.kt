package com.instructure.student.offline.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.color
import com.instructure.pandautils.utils.setupAsBackButton
import com.instructure.student.R
import com.instructure.student.databinding.ActivityDownloadsContentListBinding
import com.instructure.student.offline.adapter.DownloadsFilesAdapter
import com.instructure.student.offline.item.DownloadsFileItem
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.offline.util.OfflineConst
import com.instructure.student.offline.util.OfflineUtils
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DownloadsFilesActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityDownloadsContentListBinding

    private var mCourseId = -1L

    private var mDownloadsFilesAdapter: DownloadsFilesAdapter? = null
    private var mOfflineListener: OfflineManager.OfflineListener? = null

    private var mAlertDialog: AlertDialog? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDownloadsContentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mCourseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)

        initView()

        mOfflineListener = object : OfflineManager.OfflineListener() {

            @SuppressLint("NotifyDataSetChanged")
            override fun onItemRemoved(key: String) {
                if (OfflineUtils.getModuleType(key) == OfflineConst.MODULE_TYPE_FILES &&
                    OfflineUtils.getCourseId(key) == mCourseId
                ) {
                    mDownloadsFilesAdapter?.getItems()?.indexOfFirst { it.key == key }
                        ?.let { index ->
                            if (index >= 0) {
                                mDownloadsFilesAdapter?.getItems()?.removeAt(index)
                                mDownloadsFilesAdapter?.notifyItemRemoved(index)
                            }
                        }
                    if (mDownloadsFilesAdapter?.itemCount == 0) finish()
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
        binding.toolbar.setTitle(R.string.download_course_files)

        val themedColor = CanvasContext.fromContextCode("course_$mCourseId").color
        ViewStyler.themeToolbarColored(
            this, binding.toolbar, themedColor, Color.WHITE
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        DownloadsRepository.getFileItems(mCourseId)?.let {
            mDownloadsFilesAdapter = DownloadsFilesAdapter(
                themedColor, it.toMutableList(),
                object : DownloadsFilesAdapter.OnDownloadsFilesListener {
                    override fun onFileClick(fileItem: DownloadsFileItem) {
                        if (Offline.getOfflineRepository()
                                .getOfflineModule(fileItem.key) == null
                        ) {
                            Snackbar.make(
                                binding.recyclerView, R.string.download_content_not_downloaded,
                                Snackbar.LENGTH_LONG
                            ).show()
                            return
                        }

                        startActivity(
                            DownloadsContentActivity.newIntent(
                                this@DownloadsFilesActivity, fileItem.key
                            )
                        )
                    }
                })
            binding.recyclerView.adapter = mDownloadsFilesAdapter
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
        binding.progressBar.visibility = View.VISIBLE

        launch {
            val keys = mutableListOf<String>()
            DownloadsRepository.getFileItems(mCourseId)?.forEach {
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

        @JvmStatic
        fun newIntent(context: Context, courseId: Long): Intent {
            return Intent(context, DownloadsFilesActivity::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
        }
    }
}