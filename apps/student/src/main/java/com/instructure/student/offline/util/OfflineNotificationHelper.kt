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
                        if (task.isSuccessful) subscribeUserToSNS(task.result, user.id)
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
                    getLambdaInvoker()
                        .deletePlatformEndpoint(DeletePlatformRequest("$userId", domain, token))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun createUserChannel(userId: Long, pushChannelId: Long) {
        val domain = ApiPrefs.domain

        try {
            val response =
                getLambdaInvoker().createUserChannel(UserChannelRequest("$userId", domain))

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
            getLambdaInvoker()
                .createPlatformEndpoint(CreatePlatformRequest("$userId", domain, token))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAmazonCredentials() = object : AWSCredentials {
        override fun getAWSAccessKeyId(): String {
            return BuildConfig.AWS_ACCESS_KEY
        }

        override fun getAWSSecretKey(): String {
            return BuildConfig.AWS_SECRET_KEY
        }
    }

    private fun getLambdaInvoker(): ILambda {
        val provider = object : AWSCredentialsProvider {
            override fun getCredentials() = getAmazonCredentials()

            override fun refresh() {

            }
        }
        return LambdaInvokerFactory.builder()
            .context(ContextKeeper.appContext)
            .region(Regions.US_EAST_1)
            .credentialsProvider(provider)
            .build().build(ILambda::class.java)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OfflineNotificationEntryPoint {
        fun getNotificationPreferencesManager(): NotificationPreferencesManager
    }

    interface ILambda {

        @LambdaFunction
        fun createUserChannel(request: UserChannelRequest): UserChannelResponse

        @LambdaFunction
        fun createPlatformEndpoint(request: CreatePlatformRequest)

        @LambdaFunction
        fun deletePlatformEndpoint(request: DeletePlatformRequest)
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