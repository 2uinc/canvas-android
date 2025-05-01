package com.instructure.student.offline.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instructure.pandautils.utils.ColorKeeper
import com.instructure.student.R
import com.instructure.student.databinding.ItemDownloadsModuleBinding
import com.instructure.student.offline.item.DownloadsPageItem
import com.twou.offline.item.KeyOfflineItem

class DownloadsPagesAdapter(
    private val mContext: Context, private val mTextAndIconColor: Int,
    private val mPages: MutableList<DownloadsPageItem>,
    private val mOnDownloadsPagesListener: OnDownloadsPagesListener
) :
    RecyclerView.Adapter<DownloadsPagesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDownloadsModuleBinding.bind(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_downloads_module, parent, false)
            )
        )
    }

    override fun getItemCount(): Int = mPages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mPages[position]

        holder.binding.apply {
            headerLayout.visibility = View.GONE
            moduleItemTextView.text = item.pageName

            downloadItemView.setWithRemoveAbility()
            downloadItemView.setKeyItem(KeyOfflineItem(item.key, item.pageName))
            downloadItemView.setViewColor(mTextAndIconColor)

            val drawable =
                ColorKeeper.getColoredDrawable(mContext, R.drawable.ic_pages, mTextAndIconColor)
            iconImageView.setImageDrawable(drawable)
        }
    }

    fun getItems() = mPages

    inner class ViewHolder(val binding: ItemDownloadsModuleBinding) :
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