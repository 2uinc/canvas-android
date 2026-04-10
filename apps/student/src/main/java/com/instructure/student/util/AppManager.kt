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
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.bugfender.sdk.Bugfender
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.trace.DatadogTracing
import com.instructure.canvasapi2.utils.MasqueradeHelper
import com.instructure.loginapi.login.tasks.LogoutTask
import com.instructure.pandautils.analytics.pageview.PageViewUploadWorker
import com.instructure.pandautils.features.reminder.AlarmScheduler
import com.instructure.pandautils.room.offline.DatabaseProvider
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.rum.Rum

@HiltAndroidApp
class AppManager : BaseAppManager() {

    @Inject
    lateinit var typefaceBehavior: TypefaceBehavior

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var databaseProvider: DatabaseProvider

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var workManager: WorkManager
    lateinit var logger: Logger

    override fun onCreate() {
        super.onCreate()
        MasqueradeHelper.masqueradeLogoutTask = Runnable {
            StudentLogoutTask(
                LogoutTask.Type.LOGOUT,
                typefaceBehavior = typefaceBehavior,
                databaseProvider = databaseProvider,
                alarmScheduler = alarmScheduler
            ).execute()
        }

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

        Bugfender.init(this, BuildConfig.BUGFENDER_KEY, BuildConfig.DEBUG)

        schedulePandataUpload()
        initPendo()
        setupDataDog()
    }

    private fun setupDataDog() {
        val configuration = Configuration.Builder(
            clientToken = BuildConfig.DATADOG_CLIENT_TOKEN,
            env = if (BuildConfig.BUILD_TYPE == "release") "production" else "staging",
            service = "degrees-mobile"
        ).build()

        Datadog.initialize(this, configuration, trackingConsent = TrackingConsent.GRANTED)

        Trace.equals(
            TraceConfiguration.Builder().setEventMapper(object : SpanEventMapper {
                override fun map(event: SpanEvent): SpanEvent {
                    val originalUrl = event.resource
                    event.resource = redactSensitiveData(originalUrl)
                    return event
                }
            })
                .build()
        )

        GlobalDatadogTracer.registerIfAbsent(
            DatadogTracing.newTracerBuilder()
                .build()
        )

        Datadog.setVerbosity(Log.INFO)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(true)
            .setName("GetSmarterAndroid")
            .build()

        val rumConfig = RumConfiguration.Builder(BuildConfig.DATADOG_APPLICATION_ID)
            .setResourceEventMapper { event ->
                val originalUrl = event.resource.url
                event.resource.url = redactSensitiveData(originalUrl)
                event
            }
            .setErrorEventMapper { event ->
                event.error.message = redactSensitiveData(event.error.message)
                event.error.resource?.url = redactSensitiveData(event.error.resource?.url.orEmpty())
                event
            }
            .trackUserInteractions()
            .trackAnonymousUser(true)
            .trackLongTasks()
            .trackBackgroundEvents(true)
            .trackNonFatalAnrs(true)
            .build()
        Rum.enable(rumConfig)
    }
    private fun redactSensitiveData(message: String) = message.replace(
        Regex(
            "(username|password|wstoken|token)=[^&]*",
            RegexOption.IGNORE_CASE
        ), "$1=****"
    )
    private fun initPendo() {
//        val options = Pendo.PendoOptions.Builder().setJetpackComposeBeta(true).build()
//        Pendo.setup(this, BuildConfig.PENDO_TOKEN, options, null)
    }

    override fun performLogoutOnAuthError() {
        StudentLogoutTask(
            LogoutTask.Type.LOGOUT,
            typefaceBehavior = typefaceBehavior,
            databaseProvider = databaseProvider,
            alarmScheduler = alarmScheduler
        ).execute()
    }

    override fun getWorkManagerFactory(): WorkerFactory = workerFactory

    private fun schedulePandataUpload() {
        val workRequest = PeriodicWorkRequestBuilder<PageViewUploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork("pageView-student", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    companion object {
        const val OFFLINE_KEY = "OfflineTest"
    }
}
