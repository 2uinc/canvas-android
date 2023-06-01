package com.instructure.student.offline.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instructure.student.R
import com.instructure.student.databinding.ItemDownloadsCourseBinding
import com.instructure.student.offline.item.DownloadsCourseItem

class DownloadsCoursesAdapter(
    private val mCourses: List<DownloadsCourseItem>,
    private val mOnDownloadsCoursesListener: OnDownloadsCoursesListener
) :
    RecyclerView.Adapter<DownloadsCoursesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDownloadsCourseBinding.bind(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_downloads_course, parent, false)
            )
        )
    }

    override fun getItemCount(): Int = mCourses.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mCourses[position]

        holder.binding.titleTextView.text = item.title
    }

    inner class ViewHolder(val binding: ItemDownloadsCourseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.mainLayout.setOnClickListener {
                val item = mCourses[layoutPosition]

                mOnDownloadsCoursesListener.onCourseClick(item)
            }
        }
    }

    interface OnDownloadsCoursesListener {

        fun onCourseClick(courseItem: DownloadsCourseItem)
    }
}