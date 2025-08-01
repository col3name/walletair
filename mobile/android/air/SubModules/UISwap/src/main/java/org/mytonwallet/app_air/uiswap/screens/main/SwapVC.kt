package org.mytonwallet.app_air.uiswap.screens.main

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import org.mytonwallet.app_air.uicomponents.base.WNavigationBar
import org.mytonwallet.app_air.uicomponents.base.WViewControllerWithModelStore
import org.mytonwallet.app_air.uicomponents.base.showAlert
import org.mytonwallet.app_air.uicomponents.drawable.SeparatorBackgroundDrawable
import org.mytonwallet.app_air.uicomponents.extensions.collectFlow
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.extensions.setConstraints
import org.mytonwallet.app_air.uicomponents.extensions.setTextIfDiffer
import org.mytonwallet.app_air.uicomponents.helpers.DieselAuthorizationHelpers
import org.mytonwallet.app_air.uicomponents.viewControllers.selector.TokenSelectorVC
import org.mytonwallet.app_air.uicomponents.widgets.ExpandableFrameLayout
import org.mytonwallet.app_air.uicomponents.widgets.WBaseView
import org.mytonwallet.app_air.uicomponents.widgets.WButton
import org.mytonwallet.app_air.uicomponents.widgets.dialog.WDialog
import org.mytonwallet.app_air.uicomponents.widgets.hideKeyboard
import org.mytonwallet.app_air.uicomponents.widgets.setBackgroundColor
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.PasscodeConfirmVC
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.PasscodeViewState
import org.mytonwallet.app_air.uisend.sent.SentVC
import org.mytonwallet.app_air.uiswap.screens.cex.SwapSendAddressOutputVC
import org.mytonwallet.app_air.uiswap.screens.cex.receiveAddressInput.SwapReceiveAddressInputVC
import org.mytonwallet.app_air.uiswap.screens.main.views.SwapAssetInputView
import org.mytonwallet.app_air.uiswap.screens.main.views.SwapChangellyView
import org.mytonwallet.app_air.uiswap.screens.main.views.SwapEstimatedHeader
import org.mytonwallet.app_air.uiswap.screens.main.views.SwapEstimatedInfo
import org.mytonwallet.app_air.uiswap.screens.main.views.SwapSwapAssetsButton
import org.mytonwallet.app_air.uiswap.screens.main.views.dexAggregatorDialog.DexAggregatorDialog
import org.mytonwallet.app_air.uiswap.views.SwapConfirmView
import org.mytonwallet.app_air.walletcontext.R
import org.mytonwallet.app_air.walletcontext.helpers.LocaleController
import org.mytonwallet.app_air.walletcontext.theme.ThemeManager
import org.mytonwallet.app_air.walletcontext.theme.ViewConstants
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import org.mytonwallet.app_air.walletcore.moshi.MApiSwapAsset
import kotlin.math.max

