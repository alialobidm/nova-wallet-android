package io.novafoundation.nova.feature_wallet_impl.data.network.blockchain.assets.balances.utility

import android.util.Log
import io.novafoundation.nova.common.data.network.runtime.binding.AccountBalance
import io.novafoundation.nova.common.data.network.runtime.binding.AccountInfo
import io.novafoundation.nova.common.utils.LOG_TAG
import io.novafoundation.nova.common.utils.balances
import io.novafoundation.nova.common.utils.decodeValue
import io.novafoundation.nova.common.utils.numberConstant
import io.novafoundation.nova.common.utils.system
import io.novafoundation.nova.core.updater.SharedRequestsBuilder
import io.novafoundation.nova.core_db.dao.LockDao
import io.novafoundation.nova.feature_account_api.domain.model.MetaAccount
import io.novafoundation.nova.feature_wallet_api.data.cache.AssetCache
import io.novafoundation.nova.feature_wallet_api.data.cache.bindAccountInfoOrDefault
import io.novafoundation.nova.feature_wallet_api.data.cache.updateAsset
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.assets.balances.AssetBalance
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.assets.balances.BalanceSyncUpdate
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.types.Balance
import io.novafoundation.nova.feature_wallet_api.domain.model.Asset
import io.novafoundation.nova.feature_wallet_api.domain.model.Asset.Companion.calculateTransferable
import io.novafoundation.nova.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import io.novafoundation.nova.feature_wallet_impl.data.network.blockchain.assets.balances.bindBalanceLocks
import io.novafoundation.nova.feature_wallet_impl.data.network.blockchain.assets.balances.updateLocks
import io.novafoundation.nova.runtime.ext.utilityAsset
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.multiNetwork.getRuntime
import io.novafoundation.nova.runtime.storage.source.StorageDataSource
import io.novafoundation.nova.runtime.storage.source.query.metadata
import io.novafoundation.nova.runtime.storage.typed.account
import io.novafoundation.nova.runtime.storage.typed.system
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

class NativeAssetBalance(
    private val chainRegistry: ChainRegistry,
    private val assetCache: AssetCache,
    private val substrateRemoteSource: SubstrateRemoteSource,
    private val remoteStorage: StorageDataSource,
    private val lockDao: LockDao
) : AssetBalance {

    override suspend fun startSyncingBalanceLocks(
        metaAccount: MetaAccount,
        chain: Chain,
        chainAsset: Chain.Asset,
        accountId: AccountId,
        subscriptionBuilder: SharedRequestsBuilder
    ): Flow<*> {
        val runtime = chainRegistry.getRuntime(chain.id)
        val storage = runtime.metadata.balances().storage("Locks")
        val key = storage.storageKey(runtime, accountId)

        return subscriptionBuilder.subscribe(key)
            .map { change ->
                val balanceLocks = bindBalanceLocks(storage.decodeValue(change.value, runtime)).orEmpty()
                lockDao.updateLocks(balanceLocks, metaAccount.id, chain.id, chainAsset.id)
            }
    }

    override suspend fun isSelfSufficient(chainAsset: Chain.Asset): Boolean {
        return true
    }

    override suspend fun existentialDeposit(chain: Chain, chainAsset: Chain.Asset): BigInteger {
        val runtime = chainRegistry.getRuntime(chain.id)

        return runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime)
    }

    override suspend fun queryAccountBalance(chain: Chain, chainAsset: Chain.Asset, accountId: AccountId): AccountBalance {
        return substrateRemoteSource.getAccountInfo(chain.id, accountId).data
    }

    override suspend fun subscribeTransferableAccountBalance(
        chain: Chain,
        chainAsset: Chain.Asset,
        accountId: AccountId,
        sharedSubscriptionBuilder: SharedRequestsBuilder
    ): Flow<Balance> {
        return remoteStorage.subscribe(chain.id, sharedSubscriptionBuilder) {
            metadata.system.account.observe(accountId).map {
                val accountInfo = it ?: AccountInfo.empty()

                accountInfo.transferableBalance()
            }
        }
    }

    override suspend fun queryTotalBalance(chain: Chain, chainAsset: Chain.Asset, accountId: AccountId): BigInteger {
        val accountData = queryAccountBalance(chain, chainAsset, accountId)

        return accountData.free + accountData.reserved
    }

    override suspend fun startSyncingBalance(
        chain: Chain,
        chainAsset: Chain.Asset,
        metaAccount: MetaAccount,
        accountId: AccountId,
        subscriptionBuilder: SharedRequestsBuilder
    ): Flow<BalanceSyncUpdate> {
        val runtime = chainRegistry.getRuntime(chain.id)

        val key = try {
            runtime.metadata.system().storage("Account").storageKey(runtime, accountId)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to construct account storage key: ${e.message} in ${chain.name}")

            return emptyFlow()
        }

        return subscriptionBuilder.subscribe(key)
            .map { change ->
                val accountInfo = bindAccountInfoOrDefault(change.value, runtime)
                val assetChanged = assetCache.updateAsset(metaAccount.id, chain.utilityAsset, accountInfo)

                if (assetChanged) {
                    BalanceSyncUpdate.CauseFetchable(change.block)
                } else {
                    BalanceSyncUpdate.NoCause
                }
            }
    }

    private fun AccountInfo.transferableBalance(): Balance {
        return transferableMode.calculateTransferable(data)
    }

    private val AccountInfo.transferableMode: Asset.TransferableMode
        get() = if (data.flags.holdsAndFreezesEnabled()) {
            Asset.TransferableMode.HOLDS_AND_FREEZES
        } else {
            Asset.TransferableMode.REGULAR
        }
}
