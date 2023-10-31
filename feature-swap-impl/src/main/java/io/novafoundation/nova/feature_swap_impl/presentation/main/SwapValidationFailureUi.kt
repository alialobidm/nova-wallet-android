package io.novafoundation.nova.feature_swap_impl.presentation.main

import io.novafoundation.nova.common.base.TitleAndMessage
import io.novafoundation.nova.common.mixin.api.CustomDialogDisplayer
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.common.utils.sendEvent
import io.novafoundation.nova.common.validation.TransformedFailure
import io.novafoundation.nova.common.validation.ValidationFlowActions
import io.novafoundation.nova.common.validation.ValidationStatus
import io.novafoundation.nova.common.validation.asDefault
import io.novafoundation.nova.feature_swap_api.domain.model.SwapFee
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.TooSmallRemainingBalance
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.FeeChangeDetected
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.InsufficientBalance
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.AmountOutIsTooLowToStayAboveED
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.NotEnoughLiquidity
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.NonPositiveAmount
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.NewRateExceededSlippage
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.InvalidSlippage
import io.novafoundation.nova.feature_swap_impl.domain.validation.SwapValidationFailure.NotEnoughFunds
import io.novafoundation.nova.feature_swap_impl.presentation.main.input.SwapAmountInputMixin
import io.novafoundation.nova.feature_wallet_api.R
import io.novafoundation.nova.feature_wallet_api.domain.model.amountFromPlanks
import io.novafoundation.nova.feature_wallet_api.domain.validation.amountIsTooBig
import io.novafoundation.nova.feature_wallet_api.domain.validation.handleFeeSpikeDetected
import io.novafoundation.nova.feature_wallet_api.domain.validation.handleNotEnoughFeeError
import io.novafoundation.nova.feature_wallet_api.presentation.formatters.formatPlanks
import io.novafoundation.nova.feature_wallet_api.presentation.formatters.formatTokenAmount
import io.novafoundation.nova.feature_wallet_api.presentation.mixin.amountChooser.invokeMaxClick
import io.novafoundation.nova.feature_wallet_api.presentation.mixin.amountChooser.setAmount
import io.novafoundation.nova.feature_wallet_api.presentation.mixin.fee.GenericFeeLoaderMixin
import io.novafoundation.nova.feature_wallet_api.presentation.validation.handleNonPositiveAmount
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.mapSwapValidationFailureToUI(
    resourceManager: ResourceManager,
    status: ValidationStatus.NotValid<SwapValidationFailure>,
    actions: ValidationFlowActions<*>,
    feeLoaderMixin: GenericFeeLoaderMixin.Presentation<SwapFee>,
    amountInInputMixin: SwapAmountInputMixin.Presentation,
    amountOutInputMixin: SwapAmountInputMixin.Presentation,
): TransformedFailure? {
    return when (val reason = status.reason) {
        NotEnoughFunds.InUsedAsset -> resourceManager.amountIsTooBig().asDefault()

        is NotEnoughFunds.InCommissionAsset -> handleNotEnoughFeeError(reason, resourceManager).asDefault()

        InvalidSlippage -> TitleAndMessage(
            resourceManager.getString(R.string.swap_invalid_slippage_failure_title),
            resourceManager.getString(R.string.swap_invalid_slippage_failure_message)
        ).asDefault()

        is NewRateExceededSlippage -> TitleAndMessage(
            resourceManager.getString(R.string.swap_rate_was_updated_failure_title),
            resourceManager.getString(
                R.string.swap_rate_was_updated_failure_message,
                BigDecimal.ONE.formatTokenAmount(reason.assetIn),
                reason.selectedRate.formatTokenAmount(reason.assetOut),
                reason.newRate.formatTokenAmount(reason.assetOut)
            )
        ).asDefault()

        NonPositiveAmount -> handleNonPositiveAmount(resourceManager).asDefault()

        NotEnoughLiquidity -> TitleAndMessage(resourceManager.getString(R.string.swap_not_enought_liquidity_failure), second = null).asDefault()

        is AmountOutIsTooLowToStayAboveED -> handleErrorToSwapMin(reason, resourceManager, amountOutInputMixin)

        is TooSmallRemainingBalance.NoNeedsToBuyMainAssetED -> handleTooSmallRemainingBalance(
            title = resourceManager.getString(R.string.swap_failure_too_small_remaining_balance_title),
            message = resourceManager.getString(
                R.string.swap_failure_too_small_remaining_balance_message,
                reason.assetInExistentialDeposit.formatPlanks(reason.assetIn),
                reason.remainingBalance.formatPlanks(reason.assetIn)
            ),
            resourceManager = resourceManager,
            actions = actions,
            amountInputMixin = amountInInputMixin
        )

        is TooSmallRemainingBalance.NeedsToBuyMainAssetED -> handleTooSmallRemainingBalance(
            title = resourceManager.getString(R.string.swap_failure_too_small_remaining_balance_title),
            message = resourceManager.getString(
                R.string.swap_failure_too_small_remaining_balance_with_buy_ed_message,
                reason.assetInExistentialDeposit.formatPlanks(reason.assetIn),
                reason.fee.amount.formatPlanks(reason.feeAsset),
                reason.toSellAmountToKeepEDUsingAssetIn.formatPlanks(reason.assetIn),
                reason.toBuyAmountToKeepEDInCommissionAsset.formatPlanks(reason.feeAsset),
                reason.feeAsset.symbol,
                reason.remainingBalance.formatPlanks(reason.assetIn)
            ),
            resourceManager = resourceManager,
            actions = actions,
            amountInputMixin = amountInInputMixin
        )

        is InsufficientBalance.NoNeedsToBuyMainAssetED -> handleInsufficientBalance(
            title = resourceManager.getString(R.string.common_not_enough_funds_title),
            message = resourceManager.getString(
                R.string.swap_failure_insufficient_balance_message,
                reason.maxSwapAmount.formatPlanks(reason.assetIn),
                reason.fee.amount.formatPlanks(reason.feeAsset)
            ),
            resourceManager = resourceManager,
            amountInputMixin = amountInInputMixin
        )

        is InsufficientBalance.NeedsToBuyMainAssetED -> handleInsufficientBalance(
            title = resourceManager.getString(R.string.common_not_enough_funds_title),
            message = resourceManager.getString(
                R.string.swap_failure_insufficient_balance_with_buy_ed_message,
                reason.maxSwapAmount.formatPlanks(reason.assetIn),
                reason.fee.amount.formatPlanks(reason.feeAsset),
                reason.toSellAmountToKeepEDUsingAssetIn.formatPlanks(reason.assetIn),
                reason.toBuyAmountToKeepEDInCommissionAsset.formatPlanks(reason.feeAsset),
                reason.feeAsset.symbol
            ),
            resourceManager = resourceManager,
            amountInputMixin = amountInInputMixin
        )

        is FeeChangeDetected -> handleFeeSpikeDetected(
            error = reason,
            resourceManager = resourceManager,
            actions = actions,
            setFee = { feeLoaderMixin.setFee(it.newFee.genericFee) }
        )
    }
}