@SuppressLint("ViewConstructor")
class SwapVC(
    context: Context,
    defaultSendingToken: MApiSwapAsset? = null,
    defaultReceivingToken: MApiSwapAsset? = null,
    amountIn: Double? = null
) :
    WViewControllerWithModelStore(context) {

    private val swapViewModel by lazy { ViewModelProvider(this)[SwapViewModel::class.java] }

    private val separatorBackgroundDrawable: SeparatorBackgroundDrawable by lazy {
        SeparatorBackgroundDrawable().apply {
            backgroundWColor = WColor.Background
        }
    }

    private val scrollView = ScrollView(context).apply {
        id = View.generateViewId()
        overScrollMode = ScrollView.OVER_SCROLL_ALWAYS
    }
    private val contentLayout = FrameLayout(context)
    private val linearLayout = LinearLayout(context)

    private val topGapView = WBaseView(context)
    private val swapAssetsButton = SwapSwapAssetsButton(context)
    private val sendAmount = SwapAssetInputView(context)
    private val receiveAmount = SwapAssetInputView(context)
    private val middleGapView = View(context)

    private val continueButton = WButton(context)

    private val changellyView = SwapChangellyView(context)

    private val estOuterContainer = ConstraintLayout(context)
    private val estShowMoreContainer = ExpandableFrameLayout(context).apply {
        id = View.generateViewId()
        expanded = true
    }
    private val estShowMore = SwapEstimatedHeader(context)
    private val estLayout = SwapEstimatedInfo(context, onDexPopupPressed = {
        swapViewModel.getLastResponse()?.let { res ->
            res.dex?.let { dex ->
                if ((dex.all?.size ?: 0) < 2)
                    return@SwapEstimatedInfo
                lateinit var dialogRef: WDialog
                dialogRef = DexAggregatorDialog.create(
                    context,
                    res.request.tokenToReceive,
                    res.request.tokenToSend,
                    dex.all ?: emptyList(),
                    dex.bestDexLabel ?: dex.dexLabel,
                    res.dex.dexLabel,
                    onSelect = {
                        swapViewModel.setDex(if (it == dex.bestDexLabel) null else it)
                        dialogRef.dismiss()
                    }
                )
                dialogRef.presentOn(this)
            }
        }
    }, onSlippageChange = {
        swapViewModel.setSlippage(it)
    }).apply {
        id = View.generateViewId()
    }

    private var ignoreTextChanges = false

    private val sendAmountTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (!ignoreTextChanges) {
                swapViewModel.onTokenToSendAmountInput(s?.toString() ?: "")
            }
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    private val receiveAmountTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (!ignoreTextChanges) {
                swapViewModel.onTokenToReceiveAmountInput(s?.toString() ?: "")
            }
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    init {
        if (defaultSendingToken != null)
            swapViewModel.setTokenToSend(defaultSendingToken)
        if (defaultReceivingToken != null)
            swapViewModel.setTokenToReceive(defaultReceivingToken)
        if (amountIn != null)
            swapViewModel.setAmount(amountIn)
    }

    override fun setupViews() {
        super.setupViews()

        setNavTitle(LocaleController.getString(R.string.Home_Swap))
        setupNavBar(true)
        navigationBar?.addCloseButton()

        view.addView(scrollView, ViewGroup.LayoutParams(MATCH_PARENT, 0))

        continueButton.id = View.generateViewId()
        view.addView(continueButton, ViewGroup.LayoutParams(MATCH_PARENT, 50.dp))

        scrollView.addView(contentLayout, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        linearLayout.orientation = LinearLayout.VERTICAL
        contentLayout.addView(linearLayout, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val topSpace = (navigationController?.getSystemBars()?.top ?: 0) +
            WNavigationBar.DEFAULT_HEIGHT.dp

        contentLayout.addView(
            swapAssetsButton,
            FrameLayout.LayoutParams(32.dp, 32.dp).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = 80.dp + topSpace
            })

        sendAmount.setMode(SwapAssetInputView.Mode.SELL)

        linearLayout.addView(
            topGapView, ViewGroup.LayoutParams(MATCH_PARENT, topSpace)
        )
        linearLayout.addView(sendAmount)
        receiveAmount.setMode(SwapAssetInputView.Mode.BUY)
        linearLayout.addView(receiveAmount)
        linearLayout.addView(
            middleGapView,
            ViewGroup.LayoutParams(MATCH_PARENT, ViewConstants.GAP.dp)
        )

        linearLayout.addView(changellyView)
        estShowMoreContainer.addView(estShowMore)
        estOuterContainer.addView(estLayout)
        estOuterContainer.addView(
            estShowMoreContainer,
            ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        )
        estOuterContainer.setConstraints {
            toTop(estShowMoreContainer)
            topToBottom(estLayout, estShowMoreContainer, -8f)
            toBottom(estLayout)
        }
        linearLayout.addView(estOuterContainer)

        view.setConstraints {
            toCenterX(scrollView)
            toTop(scrollView)
            bottomToTop(scrollView, continueButton, 20f)
            toCenterX(continueButton, 20f)
            toBottomPx(
                continueButton, 20.dp + max(
                    (navigationController?.getSystemBars()?.bottom ?: 0),
                    (window?.imeInsets?.bottom ?: 0)
                )
            )
        }

        updateTheme()

        estLayout.onDialogShowListener = { title, info ->
            showAlert(title, info, LocaleController.getString(R.string.Swap_Est_Info_Close))
        }

        estShowMore.setOnClickListener {
            estLayout.expanded = !estLayout.expanded
            estShowMore.isExpanded.animatedValue = estLayout.expanded
        }

        continueButton.setOnClickListener {
            if (swapViewModel.shouldAuthorizeDiesel) {
                DieselAuthorizationHelpers.authorizeDiesel(context)
                return@setOnClickListener
            }
            swapViewModel.openSwapConfirmation(null)
        }

        swapAssetsButton.setOnClickListener {
            val send = sendAmount.amountEditText.text.toString()
            val receive = receiveAmount.amountEditText.text.toString()

            ignoreTextChanges = true
            sendAmount.amountEditText.text?.replace(0, send.length, receive)
            receiveAmount.amountEditText.text?.replace(0, receive.length, send)
            ignoreTextChanges = false

            swapViewModel.swapTokens()
        }
        sendAmount.assetView.setOnClickListener { swapViewModel.openTokenToSendSelector() }
        sendAmount.setOnMaxBalanceClickListener { swapViewModel.tokenToSendSetMaxAmount() }
        sendAmount.amountEditText.addTextChangedListener(sendAmountTextWatcher)

        receiveAmount.assetView.setOnClickListener { swapViewModel.openTokenToReceiveSelector() }
        receiveAmount.amountEditText.addTextChangedListener(receiveAmountTextWatcher)

        collectFlow(swapViewModel.uiInputStateFlow) {
            changellyView.expanded = it.isCex
            sendAmount.setAsset(it.tokenToSend)
            sendAmount.setBalance(it.tokenToSendMaxAmount)
            receiveAmount.setAsset(it.tokenToReceive)
            if (it.isCex && receiveAmount.amountEditText.isFocused) {
                sendAmount.amountEditText.requestFocus()
            }

            sendAmount.amountEditText.isEnabled = it.tokenToSend != null
            receiveAmount.amountEditText.isEnabled = it.tokenToReceive != null && !it.isCex
            when (it.swapDetailsVisibility) {
                SwapViewModel.SwapDetailsVisibility.VISIBLE -> {
                    estShowMoreContainer.expanded = true
                    estLayout.setIsCex(false)
                }

                SwapViewModel.SwapDetailsVisibility.CEX -> {
                    estShowMoreContainer.expanded = true
                    estLayout.setIsCex(true)
                }

                SwapViewModel.SwapDetailsVisibility.GONE -> {
                    estShowMoreContainer.expanded = false
                    estShowMore.isExpanded.animatedValue = false
                    estLayout.expanded = false
                }
            }

            val editView = if (it.reverse) receiveAmount else sendAmount

            ignoreTextChanges = true
            editView.amountEditText.setTextIfDiffer(it.amountInput, selectionToEnd = false)
            ignoreTextChanges = false
        }

        collectFlow(swapViewModel.simulatedSwapFlow) { est ->
            estLayout.setEstimated(est)
            ignoreTextChanges = true
            est?.let { estimate ->
                if (!estimate.request.reverse) {
                    if (estimate.request.isFromAmountMax) {
                        if (estimate.fromAmountDecimalStr != null) {
                            sendAmount.amountEditText.setTextIfDiffer(
                                estimate.fromAmountDecimalStr,
                                selectionToEnd = false
                            )
                            sendAmount.setBalance(swapViewModel.tokenToSendMaxAmount)
                        }
                    }
                    receiveAmount.amountEditText.setTextIfDiffer(
                        estimate.toAmountDecimalStr,
                        selectionToEnd = false
                    )
                } else {
                    sendAmount.amountEditText.setTextIfDiffer(
                        estimate.fromAmountDecimalStr,
                        selectionToEnd = false
                    )
                }
            } ?: run {
                if (!swapViewModel.isReverse()) {
                    receiveAmount.amountEditText.text?.clear()
                } else {
                    sendAmount.amountEditText.text?.clear()
                }
            }
            ignoreTextChanges = false
        }

        collectFlow(swapViewModel.uiStatusFlow) {
            continueButton.isLoading = it.button.status.isLoading
            if (!it.button.status.isLoading) {
                continueButton.isEnabled = it.button.status.isEnabled
                continueButton.isError = it.button.status.isError
                continueButton.text = it.button.title
            }

            receiveAmount.amountEditText.isLoading.animatedValue = it.tokenToReceive.isLoading
                && (receiveAmount.amountEditText.text?.isNotEmpty() == true)
            receiveAmount.amountEditText.isError.animatedValue = it.tokenToReceive.isError

            sendAmount.amountEditText.isLoading.animatedValue = it.tokenToSend.isLoading
                && (sendAmount.amountEditText.text?.isNotEmpty() == true)
            sendAmount.amountEditText.isError.animatedValue = it.tokenToSend.isError
        }

        collectFlow(swapViewModel.eventsFlow, this::onEvent)
    }

    override fun updateTheme() {
        super.updateTheme()
        view.setBackgroundColor(WColor.SecondaryBackground.color)
        estShowMoreContainer.setBackgroundColor(WColor.Background.color)
        if (ThemeManager.uiMode.hasRoundedCorners) {
            estOuterContainer.setBackgroundColor(
                WColor.Background.color,
                ViewConstants.BIG_RADIUS.dp,
                true
            )
        } else {
            estOuterContainer.background = separatorBackgroundDrawable
            separatorBackgroundDrawable.invalidateSelf()
        }
    }

    private fun onEvent(event: SwapViewModel.Event) {
        when (event) {
            is SwapViewModel.Event.ShowSelector -> {
                view.hideKeyboard()
                val titleToShow = when (event.mode) {
                    SwapViewModel.Mode.RECEIVE -> LocaleController.getString(R.string.Swap_Buy)
                    SwapViewModel.Mode.SEND -> LocaleController.getString(R.string.Swap_Sell)
                }
                push(
                    TokenSelectorVC(
                        context, titleToShow, event.assets
                    ).apply {
                        setOnAssetSelectListener { asset ->
                            if (event.mode == SwapViewModel.Mode.SEND) {
                                swapViewModel.setTokenToSend(asset)
                            } else {
                                swapViewModel.setTokenToReceive(asset)
                            }
                        }
                    })
            }

            is SwapViewModel.Event.ShowAddressToReceiveInput -> {
                val request = event.request
                view.hideKeyboard()
                push(
                    SwapReceiveAddressInputVC(
                        context,
                        estimate = request,
                        callback = { address ->
                            swapViewModel.openSwapConfirmation(address)
                        }
                    ))
            }

            is SwapViewModel.Event.ShowAddressToSend -> {
                view.hideKeyboard()
                push(
                    SwapSendAddressOutputVC(
                        context,
                        event.estimate.request.tokenToSend,
                        event.estimate.request.tokenToReceive,
                        event.estimate.fromAmount,
                        event.estimate.toAmount,
                        event.cex.payinAddress,
                        event.cex.transactionId
                    )
                )
            }

            is SwapViewModel.Event.ShowConfirm -> {
                val request = event.request
                view.hideKeyboard()
                val confirmActionVC = PasscodeConfirmVC(
                    context,
                    PasscodeViewState.CustomHeader(
                        SwapConfirmView(context).apply {
                            config(
                                request.request.tokenToSend,
                                request.request.tokenToReceive,
                                request.fromAmount,
                                request.toAmount
                            )
                        },
                        LocaleController.getString(R.string.Swap_Confirm_Title)
                    ), task = { passcode ->
                        swapViewModel.doSend(passcode, request, event.addressToReceive)
                    })
                push(confirmActionVC)
            }

            is SwapViewModel.Event.SwapComplete -> {
                val success = event.success
                if (success) {
                    push(
                        SentVC(
                            context, LocaleController.getString(R.string.Home_Swap),
                            LocaleController.getString(R.string.Home_Swap_Completed),
                            null,
                            null
                        ), onCompletion = {
                            navigationController?.removePrevViewControllers()
                        }
                    )
                } else {
                    pop()
                    showError(event.error?.parsed)
                }
            }
        }
    }

    override fun insetsUpdated() {
        super.insetsUpdated()
        scrollView.setPadding(
            ViewConstants.HORIZONTAL_PADDINGS.dp,
            0,
            ViewConstants.HORIZONTAL_PADDINGS.dp,
            0
        )
        view.setConstraints {
            toBottomPx(
                continueButton, 20.dp + max(
                    (navigationController?.getSystemBars()?.bottom ?: 0),
                    (window?.imeInsets?.bottom ?: 0)
                )
            )
        }
    }

}
