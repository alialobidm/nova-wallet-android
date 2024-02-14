package io.novafoundation.nova.feature_push_notifications.data.data

import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import io.novafoundation.nova.common.data.storage.Preferences
import io.novafoundation.nova.common.utils.coroutines.RootScope
import io.novafoundation.nova.feature_push_notifications.data.NovaFirebaseMessagingService
import io.novafoundation.nova.feature_push_notifications.data.data.settings.PushSettings
import io.novafoundation.nova.feature_push_notifications.data.data.settings.PushSettingsProvider
import io.novafoundation.nova.feature_push_notifications.data.data.subscription.PushSubscriptionService
import kotlinx.coroutines.launch
import kotlin.jvm.Throws

interface PushNotificationsService {

    fun onTokenUpdated(token: String)

    fun isPushNotificationsEnabled(): Boolean

    suspend fun initPushNotifications(): Result<Unit>

    suspend fun updatePushSettings(enabled: Boolean, pushSettings: PushSettings): Result<Unit>
}

class RealPushNotificationsService(
    private val settingProvider: PushSettingsProvider,
    private val subscriptionService: PushSubscriptionService,
    private val rootScope: RootScope,
    private val preferences: Preferences,
    private val tokenCache: PushTokenCache,
    private val googleApiAvailabilityProvider: GoogleApiAvailabilityProvider
) : PushNotificationsService {

    private var skipTokenCallback = false

    init {
        if (isPushNotificationsEnabled()) {
            NovaFirebaseMessagingService.logToken()
        }
    }

    override fun onTokenUpdated(token: String) {
        if (!googleApiAvailabilityProvider.isAvailable()) return
        if (!isPushNotificationsEnabled()) return
        if (skipTokenCallback) return

        rootScope.launch {
            tokenCache.updatePushToken(token)
            updatePushSettings(isPushNotificationsEnabled(), settingProvider.getPushSettings())
        }
    }

    override suspend fun updatePushSettings(enabled: Boolean, pushSettings: PushSettings): Result<Unit> {
        if (!googleApiAvailabilityProvider.isAvailable()) return Result.success(Unit)

        return runCatching {
            setPushNotificationsEnabled(enabled)
            val pushToken = getPushToken()
            settingProvider.updateWalletSettings(pushSettings)
            subscriptionService.handleSubscription(enabled, pushToken, pushSettings)
        }
    }

    override fun isPushNotificationsEnabled(): Boolean {
        return settingProvider.isPushNotificationsEnabled()
    }

    override suspend fun initPushNotifications(): Result<Unit> {
        if (!googleApiAvailabilityProvider.isAvailable()) return Result.success(Unit)

        return updatePushSettings(true, settingProvider.getDefaultPushSettings())
    }

    @Throws
    private suspend fun setPushNotificationsEnabled(isEnable: Boolean) {
        if (isEnable == isPushNotificationsEnabled()) return
        skipTokenCallback = true

        val pushToken = if (isEnable) {
            NovaFirebaseMessagingService.requestToken()
        } else {
            NovaFirebaseMessagingService.deleteToken()
            null
        }

        tokenCache.updatePushToken(pushToken)
        Firebase.messaging.isAutoInitEnabled = isEnable
        settingProvider.setPushNotificationsEnabled(isEnable)

        skipTokenCallback = false
    }

    private suspend fun getPushToken(): String? {
        return tokenCache.getPushToken()
    }
}
