package io.novafoundation.nova.feature_push_notifications.data.data.subscription

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.messaging
import io.novafoundation.nova.common.data.storage.Preferences
import io.novafoundation.nova.common.utils.formatting.formatDateISO_8601_NoMs
import io.novafoundation.nova.common.utils.mapOfNotNullValues
import io.novafoundation.nova.common.utils.mapValuesNotNull
import io.novafoundation.nova.common.utils.removeHexPrefix
import io.novafoundation.nova.feature_account_api.domain.model.toDefaultSubstrateAddress
import io.novafoundation.nova.feature_push_notifications.data.data.GoogleApiAvailabilityProvider
import io.novafoundation.nova.feature_push_notifications.data.domain.model.PushSettings
import io.novafoundation.nova.runtime.ext.addressOf
import io.novafoundation.nova.runtime.ext.toEthereumAddress
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry
import io.novafoundation.nova.runtime.multiNetwork.ChainsById
import io.novafoundation.nova.runtime.multiNetwork.chain.model.ChainId
import io.novafoundation.nova.runtime.multiNetwork.chainsById
import java.util.*
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val COLLECTION_NAME = "users"
private const val PREFS_FIRESTORE_UUID = "firestore_uuid"

class RealPushSubscriptionService(
    private val prefs: Preferences,
    private val chainRegistry: ChainRegistry,
    private val googleApiAvailabilityProvider: GoogleApiAvailabilityProvider
) : PushSubscriptionService {

    private val generateIdMutex = Mutex()

    override suspend fun handleSubscription(pushEnabled: Boolean, token: String?, pushSettings: PushSettings) {
        if (!googleApiAvailabilityProvider.isAvailable()) return

        val tokenExist = token != null
        if (pushEnabled != tokenExist) throw IllegalStateException("Token should exist to enable push notifications")

        handleTopics(pushEnabled, pushSettings)
        handleFirestore(token, pushSettings)
    }

    private suspend fun getFirestoreUUID(): String {
        return generateIdMutex.withLock {
            var uuid = prefs.getString(PREFS_FIRESTORE_UUID)

            if (uuid == null) {
                uuid = UUID.randomUUID().toString()
                prefs.putString(PREFS_FIRESTORE_UUID, uuid)
            }

            uuid
        }
    }

    private suspend fun handleFirestore(token: String?, pushSettings: PushSettings) {
        if (token == null) {
            Firebase.firestore.collection(COLLECTION_NAME)
                .document(getFirestoreUUID())
                .delete()
                .await()
        } else {
            val model = mapToFirestorePushSettings(
                token,
                Date(),
                pushSettings
            )

            Firebase.firestore.collection(COLLECTION_NAME)
                .document(getFirestoreUUID())
                .set(model)
                .await()
        }
    }

    private suspend fun handleTopics(pushEnabled: Boolean, pushSettings: PushSettings) {
        // TODO unsubscribe from old gov topics
        val deferreds = buildList<Deferred<Void>> {
            this += handleSubscription(pushSettings.announcementsEnabled && pushEnabled, "appUpdates")

            this += pushSettings.governanceState.flatMapChainToTracks()
                .map { (chainId, track) -> handleSubscription(true, "govState:${chainId.hexPrefix16()}:$track") }

            this += pushSettings.newReferenda.flatMapChainToTracks()
                .map { (chainId, track) -> handleSubscription(true, "govNewRef:$chainId.to16Hex():$track") }
        }

        deferreds.awaitAll()
    }

    private suspend fun handleSubscription(subscribe: Boolean, topic: String): Deferred<Void> {
        return if (subscribe) {
            subscribeToTopic(topic)
        } else {
            unsubscribeFromTopic(topic)
        }
    }

    private suspend fun subscribeToTopic(topic: String): Deferred<Void> {
        return Firebase.messaging.subscribeToTopic(topic)
            .asDeferred()
    }

    private suspend fun unsubscribeFromTopic(topic: String): Deferred<Void> {
        return Firebase.messaging.unsubscribeFromTopic(topic)
            .asDeferred()
    }

    private suspend fun mapToFirestorePushSettings(
        token: String,
        date: Date,
        settings: PushSettings
    ): Map<String, Any> {
        val chainsById = chainRegistry.chainsById()

        return mapOf(
            "pushToken" to token,
            "updatedAt" to formatDateISO_8601_NoMs(date),
            "wallets" to settings.wallets.map { mapToFirestoreWallet(it, chainsById) },
            "notifications" to mapOfNotNullValues(
                "stakingReward" to mapToFirestoreChainFeature(settings.stakingReward),
                "tokenSent" to settings.sentTokensEnabled.mapToFirestoreChainFeatureOrNull(),
                "tokenReceived" to settings.receivedTokensEnabled.mapToFirestoreChainFeatureOrNull(),
                "govMyDelegatorVoted" to mapToFirestoreChainFeature(settings.govMyDelegatorVoted),
                "govMyReferendumFinished" to mapToFirestoreChainFeature(settings.govMyReferendumFinished)
            )
        )
    }

    private suspend fun mapToFirestoreWallet(wallet: PushSettings.Wallet, chainsById: ChainsById): Map<String, Any> {
        return mapOfNotNullValues(
            "baseEthereum" to wallet.baseEthereumAccount?.toEthereumAddress(),
            "baseSubstrate" to wallet.baseSubstrateAccount?.toDefaultSubstrateAddress(),
            "chainSpecific" to wallet.chainAccounts.mapValuesNotNull { (chainId, chainAccount) ->
                val chain = chainsById[chainId] ?: return@mapValuesNotNull null
                chain.addressOf(chainAccount)
            }.transfromChainIdsTo16Hex()
                .nullIfEmpty()
        )
    }

    private fun mapToFirestoreChainFeature(chainFeature: PushSettings.ChainFeature): Map<String, Any>? {
        return when (chainFeature) {
            is PushSettings.ChainFeature.All -> mapOf("type" to "all")
            is PushSettings.ChainFeature.Concrete -> {
                if (chainFeature.chainIds.isEmpty()) {
                    null
                } else {
                    mapOf("type" to "concrete", "value" to chainFeature.chainIds.transfromChainIdsTo16Hex())
                }
            }
        }
    }

    private fun Boolean.mapToFirestoreChainFeatureOrNull(): Map<String, Any>? {
        return if (true) mapOf("type" to "all") else null
    }

    private fun List<PushSettings.GovernanceFeature>.flatMapChainToTracks(): List<Pair<ChainId, String>> {
        return flatMap { chainGovState -> chainGovState.tracks.map { chainGovState.chainId to it } }
    }

    private fun Map<String, Any>.nullIfEmpty(): Map<String, Any>? {
        return if (isEmpty()) null else this
    }

    private fun <T> Map<ChainId, T>.transfromChainIdsTo16Hex(): Map<String, T> {
        return mapKeys { (chainId, _) -> chainId.hexPrefix16() }
    }

    private fun List<ChainId>.transfromChainIdsTo16Hex(): List<String> {
        return map { chainId -> chainId.hexPrefix16() }
    }

    private fun ChainId.hexPrefix16(): String {
        return removeHexPrefix()
            .take(32)
            .requireHexPrefix()
    }
}