fun CoroutineScope.handleInsufficientBalance(
    title: String,
    message: String,
    resourceManager: ResourceManager,
    amountInputMixin: SwapAmountInputMixin.Presentation
): TransformedFailure {
    return handleErrorToSwapMax(
        title = title,
        message = message,
        resourceManager = resourceManager,
        amountInputMixin = amountInputMixin,
        negativeButtonText = resourceManager.getString(R.string.common_cancel),
        clickNegativeButton = { }
    )
}

fun CoroutineScope.handleTooSmallRemainingBalance(
    title: String,
    message: String,
    resourceManager: ResourceManager,
    actions: ValidationFlowActions<*>,
    amountInputMixin: SwapAmountInputMixin.Presentation
): TransformedFailure {
    return handleErrorToSwapMax(
        title = title,
        message = message,
        resourceManager = resourceManager,
        amountInputMixin = amountInputMixin,
        negativeButtonText = resourceManager.getString(R.string.common_proceed),
        clickNegativeButton = { actions.resumeFlow() }
    )
}

fun CoroutineScope.handleErrorToSwapMax(
    title: String,
    message: String,
    resourceManager: ResourceManager,
    amountInputMixin: SwapAmountInputMixin.Presentation,
    negativeButtonText: String,
    clickNegativeButton: () -> Unit
): TransformedFailure {
    return TransformedFailure.Custom(
        CustomDialogDisplayer.Payload(
            title = title,
            message = message,
            customStyle = R.style.AccentAlertDialogTheme,
            okAction = CustomDialogDisplayer.Payload.DialogAction(
                title = resourceManager.getString(R.string.swap_failure_swap_max_button),
                action = {
                    launch {
                        amountInputMixin.invokeMaxClick()
                    }
                }
            ),
            cancelAction = CustomDialogDisplayer.Payload.DialogAction(
                title = negativeButtonText,
                action = clickNegativeButton
            )
        )
    )
}

fun CoroutineScope.handleErrorToSwapMin(
    reason: AmountOutIsTooLowToStayAboveED,
    resourceManager: ResourceManager,
    amountOutInputMixin: SwapAmountInputMixin.Presentation
): TransformedFailure {
    return TransformedFailure.Custom(
        CustomDialogDisplayer.Payload(
            title = resourceManager.getString(R.string.swap_too_low_amount_to_stay_abow_ed_title),
            message = resourceManager.getString(
                R.string.swap_too_low_amount_to_stay_abow_ed_message,
                reason.amountInPlanks.formatPlanks(reason.asset),
                reason.existentialDeposit.formatPlanks(reason.asset),
            ),
            customStyle = R.style.AccentAlertDialogTheme,
            okAction = CustomDialogDisplayer.Payload.DialogAction(
                title = resourceManager.getString(R.string.swap_failure_swap_min_button),
                action = {
                    launch {
                        amountOutInputMixin.requestFocusLiveData.sendEvent()
                        val existentialDepositAmount = reason.asset.amountFromPlanks(reason.existentialDeposit)
                        amountOutInputMixin.setAmount(existentialDepositAmount)
                    }
                }
            ),
            cancelAction = CustomDialogDisplayer.Payload.DialogAction(
                title = resourceManager.getString(R.string.common_cancel),
                action = { }
            )
        )
    )
}
