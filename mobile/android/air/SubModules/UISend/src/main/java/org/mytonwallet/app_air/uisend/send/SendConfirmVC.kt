package org.mytonwallet.app_air.uisend.send

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintSet
import org.mytonwallet.app_air.ledger.screens.ledgerConnect.LedgerConnectVC
import org.mytonwallet.app_air.uicomponents.adapter.implementation.holders.ListGapCell
import org.mytonwallet.app_air.uicomponents.adapter.implementation.holders.ListTitleCell
import org.mytonwallet.app_air.uicomponents.base.WViewController
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.extensions.setPaddingDp
import org.mytonwallet.app_air.uicomponents.helpers.WFont
import org.mytonwallet.app_air.uicomponents.helpers.spans.WForegroundColorSpan
import org.mytonwallet.app_air.uicomponents.helpers.typeface
import org.mytonwallet.app_air.uicomponents.image.Content
import org.mytonwallet.app_air.uicomponents.widgets.CopyTextView
import org.mytonwallet.app_air.uicomponents.widgets.WButton
import org.mytonwallet.app_air.uicomponents.widgets.lockView
import org.mytonwallet.app_air.uicomponents.widgets.passcode.headers.PasscodeHeaderSendView
import org.mytonwallet.app_air.uicomponents.widgets.setBackgroundColor
import org.mytonwallet.app_air.uicomponents.widgets.unlockView
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.PasscodeConfirmVC
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.PasscodeViewState
import org.mytonwallet.app_air.uipasscode.viewControllers.passcodeConfirm.views.PasscodeScreenView
import org.mytonwallet.app_air.uisend.send.lauouts.ConfirmAmountView
import org.mytonwallet.app_air.walletcontext.R
import org.mytonwallet.app_air.walletcontext.helpers.LocaleController
import org.mytonwallet.app_air.walletcontext.theme.ThemeManager
import org.mytonwallet.app_air.walletcontext.theme.ViewConstants
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import org.mytonwallet.app_air.walletcontext.utils.CoinUtils
import org.mytonwallet.app_air.walletcore.moshi.MApiSubmitTransferOptions
import org.mytonwallet.app_air.walletcore.stores.AccountStore
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class SendConfirmVC(
    context: Context,
    private val config: SendViewModel.DraftResult.Result,
    private val transferOptions: MApiSubmitTransferOptions,
    private val slug: String
) : WViewController(context) {

    private var task: ((passcode: String?) -> Unit)? = null

    fun setNextTask(task: (passcode: String?) -> Unit) {
        this.task = task
    }

    private val amountInfoView by lazy {
        ConfirmAmountView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

            val amount = SpannableStringBuilder(config.request.amountEquivalent.getFmt(false))
            CoinUtils.setSpanToFractionalPart(amount, WForegroundColorSpan(WColor.SecondaryText))
            set(
                Content.of(config.request.token),
                amount = amount,
                currency = config.request.amountEquivalent.getFmt(true),
                fee = LocaleController.getString(
                    R.string.DApp_Send_FeeX,
                    listOf(
                        config.showingFee?.toString(
                            config.request.token,
                        ) ?: ""
                    )
                )
            )
        }
    }
    private val title1 = ListTitleCell(context).apply {
        text = LocaleController.getString(R.string.SendTo_SendTo)
    }
    private val addressInputView by lazy {
        CopyTextView(context).apply {
            typeface = WFont.Regular.typeface
            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT
            ).apply {
                val left = (20 - CopyTextView.PADDING_HORIZONTAL).dp
                setPadding(
                    left,
                    -CopyTextView.PADDING_VERTICAL.dp,
                    left,
                    (20 - CopyTextView.PADDING_VERTICAL).dp
                )
            }

            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineHeight(TypedValue.COMPLEX_UNIT_SP, 24f)
            text = config.resolvedAddress
            clipLabel = "Address"
            clipToast = LocaleController.getString(R.string.SendConfirm_AddressCopied)
        }
    }

    private val commentInputView by lazy {
        AppCompatTextView(context).apply {
            typeface = WFont.Regular.typeface
            layoutParams =
                ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPaddingDp(20, 0, 20, 20)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineHeight(TypedValue.COMPLEX_UNIT_SP, 24f)
            text = config.request.input.comment
        }
    }

    private val gap1 = ListGapCell(context)
    private val title2 = ListTitleCell(context).apply {
        text = LocaleController.getString(R.string.SendConfirm_Amount)
    }

    private val gap2 = ListGapCell(context)
    private val title3 = ListTitleCell(context).apply {
        text = LocaleController.getString(R.string.SendTo_CommentOrMemo)
    }

    private val linearLayout by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title1)
            addView(addressInputView)
            addView(gap1)
            addView(title2)
            addView(
                amountInfoView,
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )

            if (config.request.input.comment.isNotEmpty()) {
                addView(gap2)
                addView(title3)
                addView(
                    commentInputView,
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                )
            }
        }
    }

    private val scrollView by lazy {
        ScrollView(context).apply {
            addView(
                linearLayout,
                ViewGroup.LayoutParams(
                    MATCH_PARENT,
                    WRAP_CONTENT
                )
            )
            id = View.generateViewId()
            overScrollMode = ScrollView.OVER_SCROLL_ALWAYS
        }
    }

    private val confirmButton by lazy {
        WButton(context, WButton.Type.PRIMARY).apply {
            id = View.generateViewId()
            text = LocaleController.getString(R.string.YesConfirm)
            setOnClickListener {
                if (AccountStore.activeAccount?.isHardware == true) {
                    confirmHardware(transferOptions)
                } else {
                    confirmWithPassword()
                }
            }
        }
    }

    private val cancelButton by lazy {
        WButton(context, WButton.Type.SECONDARY_WITH_BACKGROUND).apply {
            id = View.generateViewId()
            text = LocaleController.getString(R.string.NoEdit)
            setOnClickListener { pop() }
        }
    }

    override fun setupViews() {
        super.setupViews()
        setNavTitle(LocaleController.getString(R.string.IsItAllOk))
        setupNavBar(true)
        navigationBar?.addCloseButton()

        view.addView(scrollView, ViewGroup.LayoutParams(MATCH_PARENT, 0))
        view.addView(cancelButton, ViewGroup.LayoutParams(0, 50.dp))
        view.addView(confirmButton, ViewGroup.LayoutParams(0, 50.dp))
        view.setConstraints {
            toCenterX(scrollView)
            topToBottom(scrollView, navigationBar!!)
            bottomToTop(scrollView, confirmButton, 20f)
            toBottomPx(
                cancelButton, 20.dp + max(
                    (navigationController?.getSystemBars()?.bottom ?: 0),
                    (window?.imeInsets?.bottom ?: 0)
                )
            )
            topToTop(confirmButton, cancelButton)
            toLeft(cancelButton)
            leftToRight(confirmButton, cancelButton)
            toRight(confirmButton)
            setMargin(cancelButton.id, ConstraintSet.START, 20.dp)
            setMargin(confirmButton.id, ConstraintSet.START, 8.dp)
            setMargin(confirmButton.id, ConstraintSet.END, 20.dp)
            createHorizontalChain(
                ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
                ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
                intArrayOf(cancelButton.id, confirmButton.id),
                null,
                ConstraintSet.CHAIN_SPREAD
            )
        }

        updateTheme()
    }

    override fun updateTheme() {
        super.updateTheme()
        view.setBackgroundColor(WColor.SecondaryBackground.color)
        title1.setBackgroundColor(WColor.Background.color, ViewConstants.BAR_ROUNDS.dp, 0f)
        addressInputView.setBackgroundColor(
            WColor.Background.color,
            0f,
            ViewConstants.BIG_RADIUS.dp
        )
        title2.setBackgroundColor(WColor.Background.color, ViewConstants.BIG_RADIUS.dp, 0f)
        amountInfoView.setBackgroundColor(
            WColor.Background.color,
            0f,
            ViewConstants.BIG_RADIUS.dp
        )
        title3.setBackgroundColor(WColor.Background.color, ViewConstants.BIG_RADIUS.dp, 0f)
        commentInputView.setBackgroundColor(
            WColor.Background.color,
            0f,
            ViewConstants.BIG_RADIUS.dp
        )
        addressInputView.setTextColor(WColor.PrimaryText.color)
        commentInputView.setTextColor(WColor.PrimaryText.color)
        val showSeparator =
            !ThemeManager.uiMode.hasRoundedCorners && !ThemeManager.isDark
        gap1.showSeparator = showSeparator
        gap2.showSeparator = showSeparator
        gap1.invalidate()
        gap2.invalidate()
    }

    override fun insetsUpdated() {
        super.insetsUpdated()
        linearLayout.setPadding(
            ViewConstants.HORIZONTAL_PADDINGS.dp,
            0,
            ViewConstants.HORIZONTAL_PADDINGS.dp,
            0
        )
        view.setConstraints {
            toBottomPx(
                cancelButton, 20.dp + max(
                    (navigationController?.getSystemBars()?.bottom ?: 0),
                    (window?.imeInsets?.bottom ?: 0)
                )
            )
        }
    }

    private fun confirmHardware(transferOptions: MApiSubmitTransferOptions) {
        confirmButton.lockView()
        val account = AccountStore.activeAccount!!
        val ledger = account.ledger ?: return
        val ledgerConnectVC = LedgerConnectVC(
            context,
            LedgerConnectVC.Mode.ConnectToSubmitTransfer(
                ledger.index,
                account.tonAddress!!,
                signData = LedgerConnectVC.SignData.SignTransfer(
                    transferOptions = transferOptions,
                    slug = slug
                ),
                onDone = {
                    task?.invoke(null)
                }),
            headerView = PasscodeHeaderSendView(
                context,
                (view.height * PasscodeScreenView.TOP_HEADER_MAX_HEIGHT_RATIO).roundToInt()
            ).apply {
                configSendingToken(
                    config.request.token,
                    config.request.amountEquivalent.getFmt(false),
                    config.resolvedAddress
                )
            }
        )
        push(ledgerConnectVC, onCompletion = {
            confirmButton.unlockView()
        })
    }

    private fun confirmWithPassword() {
        push(
            PasscodeConfirmVC(
                context,
                PasscodeViewState.CustomHeader(
                    PasscodeHeaderSendView(
                        context,
                        (view.height * 0.25f).roundToInt()
                    ).apply {
                        configSendingToken(
                            config.request.token,
                            config.request.amountEquivalent.getFmt(false),
                            config.resolvedAddress
                        )
                    },
                    LocaleController.getString(R.string.DApp_Send_Confirm)
                ),
                task = { passcode -> task?.invoke(passcode) }
            ))
    }
}
