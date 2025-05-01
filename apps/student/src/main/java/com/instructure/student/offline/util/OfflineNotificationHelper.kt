@file:Suppress("FunctionName", "SpellCheckingInspection")

package com.instructure.student.offline.util

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunction
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory
import com.amazonaws.regions.Regions
import com.google.firebase.messaging.FirebaseMessaging
import com.instructure.canvasapi2.managers.CommunicationChannelsManager
import com.instructure.canvasapi2.managers.NotificationPreferencesManager
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.ContextKeeper
import com.instructure.canvasapi2.utils.RemoteConfigPrefs
import com.instructure.canvasapi2.utils.weave.catch
import com.instructure.canvasapi2.utils.weave.tryWeave
import com.instructure.canvasapi2.utils.weave.weave
import com.instructure.student.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.json.JSONObject

object OfflineNotificationHelper {

    private val mNotificationPreferencesManager: NotificationPreferencesManager

    private const val EMAIL = BuildConfig.EMAIL_DOMAIN

    init {
        mNotificationPreferencesManager = EntryPointAccessors.fromApplication(
            ContextKeeper.appContext, OfflineNotificationEntryPoint::class.java
        ).getNotificationPreferencesManager()
    }

    fun setup() {
        tryWeave(background = true) {
            ApiPrefs.user?.let { user ->
                CommunicationChannelsManager.getCommunicationChannelsAsync(user.id, true)
                    .await().dataOrNull?.let { channels ->
                        val channel =
                            channels.firstOrNull { channel -> channel.address?.contains(EMAIL) == true }
                        if (channel == null) {
                            createUserChannel(
                                user.id, channels.firstOrNull { it.type == "push" }?.id ?: 0
                            )
                        }
                    }

                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    tryWeave(background = true) {
                        if (task.isSuccessful) {
                            subscribeUserToSNS(task.result, user.id)
                        }
                    } catch {
                        it.printStackTrace()
                    }
                }
            }
        } catch {
            it.printStackTrace()
        }
    }

    fun unsubscribeUserFromSNS() {
        val userId = ApiPrefs.user?.id ?: return
        val domain = ApiPrefs.domain
        val token = OfflineStorageHelper.deviceToken

        weave {
            inBackground {
                try {
                    deletePlatformEndpoint(DeletePlatformRequest("$userId", domain, token))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun createUserChannel(userId: Long, pushChannelId: Long) {
        val domain = ApiPrefs.domain

        try {
            val response = createUserChannel(UserChannelRequest("$userId", domain))

            val jo = JSONObject(response.body)
            val channelId = jo.getLong("id")

            copyChannelSettings(userId, pushChannelId, channelId)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun copyChannelSettings(userId: Long, fromId: Long, toId: Long) {
        val allowedTypes = listOf(
            "announcement",
            "appointment_availability",
            "appointment_cancelations",
            "calendar",
            "conversation_message",
            "course_content",
            "due_date",
            "grading",
            "invitation",
            "student_appointment_signups",
            "submission_comment",
            "discussion_mention",
        )

        val notificationPreferences =
            mNotificationPreferencesManager.getNotificationPreferencesAsync(userId, fromId, true)
                .await().dataOrThrow

        val filteredPrefs =
            notificationPreferences.notificationPreferences
                .filter { allowedTypes.contains(it.category) }
                .filter { it.frequency == "immediately" }
                .map { it.notification }

        mNotificationPreferencesManager.updateMultipleNotificationPreferencesAsync(
            toId, filteredPrefs, "immediately"
        ).await().dataOrThrow
    }

    private fun subscribeUserToSNS(token: String, userId: Long) {
        OfflineStorageHelper.deviceToken = token
        val domain = ApiPrefs.domain

        try {
            createPlatformEndpoint(CreatePlatformRequest("$userId", domain, token))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAmazonCredentials(debug: Boolean = false) = object : AWSCredentials {
        override fun getAWSAccessKeyId(): String {
            val param = if (debug) "aws_key_debug" else "aws_key_release"
            val value = RemoteConfigPrefs.getString(param).orEmpty()
            return value
        }

        override fun getAWSSecretKey(): String {
            val param = if (debug) "aws_secret_key_debug" else "aws_secret_key_release"
            val value = RemoteConfigPrefs.getString(param).orEmpty()
            return value
        }
    }

    private fun createUserChannel(request: UserChannelRequest): UserChannelResponse {
        return if (BuildConfig.DEBUG) {
            getLambdaDebugInvoker().mobilecanvas_createUserChannel_stg(request)

        } else {
            getLambdaReleaseInvoker().mobilecanvas_createUserChannel_prod(request)
        }
    }

    private fun createPlatformEndpoint(request: CreatePlatformRequest) {
        if (BuildConfig.DEBUG) {
            getLambdaDebugInvoker().mobilecanvas_createPlatformEndpoint_stg(request)

        } else {
            getLambdaReleaseInvoker().mobilecanvas_createPlatformEndpoint_prod(request)
        }
    }

    private fun deletePlatformEndpoint(request: DeletePlatformRequest) {
        if (BuildConfig.DEBUG) {
            getLambdaDebugInvoker().mobilecanvas_deletePlatformEndpoint_stg(request)

        } else {
            getLambdaReleaseInvoker().mobilecanvas_deletePlatformEndpoint_prod(request)
        }
    }

    private fun getLambdaDebugInvoker(): ILambdaDebug {
        val provider = object : AWSCredentialsProvider {
            override fun getCredentials() = getAmazonCredentials()

            override fun refresh() {

            }
        }
        return LambdaInvokerFactory.builder()
            .context(ContextKeeper.appContext)
            .region(Regions.US_WEST_2)
            .credentialsProvider(provider)
            .build().build(ILambdaDebug::class.java)
    }

    private fun getLambdaReleaseInvoker(): ILambdaRelease {
        val provider = object : AWSCredentialsProvider {
            override fun getCredentials() = getAmazonCredentials()

            override fun refresh() {

            }
        }
        return LambdaInvokerFactory.builder()
            .context(ContextKeeper.appContext)
            .region(Regions.US_WEST_2)
            .credentialsProvider(provider)
            .build().build(ILambdaRelease::class.java)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OfflineNotificationEntryPoint {
        fun getNotificationPreferencesManager(): NotificationPreferencesManager
    }

    interface ILambdaDebug {

        @LambdaFunction
        fun mobilecanvas_createUserChannel_stg(request: UserChannelRequest): UserChannelResponse

        @LambdaFunction
        fun mobilecanvas_createPlatformEndpoint_stg(request: CreatePlatformRequest)

        @LambdaFunction
        fun mobilecanvas_deletePlatformEndpoint_stg(request: DeletePlatformRequest)
    }

    interface ILambdaRelease {

        @LambdaFunction
        fun mobilecanvas_createUserChannel_prod(request: UserChannelRequest): UserChannelResponse

        @LambdaFunction
        fun mobilecanvas_createPlatformEndpoint_prod(request: CreatePlatformRequest)

        @LambdaFunction
        fun mobilecanvas_deletePlatformEndpoint_prod(request: DeletePlatformRequest)
    }

    data class UserChannelRequest(val userid: String, val domain: String)
    data class UserChannelResponse(val body: String)

    data class CreatePlatformRequest(
        val userid: String, val domain: String, val deviceToken: String,
        val osType: String = "android"
    )

    data class DeletePlatformRequest(
        val userid: String, val domain: String, val deviceToken: String
    )
}