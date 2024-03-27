package io.novafoundation.nova.feature_account_impl.presentation.startCreateWallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.novafoundation.nova.common.base.BaseFragment
import io.novafoundation.nova.common.di.FeatureUtils
import io.novafoundation.nova.common.utils.applyStatusBarInsets
import io.novafoundation.nova.common.utils.bindTo
import io.novafoundation.nova.common.view.setState
import io.novafoundation.nova.feature_account_api.di.AccountFeatureApi
import io.novafoundation.nova.feature_account_impl.R
import io.novafoundation.nova.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_start_create_wallet.startCreateWalletConfirmName
import kotlinx.android.synthetic.main.fragment_start_create_wallet.startCreateWalletNameInput
import kotlinx.android.synthetic.main.fragment_start_create_wallet.startCreateWalletToolbar

class StartCreateWalletFragment : BaseFragment<StartCreateWalletViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_start_create_wallet, container, false)
    }

    override fun initViews() {
        startCreateWalletToolbar.applyStatusBarInsets()
        startCreateWalletToolbar.setHomeButtonListener { viewModel.backClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .startCreateWallet()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: StartCreateWalletViewModel) {
        startCreateWalletNameInput.bindTo(viewModel.nameInput, viewLifecycleOwner.lifecycleScope)

        viewModel.confirmNameButtonState.observe { state ->
            startCreateWalletConfirmName.setState(state)
        }
    }
}