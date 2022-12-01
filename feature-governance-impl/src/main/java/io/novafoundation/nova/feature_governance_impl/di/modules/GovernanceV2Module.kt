package io.novafoundation.nova.feature_governance_impl.di.modules

import dagger.Module
import dagger.Provides
import io.novafoundation.nova.common.data.network.NetworkApiCreator
import io.novafoundation.nova.common.di.scope.FeatureScope
import io.novafoundation.nova.feature_governance_api.data.source.GovernanceSource
import io.novafoundation.nova.feature_governance_impl.data.offchain.v2.PolkassemblyV2Api
import io.novafoundation.nova.feature_governance_impl.data.preimage.PreImageSizer
import io.novafoundation.nova.feature_governance_impl.data.repository.v2.Gov2OffChainReferendaInfoRepository
import io.novafoundation.nova.feature_governance_impl.data.repository.v2.Gov2PreImageRepository
import io.novafoundation.nova.feature_governance_impl.data.repository.v2.GovV2ConvictionVotingRepository
import io.novafoundation.nova.feature_governance_impl.data.repository.v2.GovV2OnChainReferendaRepository
import io.novafoundation.nova.feature_governance_impl.data.source.StaticGovernanceSource
import io.novafoundation.nova.runtime.di.REMOTE_STORAGE_SOURCE
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry
import io.novafoundation.nova.runtime.storage.source.StorageDataSource
import javax.inject.Named
import javax.inject.Qualifier

@Qualifier
annotation class GovernanceV2

@Module
class GovernanceV2Module {

    @Provides
    @FeatureScope
    fun provideOnChainReferendaRepository(
        @Named(REMOTE_STORAGE_SOURCE) storageSource: StorageDataSource,
        chainRegistry: ChainRegistry
    ) = GovV2OnChainReferendaRepository(storageSource, chainRegistry)

    @Provides
    @FeatureScope
    fun provideConvictionVotingRepository(
        @Named(REMOTE_STORAGE_SOURCE) storageSource: StorageDataSource,
        chainRegistry: ChainRegistry
    ) = GovV2ConvictionVotingRepository(storageSource, chainRegistry)

    @Provides
    @FeatureScope
    fun providePolkassemblyApi(apiCreator: NetworkApiCreator) = apiCreator.create(PolkassemblyV2Api::class.java)

    @Provides
    @FeatureScope
    fun provideOffChainInfoRepository(polkassemblyV2Api: PolkassemblyV2Api) = Gov2OffChainReferendaInfoRepository(polkassemblyV2Api)

    @Provides
    @FeatureScope
    fun providePreImageRepository(
        @Named(REMOTE_STORAGE_SOURCE) storageSource: StorageDataSource,
        preImageSizer: PreImageSizer,
    ) = Gov2PreImageRepository(storageSource, preImageSizer)

    @Provides
    @FeatureScope
    @GovernanceV2
    fun provideGovernanceSource(
        referendaRepository: GovV2OnChainReferendaRepository,
        convictionVotingRepository: GovV2ConvictionVotingRepository,
        offChainInfoRepository: Gov2OffChainReferendaInfoRepository,
        preImageRepository: Gov2PreImageRepository,
    ): GovernanceSource = StaticGovernanceSource(
        referenda = referendaRepository,
        convictionVoting = convictionVotingRepository,
        offChainInfo = offChainInfoRepository,
        preImageRepository = preImageRepository
    )
}