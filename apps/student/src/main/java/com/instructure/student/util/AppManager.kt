/*
 * Copyright (C) 2016 - present Instructure, Inc.
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

package com.instructure.student.util

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerFactory
import com.bugfender.sdk.Bugfender
import com.instructure.canvasapi2.utils.MasqueradeHelper
import com.instructure.loginapi.login.tasks.LogoutTask
import com.instructure.pandautils.typeface.TypefaceBehavior
import com.instructure.student.BuildConfig
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.offline.util.OfflineDownloaderCreator
import com.instructure.student.offline.util.OfflineModeService
import com.instructure.student.offline.util.OfflineUtils
import com.instructure.student.tasks.StudentLogoutTask
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import com.twou.offline.data.IOfflineLoggerInterceptor
import com.twou.offline.item.KeyOfflineItem
import com.twou.offline.util.BaseOfflineUtils.Companion.isOnline
import com.twou.offline.util.OfflineLoggerType
import com.twou.offline.util.OfflineLogs
import dagger.hilt.android.HiltAndroidApp
import io.intercom.android.sdk.Intercom
import javax.inject.Inject

@HiltAndroidApp
class AppManager : BaseAppManager() {

    @Inject
    lateinit var typefaceBehavior: TypefaceBehavior

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        MasqueradeHelper.masqueradeLogoutTask = Runnable { StudentLogoutTask(LogoutTask.Type.LOGOUT, typefaceBehavior = typefaceBehavior).execute() }

        Offline.init(
            this, Offline.Builder().setHtmlErrorOverlay(OfflineUtils.getHtmlErrorOverlay())
                .setHtmlErrorScript("").setHtmlErrorCSS("")
                .setOfflineLoggerInterceptor(object : IOfflineLoggerInterceptor {
                    override fun onLogMessage(
                        keyItem: KeyOfflineItem?, type: OfflineLoggerType, message: String
                    ) {
                        OfflineLogs.w(
                            OFFLINE_KEY,
                            keyItem?.key + "; " + keyItem?.title + "; " + type + "; " + message
                        )
                        if (type != OfflineLoggerType.DEBUG) {
                            FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeError)
                        }
                        keyItem ?: return
                        when (type) {
                            OfflineLoggerType.COMMON -> {
                                Bugfender.e(
                                    OFFLINE_KEY, "${
                                        OfflineUtils.getPrettyOfflineKey(keyItem)
                                    }, with message: $message"
                                )
                            }

                            OfflineLoggerType.PREPARE -> {
                                val errorMessage = "▧ %s preparing content: ${
                                    OfflineUtils.getPrettyOfflineKey(keyItem)
                                }, with message: $message"
                                if (isOnline(this@AppManager)) {
                                    val errorType = "[ERROR]"
                                    Bugfender.e(OFFLINE_KEY, String.format(errorMessage, errorType))
                                } else {
                                    val errorType = "[ERROR NON-CRITICAL]"
                                    Bugfender.w(OFFLINE_KEY, String.format(errorMessage, errorType))
                                }
                            }

                            OfflineLoggerType.DOWNLOAD_ERROR -> {
                                if (isOnline(this@AppManager)) {
                                    val errorMessage = "▧ [ERROR] downloading content: ${
                                        OfflineUtils.getPrettyOfflineKey(keyItem)
                                    }, with message: $message"
                                    Bugfender.e(OFFLINE_KEY, errorMessage)

                                } else {
                                    val errorMessage =
                                        "▧ [ERROR NON-CRITICAL] downloading content: ${
                                            OfflineUtils.getPrettyOfflineKey(keyItem)
                                        }, with message: $message"
                                    Bugfender.w(OFFLINE_KEY, errorMessage)
                                }
                            }

                            OfflineLoggerType.DOWNLOAD_WARNING -> {
                                val errorMessage =
                                    "▧ [ERROR NON-CRITICAL] downloading content: ${
                                        OfflineUtils.getPrettyOfflineKey(keyItem)
                                    }, with message: $message"
                                Bugfender.w(OFFLINE_KEY, errorMessage)
                            }

                            OfflineLoggerType.DEBUG -> Unit
                        }
                    }
                })
        ) { OfflineDownloaderCreator(it) }

        Offline.getOfflineManager().addListener(object : OfflineManager.OfflineListener() {
            override fun onItemAdded(key: String) {
                onItemStartedDownload(key)
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeStarted)
            }

            override fun onItemStartedDownload(key: String) {
                if (!OfflineModeService.isStarted) {
                    ContextCompat.startForegroundService(
                        this@AppManager,
                        Intent(this@AppManager, OfflineModeService::class.java)
                    )
                }
            }

            override fun onItemRemoved(key: String) {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeDeleted)
            }

            override fun onItemsRemoved(keys: List<String>) {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeDeletedAll)
            }

            override fun onItemPaused(key: String) {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModePaused)
            }

            override fun onItemResumed(key: String) {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeResumed)
            }

            override fun onItemDownloaded(key: String) {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeCompleted)
            }

            override fun onPausedAll() {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModePausedAll)
            }

            override fun onResumedAll() {
                FirebaseAnalytics.logEvent(AnalyticsEvent.OfflineModeResumedAll)
            }
        })

        DownloadsRepository.loadData()

        Intercom.initialize(this, BuildConfig.INTERCOM_API_KEY, BuildConfig.INTERCOM_AP_ID)
        Bugfender.init(this, BuildConfig.BUGFENDER_KEY, BuildConfig.DEBUG)
    }

    override fun performLogoutOnAuthError() {
        StudentLogoutTask(LogoutTask.Type.LOGOUT, typefaceBehavior = typefaceBehavior).execute()
    }

    override fun getWorkManagerFactory(): WorkerFactory = workerFactory

    companion object {
        const val OFFLINE_KEY = "OfflineTest"
    }
}
