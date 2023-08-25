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
import com.instructure.canvasapi2.utils.MasqueradeHelper
import com.instructure.loginapi.login.tasks.LogoutTask
import com.instructure.pandautils.typeface.TypefaceBehavior
import com.instructure.student.offline.util.DownloadsRepository
import com.instructure.student.tasks.StudentLogoutTask
import com.instructure.student.offline.util.OfflineDownloaderCreator
import com.instructure.student.offline.util.OfflineModeService
import com.twou.offline.Offline
import com.twou.offline.OfflineManager
import dagger.hilt.android.HiltAndroidApp
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

        Offline.init(this) { OfflineDownloaderCreator(it) }

        Offline.getOfflineManager().addListener(object : OfflineManager.OfflineListener() {
            override fun onItemStartedDownload(key: String) {
                if (!OfflineModeService.isStarted) {
                    ContextCompat.startForegroundService(
                        this@AppManager,
                        Intent(this@AppManager, OfflineModeService::class.java)
                    )
                }
            }
        })

        DownloadsRepository.loadData()
    }

    override fun performLogoutOnAuthError() {
        StudentLogoutTask(LogoutTask.Type.LOGOUT, typefaceBehavior = typefaceBehavior).execute()
    }

    override fun getWorkManagerFactory(): WorkerFactory = workerFactory
}
