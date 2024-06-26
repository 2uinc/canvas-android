/*
 * Copyright (C) 2017 - present Instructure, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.instructure.student.adapter

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.instructure.canvasapi2.managers.*
import com.instructure.canvasapi2.models.*
import com.instructure.canvasapi2.utils.isValidTerm
import com.instructure.canvasapi2.utils.weave.*
import com.instructure.pandarecycler.util.GroupSortedList
import com.instructure.pandautils.features.dashboard.DashboardCourseItem
import com.instructure.pandautils.utils.ColorApiHelper
import com.instructure.student.features.dashboard.DashboardRepository
import com.instructure.student.holders.*
import com.instructure.student.interfaces.CourseAdapterToFragmentCallback
import org.threeten.bp.OffsetDateTime
import java.util.*

class DashboardRecyclerAdapter(
        context: Activity,
        private val mAdapterToFragmentCallback: CourseAdapterToFragmentCallback,
        private val repository: DashboardRepository
) : ExpandableRecyclerAdapter<DashboardRecyclerAdapter.ItemType, Any, RecyclerView.ViewHolder>(
        context,
        ItemType::class.java,
        Any::class.java
) {

    enum class ItemType {
        COURSE_HEADER,
        COURSE,
        GROUP_HEADER,
        GROUP
    }

    private var mApiCalls: WeaveJob? = null
    private var mCourseMap = mapOf<Long, Course>()

    private var isOfflineEnabled = false

    init {
        isExpandedByDefault = true
        loadData()
    }

    override fun createViewHolder(v: View, viewType: Int) = when (ItemType.values()[viewType]) {
        ItemType.COURSE_HEADER -> CourseHeaderViewHolder(v)
        ItemType.COURSE -> CourseViewHolder(v)
        ItemType.GROUP_HEADER -> GroupHeaderViewHolder(v)
        ItemType.GROUP -> GroupViewHolder(v)
    }

    override fun onBindChildHolder(holder: RecyclerView.ViewHolder, header: ItemType, item: Any) {
        when {
            holder is CourseViewHolder && item is DashboardCourseItem -> holder.bind(item, isOfflineEnabled, mAdapterToFragmentCallback)
            holder is GroupViewHolder && item is Group -> holder.bind(item, mCourseMap, mAdapterToFragmentCallback)
        }
    }

    override fun onBindHeaderHolder(holder: RecyclerView.ViewHolder, header: ItemType, isExpanded: Boolean) {
        (holder as? CourseHeaderViewHolder)?.bind(mAdapterToFragmentCallback)
    }

    override fun createItemCallback(): GroupSortedList.ItemComparatorCallback<ItemType, Any> {
        return object : GroupSortedList.ItemComparatorCallback<ItemType, Any> {
            override fun compare(group: ItemType, o1: Any, o2: Any) = when {
                o1 is DashboardCourseItem && o2 is DashboardCourseItem -> -1 // Don't sort courses, the api returns in the users order
                o1 is Group && o2 is Group -> o1.compareTo(o2)
                else -> -1
            }

            override fun areContentsTheSame(oldItem: Any, newItem: Any) = false

            override fun areItemsTheSame(item1: Any, item2: Any) = when {
                item1 is DashboardCourseItem && item2 is DashboardCourseItem -> item1.course.contextId.hashCode() == item2.course.contextId.hashCode()
                item1 is Group && item2 is Group -> item1.contextId.hashCode() == item2.contextId.hashCode()
                else -> false
            }

            override fun getUniqueItemId(item: Any) = when (item) {
                is DashboardCourseItem -> item.course.contextId.hashCode().toLong()
                is Group -> item.contextId.hashCode().toLong()
                else -> -1L
            }

            override fun getChildType(group: ItemType, item: Any) = when (item) {
                is DashboardCourseItem -> ItemType.COURSE.ordinal
                is Group -> ItemType.GROUP.ordinal
                else -> -1
            }
        }
    }

    override fun createGroupCallback(): GroupSortedList.GroupComparatorCallback<ItemType> {
        return object : GroupSortedList.GroupComparatorCallback<ItemType> {
            override fun compare(o1: ItemType, o2: ItemType) = o1.ordinal.compareTo(o2.ordinal)
            override fun areContentsTheSame(oldGroup: ItemType, newGroup: ItemType) = oldGroup == newGroup
            override fun areItemsTheSame(group1: ItemType, group2: ItemType) = group1 == group2
            override fun getUniqueGroupId(group: ItemType) = group.ordinal.toLong()
            override fun getGroupType(group: ItemType) = group.ordinal
        }
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    override fun loadData() {
        mApiCalls?.cancel()
        mApiCalls = tryWeave {
            if (isRefresh && repository.isOnline()) {
                ColorApiHelper.awaitSync()
            }

            isOfflineEnabled = repository.isOfflineEnabled()

            val courses = repository.getCourses(isRefresh)
            val groups = repository.getGroups(isRefresh)
            val dashboardCards = repository.getDashboardCourses(isRefresh)
            val syncedCourseIds = repository.getSyncedCourseIds()

            resetData()

            mCourseMap = courses.associateBy { it.id }

            // Map not null is needed because the dashboard api can return unpublished courses
            val visibleCourses = dashboardCards.map { createCourseFromDashboardCard(it, mCourseMap) }

            // Filter groups
            val allActiveGroups = groups.filter { group -> group.isActive(mCourseMap[group.courseId])}

            val isAnyGroupFavorited = allActiveGroups.any { it.isFavorite }
            val visibleGroups = if (isAnyGroupFavorited) allActiveGroups.filter { it.isFavorite } else allActiveGroups

            // Add courses
            val isOnline = repository.isOnline()
            val courseItems = visibleCourses.map {
                DashboardCourseItem(it, syncedCourseIds.contains(it.id), isOnline || mCourseMap.containsKey(it.id))
            }
            addOrUpdateAllItems(ItemType.COURSE_HEADER, courseItems)

            // Add groups
            addOrUpdateAllItems(ItemType.GROUP_HEADER, visibleGroups)

            notifyDataSetChanged()
            isAllPagesLoaded = true
            if (itemCount == 0) adapterToRecyclerViewCallback?.setIsEmpty(true)
            mAdapterToFragmentCallback.onRefreshFinished()
        } catch {
            adapterToRecyclerViewCallback?.setDisplayNoConnection(true)
            mAdapterToFragmentCallback.onRefreshFinished()
        }
    }

    private fun createCourseFromDashboardCard(dashboardCard: DashboardCard, courseMap: Map<Long, Course>): Course {
        val course = courseMap[dashboardCard.id]
        return if (course != null) {
            course
        } else {
            Course(id = dashboardCard.id, name = dashboardCard.shortName ?: "", originalName = dashboardCard.originalName, courseCode = dashboardCard.courseCode)
        }
    }

    private fun hasValidCourseForEnrollment(enrollment: Enrollment): Boolean {
        return mCourseMap[enrollment.courseId]?.let { course ->
            course.isValidTerm() && !course.accessRestrictedByDate && isEnrollmentBeforeEndDateOrNotRestricted(course)
        } ?: false
    }

    private fun isEnrollmentBeforeEndDateOrNotRestricted(course: Course): Boolean {
        val isBeforeEndDate = course.endAt?.let {
            val now = OffsetDateTime.now()
            val endDate = OffsetDateTime.parse(it).withOffsetSameInstant(OffsetDateTime.now().offset)
            now.isBefore(endDate)
        } ?: true // Case when the course has no end date

        return !course.restrictEnrollmentsToCourseDate || isBeforeEndDate
    }

    override fun itemLayoutResId(viewType: Int) = when (ItemType.values()[viewType]) {
        ItemType.COURSE_HEADER -> CourseHeaderViewHolder.HOLDER_RES_ID
        ItemType.COURSE -> CourseViewHolder.HOLDER_RES_ID
        ItemType.GROUP_HEADER -> GroupHeaderViewHolder.HOLDER_RES_ID
        ItemType.GROUP -> GroupViewHolder.HOLDER_RES_ID
    }

    override fun contextReady() = Unit

    override fun setupCallbacks() = Unit

    override fun cancel() {
        mApiCalls?.cancel()
    }

    override fun refresh() {
        mApiCalls?.cancel()
        super.refresh()
    }
}
