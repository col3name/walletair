package org.mytonwallet.app_air.uisend.sendNft

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.appcompat.widget.AppCompatEditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.widget.doOnTextChanged
import org.mytonwallet.app_air.ledger.screens.ledgerConnect.LedgerConnectVC
import org.mytonwallet.app_air.sqscan.screen.QrScannerDialog
import org.mytonwallet.app_air.uicomponents.adapter.implementation.holders.ListIconDualLineCell
import org.mytonwallet.app_air.uicomponents.adapter.implementation.holders.ListTitleCell
import org.mytonwallet.app_air.uicomponents.base.WViewController
import org.mytonwallet.app_air.uicomponents.drawable.SeparatorBackgroundDrawable
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.extensions.setPaddingDp
import org.mytonwallet.app_air.uicomponents.extensions.updateDotsTypeface
import org.mytonwallet.app_air.uicomponents.helpers.AddressPopupHelpers
import org.mytonwallet.app_air.uicomponents.helpers.WFont
import org.mytonwallet.app_air.uicomponents.helpers.spans.WForegroundColorSpan
import org.mytonwallet.app_air.uicomponents.helpers.typeface
import org.mytonwallet.app_air.uicomponents.image.Content
import org.mytonwallet.app_air.uicomponents.widgets.WButton
import org.mytonwallet.app_air.uicomponents.widgets.WScrollView
import org.mytonwallet.app_air.uicomponents.widgets.WView
import org.mytonwallet.app_air.uicomponents.widgets.passcode.headers.PasscodeHeaderSendView
import org.mytonwallet.app_air.uicomponents.widgets.setBackgroundColor
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.PasscodeConfirmVC
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.PasscodeViewState
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.views.PasscodeScreenView
import org.mytonwallet.app_air.uisend.send.lauouts.AddressInputLayout
import org.mytonwallet.app_air.uisend.sent.SentVC
import org.mytonwallet.app_air.walletcontext.R
import org.mytonwallet.app_air.walletcontext.helpers.LocaleController
import org.mytonwallet.app_air.walletcontext.theme.ThemeManager
import org.mytonwallet.app_air.walletcontext.theme.ViewConstants
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import org.mytonwallet.app_air.walletcontext.utils.formatStartEndAddress
import org.mytonwallet.app_air.walletcore.TONCOIN_SLUG
import org.mytonwallet.app_air.walletcore.deeplink.Deeplink
import org.mytonwallet.app_air.walletcore.deeplink.DeeplinkParser
import org.mytonwallet.app_air.walletcore.models.MBridgeError
import org.mytonwallet.app_air.walletcore.moshi.ApiNft
import org.mytonwallet.app_air.walletcore.moshi.MApiCheckTransactionDraftResult
import org.mytonwallet.app_air.walletcore.stores.AccountStore
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class SendNftVC(
    context: Context,
    val nft: ApiNft,
) : WViewController(context), SendNftVM.Delegate {

    private val viewModel = SendNftVM(this, nft)

    private val separatorBackgroundDrawable = SeparatorBackgroundDrawable().apply {
        backgroundWColor = WColor.Background
    }

    private val title1 = ListTitleCell(context).apply {
        id = View.generateViewId()
        text = LocaleController.getString(R.string.SendTo_SendTo)
    }

    private val addressInputView by lazy {
        AddressInputLayout(context).apply {
            id = View.generateViewId()
        }
    }

    private val title2 = ListTitleCell(context).apply {
        id = View.generateViewId()
        text = LocaleController.getString(R.string.SendNft_1Asset)
    }

    private val nftView by lazy {
        ListIconDualLineCell(context).apply {
            id = View.generateViewId()
            configure(Content.ofUrl(nft.image ?: ""), nft.name, nft.collectionName, false, 12f.dp)
        }
    }

    private val title3 = ListTitleCell(context).apply {
        id = View.generateViewId()
        text = LocaleController.getString(R.string.SendTo_CommentOrMemo)
    }

    private val commentInputView by lazy {
        AppCompatEditText(context).apply {
            id = View.generateViewId()
            background = null
            hint = LocaleController.getString(R.string.SendTo_AddMessageIfNeeded)
            typeface = WFont.Regular.typeface
            layoutParams =
                ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPaddingDp(20, 8, 20, 20)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setLineHeight(TypedValue.COMPLEX_UNIT_SP, 24f)
            }
        }
    }
    private val contentLayout by lazy {
        WView(context).apply {
            setPadding(
                ViewConstants.HORIZONTAL_PADDINGS.dp,
                0,
                ViewConstants.HORIZONTAL_PADDINGS.dp,
                0
            )
            addView(title1)
            addView(
                addressInputView,
                ConstraintLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
            addView(
                title2,
                ConstraintLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
            addView(
                nftView,
                ConstraintLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
            addView(title3)
            addView(
                commentInputView,
                ConstraintLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
            setConstraints {
                toTop(title1)
                topToBottom(addressInputView, title1)
                topToBottom(title2, addressInputView, ViewConstants.GAP.toFloat())
                topToBottom(nftView, title2)
                topToBottom(title3, nftView, ViewConstants.GAP.toFloat())
                topToBottom(commentInputView, title3)
                toBottom(commentInputView)
            }
        }
    }

    private val scrollView by lazy {
        WScrollView(WeakReference(this)).apply {
            addView(
                contentLayout,
                ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
            id = View.generateViewId()
        }
    }

    private val continueButton by lazy {
        WButton(context).apply {
            id = View.generateViewId()
        }.apply {
            isEnabled = false
            text = LocaleController.getString(R.string.SendTo_AddressOrDomain)
        }
    }

    override fun setupViews() {
        super.setupViews()
        setNavTitle(LocaleController.getString(R.string.Home_Send))
        setupNavBar(true)

        view.addView(scrollView, ViewGroup.LayoutParams(MATCH_PARENT, 0))
        view.addView(continueButton, ViewGroup.LayoutParams(MATCH_PARENT, 50.dp))
        view.setConstraints {
            toCenterX(scrollView)
            topToBottom(scrollView, navigationBar!!)
            bottomToTop(scrollView, continueButton, 20f)
            toCenterX(continueButton, 20f)
            toBottomPx(
                continueButton, 20.dp + max(
                    (navigationController?.getSystemBars()?.bottom ?: 0),
                    (window?.imeInsets?.bottom ?: 0)
                )
            )
        }

        continueButton.setOnClickListener {
            val address = viewModel.resolvedAddress?.formatStartEndAddress() ?: ""
            val sendingToString = LocaleController.getString(R.string.SendConfirm_SendingTo)
            val startOffset = continueButton.measureText(sendingToString)
            val addressAttr =
                SpannableStringBuilder(sendingToString).apply {
                    append(" $address")
                    AddressPopupHelpers.configSpannableAddress(
                        context,
                        this,
                        length - address.length,
                        address.length,
                        TONCOIN_SLUG,
                        viewModel.resolvedAddress!!,
                        startOffset.roundToInt()
                    )
                    updateDotsTypeface()
                    setSpan(
                        WForegroundColorSpan(WColor.SecondaryText),
                        length - address.length - 1,
                        length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            val headerView = PasscodeHeaderSendView(
                context,
                (view.height * PasscodeScreenView.TOP_HEADER_MAX_HEIGHT_RATIO).roundToInt()
            ).apply {
                config(
                    Content.ofUrl(nft.image ?: ""),
                    nft.name ?: "",
                    addressAttr,
                    Content.Rounding.Radius(12f.dp)
                )
            }
            if (AccountStore.activeAccount?.isHardware == true) {
                val account = AccountStore.activeAccount!!
                val ledger = account.ledger!!
                push(
                    LedgerConnectVC(
                        context,
                        LedgerConnectVC.Mode.ConnectToSubmitTransfer(
                            ledger.index,
                            account.tonAddress!!,
                            viewModel.signNftTransferData()
                        ) {
                            push(
                                SentVC(
                                    context,
                                    LocaleController.getString(R.string.SendComplete_Title),
                                    nft.name ?: "",
                                    SpannableStringBuilder("${LocaleController.getString(R.string.SendNft_SentTo)} $address").apply {
                                        updateDotsTypeface()
                                    },
                                    null
                                ),
                                onCompletion = {
                                    navigationController?.removePrevViewControllers(
                                        keptFirstViewControllers = 1
                                    )
                                })
                        },
                        headerView = headerView
                    )
                )
            } else {
                push(
                    PasscodeConfirmVC(
                        context,
                        PasscodeViewState.CustomHeader(
                            headerView,
                            LocaleController.getString(R.string.DApp_Send_Confirm)
                        ),
                        task = { passcode ->
                            viewModel.submitTransferNft(
                                nft,
                                passcode
                            ) {
                                push(
                                    SentVC(
                                        context,
                                        LocaleController.getString(R.string.SendComplete_Title),
                                        nft.name ?: "",
                                        SpannableStringBuilder("${LocaleController.getString(R.string.SendNft_SentTo)} $address").apply {
                                            updateDotsTypeface()
                                        },
                                        null
                                    ),
                                    onCompletion = {
                                        navigationController?.removePrevViewControllers(
                                            keptFirstViewControllers = 1
                                        )
                                    })
                            }
                        }
                    ))
            }
        }

        addressInputView.editTextView.doOnTextChanged { text, _, _, _ ->
            viewModel.inputChanged(address = text.toString())
        }
        addressInputView.qrScanImageView.setOnClickListener {
            QrScannerDialog.build(context) {
                val deeplink = DeeplinkParser.parse(it.toUri())
                addressInputView.editTextView.setText(
                    if (deeplink is Deeplink.Invoice) deeplink.address else it
                )
            }.show()
        }

        commentInputView.doOnTextChanged { text, _, _, _ ->
            viewModel.inputChanged(comment = text.toString())
        }

        updateTheme()
    }

    override fun updateTheme() {
        super.updateTheme()
        view.setBackgroundColor(WColor.SecondaryBackground.color)
        title1.setBackgroundColor(WColor.Background.color, ViewConstants.TOP_RADIUS.dp, 0f)
        title2.setBackgroundColor(
            WColor.Background.color,
            ViewConstants.BIG_RADIUS.dp,
            0f,
        )
        title3.setBackgroundColor(
            WColor.Background.color,
            ViewConstants.BIG_RADIUS.dp,
            0f,
        )
        if (ThemeManager.uiMode.hasRoundedCorners) {
            addressInputView.setBackgroundColor(
                WColor.Background.color,
                0f,
                ViewConstants.BIG_RADIUS.dp
            )
            nftView.setBackgroundColor(
                WColor.Background.color,
                0f,
                ViewConstants.BIG_RADIUS.dp
            )
            commentInputView.setBackgroundColor(
                WColor.Background.color,
                0f,
                ViewConstants.BIG_RADIUS.dp
            )
        } else {
            addressInputView.background = separatorBackgroundDrawable
            nftView.background = separatorBackgroundDrawable
            commentInputView.background = separatorBackgroundDrawable
            separatorBackgroundDrawable.invalidateSelf()
        }
        commentInputView.setTextColor(WColor.PrimaryText.color)
        commentInputView.setHintTextColor(WColor.SecondaryText.color)
    }

    override fun insetsUpdated() {
        super.insetsUpdated()
        view.setConstraints {
            toBottomPx(
                continueButton, 20.dp + max(
                    (navigationController?.getSystemBars()?.bottom ?: 0),
                    (window?.imeInsets?.bottom ?: 0)
                )
            )
        }
    }

    override fun feeUpdated(result: MApiCheckTransactionDraftResult?, err: MBridgeError?) {
        if (result == null && err == null) {
            continueButton.isLoading = true
            return
        }
        /*val ton = TokenStore.getToken(TONCOIN_SLUG)
        ton?.let {
            result?.fee?.let { fee ->
                feeView.setTitleAndValue(
                    LocaleController.getString(R.string.SendNft_Fee),
                    fee.toString(
                        decimals = ton.decimals,
                        currency = ton.symbol,
                        currencyDecimals = ton.decimals,
                        showPositiveSign = false
                    )
                )
            }
        }*/
        continueButton.isLoading = false
        continueButton.isEnabled = err == null
        continueButton.text = err?.toLocalized ?: title
    }

}
