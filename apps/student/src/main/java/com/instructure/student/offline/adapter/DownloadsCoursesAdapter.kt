package com.instructure.student.offline.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.instructure.pandautils.utils.ColorKeeper
import com.instructure.pandautils.utils.setCourseImage
import com.instructure.pandautils.utils.setGone
import com.instructure.student.R
import com.instructure.student.databinding.ViewholderCourseCardBinding
import com.instructure.student.databinding.ViewholderCourseHeaderBinding
import com.instructure.student.offline.item.DownloadsCourseItem
import com.instructure.student.util.StudentPrefs

class DownloadsCoursesAdapter(
    private val mCourses: List<DownloadsCourseItem>,
    private val mOnDownloadsCoursesListener: OnDownloadsCoursesListener
) :
    RecyclerView.Adapter<DownloadsCoursesAdapter.BaseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == COURSE_HEADER) {
            CourseHeaderViewHolder(
                ViewholderCourseHeaderBinding.bind(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.viewholder_course_header, parent, false)
                )
            )

        } else {
            CourseViewHolder(
                ViewholderCourseCardBinding.bind(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.viewholder_course_card, parent, false)
                )
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) COURSE_HEADER else COURSE
    }

    override fun getItemCount(): Int = mCourses.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is CourseViewHolder) {
            val item = mCourses[position - 1]

            holder.binding.apply {
                gradeLayout.setGone()
                courseColorIndicator.setGone()
                overflow.setGone()

                titleTextView.text = item.title
                courseCode.text = item.courseCode

                ColorKeeper.cachedThemedColors["course_${item.courseId}"]?.let { themedColor ->
                    titleTextView.setTextColor(themedColor.textAndIconColor())

                    courseImageView.setCourseImage(
                        imageUrl = item.logoPath,
                        courseColor = themedColor.backgroundColor(),
                        applyColor = !StudentPrefs.hideCourseColorOverlay
                    )
                }
            }
        }
    }

    open class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class CourseHeaderViewHolder(val binding: ViewholderCourseHeaderBinding) :
        BaseViewHolder(binding.root) {

        init {
            binding.editDashboardTextView.visibility = View.INVISIBLE
        }
    }

    inner class CourseViewHolder(val binding: ViewholderCourseCardBinding) :
        BaseViewHolder(binding.root) {

        init {
            binding.cardView.setOnClickListener {
                val item = mCourses[layoutPosition - 1]

                mOnDownloadsCoursesListener.onCourseClick(item)
            }
        }
    }

    interface OnDownloadsCoursesListener {

        fun onCourseClick(courseItem: DownloadsCourseItem)
    }

    companion object {
        const val COURSE_HEADER = 0
        const val COURSE = 1
    }
}