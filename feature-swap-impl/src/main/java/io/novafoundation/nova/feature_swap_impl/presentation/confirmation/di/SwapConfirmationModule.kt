package io.novafoundation.nova.feature_swap_impl.presentation.confirmation.di

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import io.novafoundation.nova.common.di.viewmodel.ViewModelKey
import io.novafoundation.nova.common.di.viewmodel.ViewModelModule
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.feature_account_api.domain.interfaces.AccountRepository
import io.novafoundation.nova.feature_account_api.presenatation.account.wallet.WalletUiUseCase
import io.novafoundation.nova.feature_swap_impl.domain.interactor.SwapInteractor
import io.novafoundation.nova.feature_swap_impl.presentation.common.PriceImpactFormatter
import io.novafoundation.nova.feature_swap_impl.presentation.common.SlippageAlertMixinFactory
import io.novafoundation.nova.feature_swap_impl.presentation.common.SwapRateFormatter
import io.novafoundation.nova.feature_swap_impl.presentation.confirmation.SwapConfirmationPayload
import io.novafoundation.nova.feature_swap_impl.presentation.confirmation.SwapConfirmationViewModel
import io.novafoundation.nova.feature_wallet_api.domain.interfaces.WalletRepository
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry

@Module(includes = [ViewModelModule::class])
class SwapConfirmationModule {

    @Provides
    @IntoMap
    @ViewModelKey(SwapConfirmationViewModel::class)
    fun provideViewModel(
        swapInteractor: SwapInteractor,
        resourceManager: ResourceManager,
        swapConfirmationPayload: SwapConfirmationPayload,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        swapRateFormatter: SwapRateFormatter,
        priceImpactFormatter: PriceImpactFormatter,
        walletUiUseCase: WalletUiUseCase,
        slippageAlertMixinFactory: SlippageAlertMixinFactory
    ): ViewModel {
        return SwapConfirmationViewModel(
            swapInteractor,
            resourceManager,
            swapConfirmationPayload,
            walletRepository,
            accountRepository,
            chainRegistry,
            swapRateFormatter,
            priceImpactFormatter,
            walletUiUseCase,
            slippageAlertMixinFactory
        )
    }

    @Provides
    fun provideViewModelCreator(
        fragment: Fragment,
        viewModelFactory: ViewModelProvider.Factory
    ): SwapConfirmationViewModel {
        return ViewModelProvider(fragment, viewModelFactory).get(SwapConfirmationViewModel::class.java)
    }
}
