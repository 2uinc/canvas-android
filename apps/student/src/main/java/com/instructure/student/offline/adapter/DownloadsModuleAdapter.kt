package com.instructure.student.offline.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instructure.student.R
import com.instructure.student.databinding.ItemDownloadsModuleBinding
import com.instructure.student.offline.item.DownloadsModuleItem
import com.instructure.student.offline.util.OfflineConst
import com.twou.offline.item.KeyOfflineItem

class DownloadsModuleAdapter(
    private val mModules: List<DownloadsModuleItem>,
    private val mOnDownloadsModuleListener: OnDownloadsModuleListener
) :
    RecyclerView.Adapter<DownloadsModuleAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDownloadsModuleBinding.bind(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_downloads_module, parent, false)
            )
        )
    }

    override fun getItemCount(): Int = mModules.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mModules[position]

        holder.binding.apply {
            if (position == 0 || item.moduleId != mModules[position - 1].moduleId) {
                moduleTextView.text = item.moduleName
                headerLayout.visibility = View.VISIBLE

            } else {
                headerLayout.visibility = View.GONE
            }

            moduleItemTextView.text = item.moduleItemName

            downloadItemView.setWithRemoveAbility()
            downloadItemView.setKeyItem(KeyOfflineItem(item.key, item.moduleItemName))

            val resId = when (item.type) {
                OfflineConst.TYPE_PAGE -> R.drawable.ic_pages
                OfflineConst.TYPE_FILE -> R.drawable.ic_download
                else -> 0
            }

            iconImageView.setImageResource(resId)
        }
    }

    inner class ViewHolder(val binding: ItemDownloadsModuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.mainLayout.setOnClickListener {
                val item = mModules[layoutPosition]

                mOnDownloadsModuleListener.onModuleItemClick(item)
            }
        }
    }

    interface OnDownloadsModuleListener {

        fun onModuleItemClick(item: DownloadsModuleItem)
    }
}