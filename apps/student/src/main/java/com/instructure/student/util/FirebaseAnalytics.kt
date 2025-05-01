package com.instructure.student.util

import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.twou.offline.util.OfflineLogs

object FirebaseAnalytics {
    private val analytics = Firebase.analytics

    fun logEvent(analyticsEvent: AnalyticsEvent) {
        OfflineLogs.d(
            "OfflineEvent",
            analyticsEvent.eventName
        )
        analytics.logEvent(analyticsEvent.eventName, setupBundle())
    }

    fun identifyUser(userId: Long) {
        analytics.setUserId(userId.toString())
    }

    private fun setupBundle(): Bundle? = null
}

sealed class AnalyticsEvent(val eventName: String) {
    object OfflineModeStarted : AnalyticsEvent(OFFLINE_MODE_STARTED)
    object OfflineModePaused : AnalyticsEvent(OFFLINE_MODE_PAUSED)
    object OfflineModePausedAll : AnalyticsEvent(OFFLINE_MODE_PAUSED_ALL)
    object OfflineModeResumed : AnalyticsEvent(OFFLINE_MODE_RESUMED)
    object OfflineModeResumedAll : AnalyticsEvent(OFFLINE_MODE_RESUMED_ALL)
    object OfflineModeCompleted : AnalyticsEvent(OFFLINE_MODE_COMPLETED)
    object OfflineModeError : AnalyticsEvent(OFFLINE_MODE_ERROR)
    object OfflineModeDeleted : AnalyticsEvent(OFFLINE_MODE_DELETED)
    object OfflineModeDeletedAll : AnalyticsEvent(OFFLINE_MODE_DELETED_ALL)

    private companion object {
        const val OFFLINE_MODE_STARTED = "offline_mode_started"
        const val OFFLINE_MODE_PAUSED = "offline_mode_paused"
        const val OFFLINE_MODE_PAUSED_ALL = "offline_mode_pausedAll"
        const val OFFLINE_MODE_RESUMED = "offline_mode_resumed"
        const val OFFLINE_MODE_RESUMED_ALL = "offline_mode_resumedAll"
        const val OFFLINE_MODE_COMPLETED = "offline_mode_completed"
        const val OFFLINE_MODE_ERROR = "offline_mode_error"
        const val OFFLINE_MODE_DELETED = "offline_mode_deleted"
        const val OFFLINE_MODE_DELETED_ALL = "offline_mode_deletedAll"
    }
}
