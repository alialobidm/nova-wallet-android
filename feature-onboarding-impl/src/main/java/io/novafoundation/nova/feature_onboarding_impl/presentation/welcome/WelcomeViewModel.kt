package io.novafoundation.nova.feature_onboarding_impl.presentation.welcome

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.novafoundation.nova.common.base.BaseViewModel
import io.novafoundation.nova.common.data.network.AppLinksProvider
import io.novafoundation.nova.common.mixin.actionAwaitable.ActionAwaitableMixin
import io.novafoundation.nova.common.mixin.actionAwaitable.fixedSelectionOf
import io.novafoundation.nova.common.mixin.api.Browserable
import io.novafoundation.nova.common.utils.Event
import io.novafoundation.nova.feature_account_api.presenatation.account.add.AddAccountPayload
import io.novafoundation.nova.feature_onboarding_impl.OnboardingRouter
import io.novafoundation.nova.feature_onboarding_impl.presentation.welcome.model.HardwareWalletModel
import io.novafoundation.nova.feature_versions_api.domain.UpdateNotificationsInteractor

class WelcomeViewModel(
    shouldShowBack: Boolean,
    private val router: OnboardingRouter,
    private val appLinksProvider: AppLinksProvider,
    private val addAccountPayload: AddAccountPayload,
    private val actionAwaitableMixinFactory: ActionAwaitableMixin.Factory,
    updateNotificationsInteractor: UpdateNotificationsInteractor
) : BaseViewModel(),
    Browserable {

    val shouldShowBackLiveData: LiveData<Boolean> = MutableLiveData(shouldShowBack)

    val selectHardwareWallet = actionAwaitableMixinFactory.fixedSelectionOf<HardwareWalletModel>()

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    init {
        updateNotificationsInteractor.allowInAppUpdateCheck()
    }

    fun createAccountClicked() {
        when (addAccountPayload) {
            is AddAccountPayload.MetaAccount -> router.openCreateAccount()
            is AddAccountPayload.ChainAccount -> router.openMnemonicScreen(accountName = null, addAccountPayload)
        }
    }

    fun importAccountClicked() {
        TODO("Waiting for import types screen")
    }

    fun termsClicked() {
        openBrowserEvent.value = Event(appLinksProvider.termsUrl)
    }

    fun privacyClicked() {
        openBrowserEvent.value = Event(appLinksProvider.privacyUrl)
    }

    fun backClicked() {
        router.back()
    }
}
