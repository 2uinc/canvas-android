package com.instructure.student.offline.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instructure.pandautils.utils.setColoredResource
import com.instructure.student.R
import com.instructure.student.databinding.ItemDownloadsModuleBinding
import com.instructure.student.offline.item.DownloadsFileItem
import com.twou.offline.item.KeyOfflineItem

class DownloadsFilesAdapter(
    private val mTextAndIconColor: Int, private val mFiles: MutableList<DownloadsFileItem>,
    private val mOnDownloadsFilesListener: OnDownloadsFilesListener
) :
    RecyclerView.Adapter<DownloadsFilesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDownloadsModuleBinding.bind(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_downloads_module, parent, false)
            )
        )
    }

    override fun getItemCount(): Int = mFiles.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mFiles[position]

        holder.binding.apply {
            headerLayout.visibility = View.GONE
            moduleItemTextView.text = item.fileName

            downloadItemView.setWithRemoveAbility()
            downloadItemView.setKeyItem(KeyOfflineItem(item.key, item.fileName))
            downloadItemView.setViewColor(mTextAndIconColor)

            val contentType = item.contentType
            val iconRes = when {
                contentType.contains("pdf") -> R.drawable.ic_pdf
                contentType.contains("presentation") -> R.drawable.ic_ppt
                contentType.contains("spreadsheet") -> R.drawable.ic_spreadsheet
                contentType.contains("wordprocessing") -> R.drawable.ic_word_doc
                contentType.contains("zip") -> R.drawable.ic_zip
                contentType.contains("image") -> R.drawable.ic_image
                else -> R.drawable.ic_document
            }

            iconImageView.setColoredResource(iconRes, mTextAndIconColor)
        }
    }

    fun getItems() = mFiles

    inner class ViewHolder(val binding: ItemDownloadsModuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.mainLayout.setOnClickListener {
                val item = mFiles[layoutPosition]

                mOnDownloadsFilesListener.onFileClick(item)
            }
        }
    }

    interface OnDownloadsFilesListener {

        fun onFileClick(fileItem: DownloadsFileItem)
    }
}