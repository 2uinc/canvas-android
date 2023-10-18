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

package com.instructure.student.receivers

import android.app.Activity
import android.content.Context
import com.google.firebase.messaging.RemoteMessage

import com.instructure.student.R
import com.instructure.student.activity.NavigationActivity
import com.instructure.pandautils.receivers.PushExternalReceiver
import io.intercom.android.sdk.push.IntercomPushClient

class StudentPushExternalReceiver : PushExternalReceiver() {

    private val mIntercomPushClient = IntercomPushClient()

    override fun getAppColor() = R.color.login_studentAppTheme

    override fun getAppName(context: Context): String = context.getString(R.string.student_app_name)

    override fun getStartingActivityClass(): Class<out Activity> = NavigationActivity.startActivityClass

    override fun onMessageReceived(message: RemoteMessage) {
        if (mIntercomPushClient.isIntercomPush(message.data)) {
            mIntercomPushClient.handlePush(application, message.data)

        } else {
            super.onMessageReceived(message)
        }
    }
}
