package com.instructure.student.offline.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.instructure.pandautils.utils.DP
import com.instructure.student.R
import com.instructure.student.databinding.FragmentFileDetailsBinding
import com.instructure.student.offline.item.FileOfflineItem
import com.instructure.student.offline.util.OfflineUtils
import com.twou.offline.Offline
import kotlinx.android.synthetic.main.fragment_file_details.*
import java.io.File

class DownloadsFileFragment : Fragment() {

    private lateinit var binding: FragmentFileDetailsBinding

    private var mKey = ""
    private var mFileOfflineItem: FileOfflineItem? = null

    private val mOfflineRepository = Offline.getOfflineRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        mKey = arguments?.getString(ARG_KEY) ?: ""
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFileDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.downloadButton.visibility = View.GONE
        binding.toolbar.visibility = View.GONE

        mOfflineRepository.getOfflineModule(mKey)?.let { offlineModule ->
            val offlineItem = OfflineUtils.convertOfflineModuleToFile(offlineModule)
            mFileOfflineItem = offlineItem

            binding.fileName.text = offlineItem.displayName
            binding.fileType.text = offlineItem.contentType

            if (offlineItem.thumbnailPath.isNotEmpty()) {
                fileIcon.layoutParams.apply {
                    height = requireActivity().DP(230).toInt()
                    width = height
                }

                Glide.with(requireActivity())
                    .load(offlineItem.thumbnailPath)
                    .apply(RequestOptions().fitCenter())
                    .into(binding.fileIcon)
            }

            binding.openButton.setOnClickListener {
                val file = getFile(offlineItem.filePath) ?: return@setOnClickListener
                val uri = getFileUri(file)
                openFile(uri)
            }
        }
    }

    private fun getFile(uri: String?): File? {
        if (uri == null) return null
        return if (uri.contains("file:/")) {
            File(uri.substring(if (uri.contains("file:///")) 7 else 5))
        } else {
            File(uri)
        }
    }

    private fun getFileUri(file: File): Uri {
        val context = requireActivity()
        return FileProvider.getUriForFile(
            context, context.applicationContext.packageName + ".provider", file
        )
    }

    private fun openFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mFileOfflineItem?.contentType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        try {
            startActivity(intent)

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.download_no_intent_msg, Toast.LENGTH_LONG)
                .show()
        }
    }

    companion object {

        const val TAG = "DownloadsFileFragment"

        private const val ARG_KEY = "ARG_KEY"

        @JvmStatic
        fun newArgs(key: String): Bundle {
            return Bundle().apply { putString(ARG_KEY, key) }
        }
    }
}