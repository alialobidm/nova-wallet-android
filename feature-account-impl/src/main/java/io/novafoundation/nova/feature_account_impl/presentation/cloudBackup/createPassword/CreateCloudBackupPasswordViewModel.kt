package io.novafoundation.nova.feature_account_impl.presentation.cloudBackup.createPassword

import io.novafoundation.nova.common.base.BaseViewModel
import io.novafoundation.nova.common.base.showError
import io.novafoundation.nova.common.presentation.DescriptiveButtonState
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.common.utils.addColor
import io.novafoundation.nova.common.utils.formatting.spannable.spannableFormatting
import io.novafoundation.nova.common.utils.toggle
import io.novafoundation.nova.common.view.bottomSheet.action.ActionBottomSheet
import io.novafoundation.nova.common.view.bottomSheet.action.ActionBottomSheetLauncher
import io.novafoundation.nova.common.view.bottomSheet.action.primary
import io.novafoundation.nova.feature_account_impl.R
import io.novafoundation.nova.feature_account_impl.domain.cloudBackup.createPassword.CreateCloudBackupPasswordInteractor
import io.novafoundation.nova.feature_account_impl.domain.cloudBackup.createPassword.model.PasswordErrors
import io.novafoundation.nova.feature_account_impl.presentation.AccountRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PasswordInputState(
    val containsMinSymbols: Boolean,
    val hasLetters: Boolean,
    val hasNumbers: Boolean,
    val passwordsMatch: Boolean
) {

    val isRequirementsSatisfied = containsMinSymbols && hasLetters && hasNumbers && passwordsMatch
}

class CreateCloudBackupPasswordViewModel(
    private val router: AccountRouter,
    private val resourceManager: ResourceManager,
    private val interactor: CreateCloudBackupPasswordInteractor,
    private val actionBottomSheetLauncher: ActionBottomSheetLauncher,
    private val payload: CreateCloudBackupPasswordPayload
) : BaseViewModel(), ActionBottomSheetLauncher by actionBottomSheetLauncher {

    val passwordFlow = MutableStateFlow("")
    val passwordConfirmFlow = MutableStateFlow("")

    val _showPasswords = MutableStateFlow(false)

    val showPasswords: Flow<Boolean> = _showPasswords

    val passwordStateFlow = combine(passwordFlow, passwordConfirmFlow) { password, confirm ->
        val passwordErrors = interactor.checkPasswords(password, confirm)
        PasswordInputState(
            containsMinSymbols = PasswordErrors.TOO_SHORT !in passwordErrors,
            hasLetters = PasswordErrors.NO_LETTERS !in passwordErrors,
            hasNumbers = PasswordErrors.NO_DIGITS !in passwordErrors,
            passwordsMatch = PasswordErrors.PASSWORDS_DO_NOT_MATCH !in passwordErrors
        )
    }.shareInBackground()

    private val _backupInProgress = MutableStateFlow(false)
    private val backupInProgress: Flow<Boolean> = _backupInProgress

    val continueButtonState = combine(passwordStateFlow, backupInProgress) { passwordState, backupInProgress ->
        when {
            backupInProgress && passwordState.isRequirementsSatisfied -> DescriptiveButtonState.Loading
            passwordState.isRequirementsSatisfied -> DescriptiveButtonState.Enabled(resourceManager.getString(R.string.common_continue))
            else -> DescriptiveButtonState.Disabled(resourceManager.getString(R.string.create_cloud_backup_password_requirement_enter_password))
        }
    }.shareInBackground()

    init {
        showPasswordWarningDialog()
    }

    private fun showPasswordWarningDialog() {
        actionBottomSheetLauncher.launchBottomSheet(
            imageRes = R.drawable.ic_cloud_backup_lock,
            title = resourceManager.getString(R.string.create_cloud_backup_password_alert_title),
            subtitle = with(resourceManager) {
                val highlightedPart = getString(R.string.create_cloud_backup_password_alert_subtitle_highlighted)
                    .addColor(getColor(R.color.text_primary))

                getString(R.string.create_cloud_backup_password_alert_subtitle).spannableFormatting(highlightedPart)
            },
            actionButtonPreferences = ActionBottomSheet.ButtonPreferences.primary(resourceManager.getString(R.string.common_got_it))
        )
    }

    fun backClicked() {
        router.back()
    }

    fun continueClicked() {
        launch {
            _backupInProgress.value = true
            val password = passwordFlow.value
            interactor.createAndBackupAccount(payload.walletName, password)
                .onSuccess {
                    router.openCreatePincode()
                }.onFailure { throwable ->
                    val titleAndMessage = mapWriteBackupFailureToUi(resourceManager, throwable)
                    titleAndMessage?.let { showError(it) }
                }

            _backupInProgress.value = false
        }
    }

    fun toggleShowPassword() {
        _showPasswords.toggle()
    }
}
