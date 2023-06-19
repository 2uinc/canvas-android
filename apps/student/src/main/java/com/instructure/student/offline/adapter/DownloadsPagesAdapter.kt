package com.instructure.student.offline.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instructure.student.R
import com.instructure.student.databinding.ItemDownloadsCourseBinding
import com.instructure.student.offline.item.DownloadsPageItem

class DownloadsPagesAdapter(
    private val mPages: List<DownloadsPageItem>,
    private val mOnDownloadsPagesListener: OnDownloadsPagesListener
) :
    RecyclerView.Adapter<DownloadsPagesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDownloadsCourseBinding.bind(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_downloads_course, parent, false)
            )
        )
    }

    override fun getItemCount(): Int = mPages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mPages[position]

        holder.binding.titleTextView.text = item.pageName
    }

    inner class ViewHolder(val binding: ItemDownloadsCourseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.mainLayout.setOnClickListener {
                val item = mPages[layoutPosition]

                mOnDownloadsPagesListener.onPageClick(item)
            }
        }
    }

    interface OnDownloadsPagesListener {

        fun onPageClick(pageItem: DownloadsPageItem)
    }
}