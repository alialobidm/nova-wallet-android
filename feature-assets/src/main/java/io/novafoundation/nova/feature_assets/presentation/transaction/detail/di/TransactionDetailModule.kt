package io.novafoundation.nova.feature_assets.presentation.transaction.detail.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import io.novafoundation.nova.common.address.AddressIconGenerator
import io.novafoundation.nova.common.di.viewmodel.ViewModelKey
import io.novafoundation.nova.common.di.viewmodel.ViewModelModule
import io.novafoundation.nova.feature_account_api.presenatation.account.AddressDisplayUseCase
import io.novafoundation.nova.feature_account_api.presenatation.actions.ExternalActions
import io.novafoundation.nova.feature_assets.presentation.AssetsRouter
import io.novafoundation.nova.feature_assets.presentation.model.OperationParcelizeModel
import io.novafoundation.nova.feature_assets.presentation.transaction.detail.transfer.TransactionDetailViewModel
import io.novafoundation.nova.feature_wallet_api.domain.ArbitraryTokenUseCase
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class TransactionDetailModule {

    @Provides
    @IntoMap
    @ViewModelKey(TransactionDetailViewModel::class)
    fun provideViewModel(
        router: AssetsRouter,
        addressIconGenerator: AddressIconGenerator,
        addressDisplayUseCase: AddressDisplayUseCase,
        chainRegistry: ChainRegistry,
        operation: OperationParcelizeModel.Transfer,
        externalActions: ExternalActions.Presentation,
        arbitraryTokenUseCase: ArbitraryTokenUseCase
    ): ViewModel {
        return TransactionDetailViewModel(
            router,
            addressIconGenerator,
            addressDisplayUseCase,
            chainRegistry,
            operation,
            externalActions,
            arbitraryTokenUseCase
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): TransactionDetailViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(TransactionDetailViewModel::class.java)
    }
}
