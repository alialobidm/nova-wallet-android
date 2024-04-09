package io.novafoundation.nova.feature_account_impl.presentation.cloudBackup.createPassword

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.novafoundation.nova.common.base.BaseFragment
import io.novafoundation.nova.common.di.FeatureUtils
import io.novafoundation.nova.common.utils.applyStatusBarInsets
import io.novafoundation.nova.common.utils.bindTo
import io.novafoundation.nova.common.utils.setCompoundDrawableTint
import io.novafoundation.nova.common.utils.setTextColorRes
import io.novafoundation.nova.common.view.bottomSheet.action.observeActionBottomSheet
import io.novafoundation.nova.common.view.setState
import io.novafoundation.nova.feature_account_api.di.AccountFeatureApi
import io.novafoundation.nova.feature_account_impl.R
import io.novafoundation.nova.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordConfirmInput
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordContinue
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordInput
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordLetters
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordMinChars
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordNumbers
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordPasswordsMatch
import kotlinx.android.synthetic.main.fragment_create_cloud_backup_password.createCloudBackupPasswordToolbar

class CreateCloudBackupPasswordFragment : BaseFragment<CreateCloudBackupPasswordViewModel>() {

    companion object {
        private const val KEY_PAYLOAD = "cloud_backup_password_payload"

        fun getBundle(payload: CreateCloudBackupPasswordPayload): Bundle {
            return Bundle().apply {
                putParcelable(KEY_PAYLOAD, payload)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_cloud_backup_password, container, false)
    }

    override fun initViews() {
        createCloudBackupPasswordToolbar.applyStatusBarInsets()
        createCloudBackupPasswordToolbar.setHomeButtonListener { viewModel.backClicked() }

        createCloudBackupPasswordContinue.setOnClickListener { viewModel.continueClicked() }
        createCloudBackupPasswordInput.setEndIconOnClickListener { viewModel.toggleShowPassword() }
        createCloudBackupPasswordConfirmInput.setEndIconOnClickListener { viewModel.toggleShowPassword() }
        createCloudBackupPasswordContinue.prepareForProgress(viewLifecycleOwner)
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .createCloudBackupPasswordFactory()
            .create(this, argument(KEY_PAYLOAD))
            .inject(this)
    }

    override fun subscribe(viewModel: CreateCloudBackupPasswordViewModel) {
        observeActionBottomSheet(viewModel)

        createCloudBackupPasswordInput.content.bindTo(viewModel.passwordFlow, lifecycleScope)
        createCloudBackupPasswordConfirmInput.content.bindTo(viewModel.passwordConfirmFlow, lifecycleScope)

        viewModel.passwordStateFlow.observe { state ->
            createCloudBackupPasswordMinChars.requirementState(state.containsMinSymbols)
            createCloudBackupPasswordNumbers.requirementState(state.hasNumbers)
            createCloudBackupPasswordLetters.requirementState(state.hasLetters)
            createCloudBackupPasswordPasswordsMatch.requirementState(state.passwordsMatch)
        }

        viewModel.continueButtonState.observe { state ->
            createCloudBackupPasswordContinue.setState(state)
        }

        viewModel.showPasswords.observe {
            createCloudBackupPasswordInput.content.passwordInputType(it)
            createCloudBackupPasswordConfirmInput.content.passwordInputType(it)
        }
    }

    private fun TextView.requirementState(isValid: Boolean) {
        if (isValid) {
            setTextColorRes(R.color.text_positive)
            setCompoundDrawableTint(R.color.icon_positive)
        } else {
            setTextColorRes(R.color.text_secondary)
            setCompoundDrawableTint(R.color.icon_secondary)
        }
    }

    private fun EditText.passwordInputType(isPasswordVisible: Boolean) {
        val selection = selectionEnd
        inputType = if (isPasswordVisible) {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        setSelection(selection)
    }
}
