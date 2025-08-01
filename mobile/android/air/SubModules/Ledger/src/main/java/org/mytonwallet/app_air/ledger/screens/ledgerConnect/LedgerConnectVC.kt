package org.mytonwallet.app_air.ledger.screens.ledgerConnect

import WNavigationController
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mytonwallet.app_air.ledger.LedgerManager
import org.mytonwallet.app_air.ledger.connectionManagers.LedgerBleManager
import org.mytonwallet.app_air.ledger.screens.ledgerConnect.views.LedgerConnectStepStatusView
import org.mytonwallet.app_air.ledger.screens.ledgerConnect.views.LedgerConnectStepView
import org.mytonwallet.app_air.uicomponents.base.WViewController
import org.mytonwallet.app_air.uicomponents.drawable.SeparatorBackgroundDrawable
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.extensions.setPaddingDp
import org.mytonwallet.app_air.uicomponents.helpers.WFont
import org.mytonwallet.app_air.uicomponents.widgets.WButton
import org.mytonwallet.app_air.uicomponents.widgets.WLabel
import org.mytonwallet.app_air.uicomponents.widgets.WThemedView
import org.mytonwallet.app_air.uicomponents.widgets.WView
import org.mytonwallet.app_air.uicomponents.widgets.menu.WMenuPopup
import org.mytonwallet.app_air.uicomponents.widgets.setBackgroundColor
import org.mytonwallet.app_air.walletcontext.R
import org.mytonwallet.app_air.walletcontext.WalletContextManager
import org.mytonwallet.app_air.walletcontext.globalStorage.WGlobalStorage
import org.mytonwallet.app_air.walletcontext.helpers.LocaleController
import org.mytonwallet.app_air.walletcontext.helpers.logger.LogMessage
import org.mytonwallet.app_air.walletcontext.helpers.logger.Logger
import org.mytonwallet.app_air.walletcontext.theme.ThemeManager
import org.mytonwallet.app_air.walletcontext.theme.ViewConstants
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import org.mytonwallet.app_air.walletcontext.utils.VerticalImageSpan
import org.mytonwallet.app_air.walletcore.JSWebViewBridge
import org.mytonwallet.app_air.walletcore.MAIN_NETWORK
import org.mytonwallet.app_air.walletcore.WalletCore
import org.mytonwallet.app_air.walletcore.WalletEvent
import org.mytonwallet.app_air.walletcore.api.activateAccount
import org.mytonwallet.app_air.walletcore.api.submitStake
import org.mytonwallet.app_air.walletcore.api.submitUnstake
import org.mytonwallet.app_air.walletcore.models.MAccount
import org.mytonwallet.app_air.walletcore.models.MBlockchain
import org.mytonwallet.app_air.walletcore.moshi.ApiNft
import org.mytonwallet.app_air.walletcore.moshi.ApiTonConnectProof
import org.mytonwallet.app_air.walletcore.moshi.ApiTransferToSign
import org.mytonwallet.app_air.walletcore.moshi.LocalActivityParams
import org.mytonwallet.app_air.walletcore.moshi.MApiLedgerDriver
import org.mytonwallet.app_air.walletcore.moshi.MApiSubmitTransferOptions
import org.mytonwallet.app_air.walletcore.moshi.MApiTonWallet
import org.mytonwallet.app_air.walletcore.moshi.MApiTonWalletVersion
import org.mytonwallet.app_air.walletcore.moshi.MLedgerWalletInfo
import org.mytonwallet.app_air.walletcore.moshi.StakingState
import org.mytonwallet.app_air.walletcore.moshi.api.ApiMethod
import org.mytonwallet.app_air.walletcore.moshi.api.ApiUpdate
import org.mytonwallet.app_air.walletcore.stores.AccountStore
import java.math.BigInteger
import kotlin.math.max

class LedgerConnectVC(
    context: Context,
    private val mode: Mode,
    private val headerView: View? = null,
) : WViewController(context), WThemedView {

    sealed class Mode {
        data object AddAccount : Mode()
        data class ConnectToSubmitTransfer(
            val index: Int,
            val address: String,
            val signData: SignData,
            val onDone: () -> Unit
        ) : Mode()
    }

    sealed class SignData {
        data class SignTransfer(
            val transferOptions: MApiSubmitTransferOptions,
            val slug: String,
            val localActivityParams: LocalActivityParams? = null,
            val payload: JSONObject? = null
        ) : SignData()

        data class SignDappTransfers(
            val update: ApiUpdate.ApiUpdateDappSendTransactions
        ) : SignData()

        data class SignLedgerProof(
            val promiseId: String,
            val proof: ApiTonConnectProof
        ) : SignData()

        data class SignNftTransfer(
            val accountId: String,
            val nft: ApiNft,
            val toAddress: String,
            val comment: String?,
            val realFee: BigInteger?
        ) : SignData()

        data class Staking(
            val isStaking: Boolean,
            val accountId: String,
            val amount: BigInteger,
            val stakingState: StakingState,
            val realFee: BigInteger,
        ) : SignData()

        data class ClaimRewards(
            val accountId: String,
            val stakingState: StakingState,
            val realFee: BigInteger
        ) : SignData()

        data class RenewNfts(
            val accountId: String,
            val nfts: List<ApiNft>,
            val realFee: BigInteger
        ) : SignData()
    }

    override val shouldDisplayTopBar = true
    override val ignoreSideGuttering = true

    private val ledgerImage = AppCompatImageView(context).apply {
        id = View.generateViewId()
    }

    private val connectLedgerStep = LedgerConnectStepView(
        context, LocaleController.getString(R.string.Ledger_ConnectYourLedger)
    ).apply {
        state = LedgerConnectStepStatusView.State.IN_PROGRESS
    }

    private val openTonAppStep = LedgerConnectStepView(
        context, LocaleController.getString(R.string.Ledger_OpenTonApp)
    )

    private val signOnDeviceStep: LedgerConnectStepView by lazy {
        LedgerConnectStepView(
            context, LocaleController.getString(
                if (mode is Mode.ConnectToSubmitTransfer && mode.signData is SignData.SignLedgerProof)
                    R.string.Ledger_ConfirmAddressOnLedger
                else
                    R.string.Ledger_ConfirmOnLedger
            )
        )
    }

    private val stepsView = LinearLayout(context).apply {
        id = View.generateViewId()
        orientation = LinearLayout.VERTICAL
        addView(connectLedgerStep, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(openTonAppStep, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        if (mode is Mode.ConnectToSubmitTransfer)
            addView(signOnDeviceStep, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        gravity = Gravity.START
    }

    private val tryAgainButton = WButton(context, WButton.Type.PRIMARY).apply {
        visibility = View.INVISIBLE
    }

    private val informationView: WView by lazy {
        WView(context).apply {
            addView(
                ledgerImage,
                ViewGroup.LayoutParams(
                    WRAP_CONTENT,
                    if (headerView == null) WRAP_CONTENT else 150.dp
                )
            )
            ledgerImage.setPaddingDp(16)
            addView(stepsView, ViewGroup.LayoutParams(0, WRAP_CONTENT))
            addView(connectionTypeView, ViewGroup.LayoutParams(0, 48.dp))
            addView(tryAgainButton, ViewGroup.LayoutParams(0, WRAP_CONTENT))
            setConstraints {
                toTop(ledgerImage)
                toCenterX(ledgerImage)
                setVerticalBias(stepsView.id, 0f)
                topToBottom(stepsView, ledgerImage)
                toCenterX(stepsView, 48f)
                toCenterX(connectionTypeView, 48f)
                topToBottom(connectionTypeView, stepsView, 16f)
                toCenterX(tryAgainButton, 48f)
                toBottomPx(
                    tryAgainButton, 20.dp + max(
                        (navigationController?.getSystemBars()?.bottom ?: 0),
                        (window?.imeInsets?.bottom ?: 0)
                    )
                )
            }
        }
    }

    val connectionTypeLabel = WLabel(context).apply {
        text = LocaleController.getString(R.string.Ledger_ConnectionType)
        setStyle(16f, WFont.SemiBold)
    }

    val connectionTypeValue = WLabel(context).apply {
        setStyle(16f, WFont.SemiBold)
    }

    val connectionTypeView: WView by lazy {
        WView(context).apply {
            addView(connectionTypeLabel)
            addView(connectionTypeValue)
            setConstraints {
                toStart(connectionTypeLabel, 16f)
                toCenterY(connectionTypeLabel)
                toEnd(connectionTypeValue, 16f)
                toCenterY(connectionTypeValue)
            }
        }
    }

    private val prevAccountsCount = WGlobalStorage.accountIds().size

    override fun setupViews() {
        super.setupViews()

        LedgerManager.init(window!!, context)

        title = when (mode) {
            Mode.AddAccount -> {
                LocaleController.getString(R.string.Settings_AddAccount)
            }

            is Mode.ConnectToSubmitTransfer -> {
                LocaleController.getString(R.string.DApp_Send_Confirm)
            }
        }
        setupNavBar(true)
        if (navigationController?.viewControllers?.size == 1) {
            navigationBar?.addCloseButton()
        }

        headerView?.let {
            headerView.id = View.generateViewId()
            view.addView(headerView, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        view.addView(informationView, ViewGroup.LayoutParams(WRAP_CONTENT, 0))
        view.setConstraints {
            headerView?.let {
                topToBottom(headerView, navigationBar!!)
                setVerticalBias(informationView.id, 0f)
                topToBottomPx(informationView, headerView, ViewConstants.GAP.dp)
            } ?: run {
                toTop(informationView)
            }
            toCenterX(informationView)
            toBottom(informationView)
        }

        connectionTypeValue.setOnClickListener {
            WMenuPopup.present(
                connectionTypeView,
                listOf(
                    WMenuPopup.Item(
                        null,
                        LocaleController.getString(R.string.Ledger_ConnectionType_Bluetooth),
                        false,
                    ) {
                        tryAgain(LedgerManager.ConnectionMode.BLE)
                        updateConnectionTypeView()
                    },
                    WMenuPopup.Item(
                        null,
                        LocaleController.getString(R.string.Ledger_ConnectionType_USB),
                        false,
                    ) {
                        tryAgain(LedgerManager.ConnectionMode.USB)
                        updateConnectionTypeView()
                    }
                ),
                offset = connectionTypeView.width - 116.dp,
                popupWidth = 100.dp,
                aboveView = true
            )
        }

        updateTheme()

        initBluetooth()
        tryAgain(LedgerManager.activeMode)
    }

    override fun updateTheme() {
        super.updateTheme()
        if (headerView == null) {
            view.setBackgroundColor(WColor.Background.color)
        } else {
            view.setBackgroundColor(WColor.SecondaryBackground.color)
            if (ThemeManager.uiMode.hasRoundedCorners)
                headerView.setBackgroundColor(
                    WColor.Background.color,
                    0f,
                    ViewConstants.BIG_RADIUS.dp,
                )
            else
                headerView.background = SeparatorBackgroundDrawable().apply {
                    backgroundWColor = WColor.Background
                }
            informationView.setBackgroundColor(
                WColor.Background.color,
                ViewConstants.BIG_RADIUS.dp,
                0f,
            )
        }
        connectionTypeView.setBackgroundColor(WColor.SecondaryBackground.color, 16f.dp)
        connectionTypeLabel.setTextColor(WColor.PrimaryText.color)
        connectionTypeValue.setTextColor(WColor.SecondaryText.color)
        updateConnectionTypeView()
    }

    private fun updateConnectionTypeView() {
        ledgerImage.setImageResource(
            if (LedgerManager.activeMode == LedgerManager.ConnectionMode.USB)
                if (ThemeManager.isDark) R.drawable.img_ledger_usb_dark else R.drawable.img_ledger_usb_light
            else
                if (ThemeManager.isDark) R.drawable.img_ledger_bluetooth_dark else R.drawable.img_ledger_bluetooth_light
        )

        val txt =
            LocaleController.getString(
                if (
                    (LedgerManager.activeMode
                        ?: LedgerManager.ConnectionMode.BLE) == LedgerManager.ConnectionMode.BLE
                )
                    R.string.Ledger_ConnectionType_Bluetooth
                else
                    R.string.Ledger_ConnectionType_USB
            ) + " "
        val ss = SpannableStringBuilder(txt)
        ContextCompat.getDrawable(
            context,
            org.mytonwallet.app_air.icons.R.drawable.ic_arrow_bottom_8
        )?.let { drawable ->
            drawable.mutate()
            drawable.setTint(WColor.SecondaryText.color)
            val width = 8.dp
            val height = 4.dp
            drawable.setBounds(0, 0, width, height)
            val imageSpan = VerticalImageSpan(drawable)
            ss.append(" ", imageSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        connectionTypeValue.text = ss
    }

    // Bluetooth adapter (turn on bluetooth if required)
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )) {
                        BluetoothAdapter.STATE_ON -> {
                            startBleConnection()
                        }

                        BluetoothAdapter.STATE_OFF -> {
                            onUpdate(
                                LedgerManager.ConnectionState.Error(
                                    step = LedgerManager.ConnectionState.Error.Step.CONNECT,
                                    message = null
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initBluetooth() {
        val bluetoothManager =
            window!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        window!!.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }

    private var tryAgainButtonToOpenSettings: Boolean? = null
    private fun configureTryAgainButton(toOpenSettings: Boolean) {
        if (this.tryAgainButtonToOpenSettings == toOpenSettings)
            return
        this.tryAgainButtonToOpenSettings = toOpenSettings
        tryAgainButton.setText(
            text = LocaleController.getString(
                if (toOpenSettings) R.string.Ledger_OpenSettings else R.string.Ledger_TryAgain
            ),
            isAnimated = true
        )
        tryAgainButton.setOnClickListener {
            if (toOpenSettings) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", context.applicationContext.packageName, null)
                intent.data = uri
                window?.startActivity(intent)
            } else {
                tryAgain(LedgerManager.activeMode)
            }
        }
    }

    private fun tryAgain(connectionMode: LedgerManager.ConnectionMode?) {
        // TODO:: We can later check if any ledger devices are connected using USB and use USB as default in that case
        val defaultMode = LedgerManager.ConnectionMode.BLE
        LedgerManager.activeMode = connectionMode ?: defaultMode
        when (LedgerManager.activeMode!!) {
            LedgerManager.ConnectionMode.BLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!LedgerBleManager.isPermissionGranted()) {
                        window?.requestPermissions(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        ) { _, grantResults ->
                            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) checkAndEnableBluetooth()
                            else {
                                onUpdate(
                                    LedgerManager.ConnectionState.Error(
                                        step = LedgerManager.ConnectionState.Error.Step.CONNECT,
                                        message = LocaleController.getString(R.string.Ledger_PermissionDenied)
                                    )
                                )
                                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                        window!!,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    )
                                ) {
                                    configureTryAgainButton(toOpenSettings = true)
                                }
                            }
                        }
                    } else checkAndEnableBluetooth()
                } else checkAndEnableBluetooth()
            }

            LedgerManager.ConnectionMode.USB -> {
                startUsbConnection()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkAndEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            window!!.startActivity(enableBtIntent)
        } else {
            startBleConnection()
        }
    }

    private fun startBleConnection() {
        LedgerManager.startConnection(LedgerManager.ConnectionMode.BLE, onUpdate = {
            onUpdate(it)
        })
    }

    private fun startUsbConnection() {
        LedgerManager.startConnection(LedgerManager.ConnectionMode.USB, onUpdate = {
            onUpdate(it)
        })
    }

    private fun finalizeImport(deviceId: String, deviceName: String) {
        val allHardwareWalletAddresses = WGlobalStorage.accountIds().mapNotNull {
            WGlobalStorage.getAccountTonAddress(it)
        }
        var index = -1
        CoroutineScope(Dispatchers.IO).launch {
            val newWallets = mutableListOf<MApiTonWallet>()
            val walletsInfo = hashMapOf<String, MLedgerWalletInfo>()
            while (true) {
                index += 1
                val wallet = LedgerManager.getWalletInfo(index) ?: break
                if (allHardwareWalletAddresses.contains(wallet.address)) {
                    // Already imported
                    continue
                }
                val balance: BigInteger
                try {
                    balance = WalletCore.call(
                        ApiMethod.WalletData.GetWalletBalance(
                            "ton", MAIN_NETWORK, wallet.address
                        )
                    )
                } catch (e: Throwable) {
                    finalizeFailed()
                    continue
                }
                walletsInfo[wallet.address] = MLedgerWalletInfo(
                    index = index,
                    address = wallet.address,
                    publicKey = wallet.publicKey,
                    balance = balance,
                    version = MApiTonWalletVersion.V4_R2,
                    driver = MApiLedgerDriver.HID,
                    deviceId = deviceId,
                    deviceName = deviceName
                )
                if (balance != BigInteger.ZERO) {
                    newWallets.add(wallet)
                    continue
                }
                if (newWallets.isEmpty()) newWallets.add(wallet)
                break
            }
            if (isDisappeared) {
                return@launch
            }
            Handler(Looper.getMainLooper()).post {
                view.lockView()
            }
            CoroutineScope(Dispatchers.IO).launch {
                val finalizedWallets = mutableListOf<String>()
                newWallets.forEach { newWallet ->
                    try {
                        val result = WalletCore.call(
                            ApiMethod.Auth.ImportLedgerWallet(
                                MAIN_NETWORK, walletsInfo[newWallet.address]!!
                            )
                        )
                        Logger.d(
                            Logger.LogTag.ACCOUNT,
                            LogMessage.Builder()
                                .append(
                                    result.accountId,
                                    LogMessage.MessagePartPrivacy.PUBLIC
                                )
                                .append(
                                    "Connected",
                                    LogMessage.MessagePartPrivacy.PUBLIC
                                )
                                .append(
                                    "Address: ${result.address}",
                                    LogMessage.MessagePartPrivacy.REDACTED
                                ).build()
                        )
                        WGlobalStorage.addAccount(
                            accountId = result.accountId,
                            accountType = MAccount.AccountType.HARDWARE.value,
                            address = newWallet.address,
                            null,
                            JSONObject().apply {
                                put("driver", result.walletInfo.driver)
                                put("index", result.walletInfo.index)
                            },
                            importedAt = null
                        )
                        finalizedWallets.add(result.accountId)
                    } catch (_: Throwable) {
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    if (finalizedWallets.size == 0) {
                        finalizeFailed()
                    } else {
                        /*if (addedWallets != newWallets.size) {
                        // TODO:: Handle partial added situation
                        }*/
                        WalletCore.activateAccount(
                            accountId = finalizedWallets.first(),
                            notifySDK = true
                        ) { _, err ->
                            if (err != null) {
                                // Should not happen
                                finalizeFailed()
                                return@activateAccount
                            }
                            if (prevAccountsCount == 0) {
                                val navigationController = WNavigationController(window!!)
                                navigationController.setRoot(WalletContextManager.delegate?.getTabsVC() as WViewController)
                                window!!.replace(navigationController, true)
                            } else {
                                WalletCore.notifyEvent(WalletEvent.AddNewWalletCompletion)
                                window!!.dismissLastNav()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun finalizeValidation() {
        val mode = mode as Mode.ConnectToSubmitTransfer
        CoroutineScope(Dispatchers.IO).launch {
            val info = LedgerManager.getWalletInfo(mode.index)
            if (info?.address == mode.address) {
                when (val signData = mode.signData) {
                    is SignData.SignTransfer -> {
                        try {
                            WalletCore.call(
                                ApiMethod.Transfer.SubmitTransfer(
                                    MBlockchain.ton,
                                    options = signData.transferOptions
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }

                    is SignData.SignDappTransfers -> {
                        try {
                            val signedMessages = WalletCore.call(
                                ApiMethod.Transfer.SignLedgerTransactions(
                                    accountId = signData.update.accountId,
                                    transactions = signData.update.transactions.map {
                                        ApiTransferToSign(
                                            toAddress = it.toAddress,
                                            amount = it.amount,
                                            rawPayload = it.rawPayload,
                                            payload = it.payload,
                                            stateInit = it.stateInit
                                        )
                                    },
                                    options = ApiMethod.Transfer.SignLedgerTransactions.Options(
                                        validUntil = signData.update.validUntil,
                                        vestingAddress = signData.update.vestingAddress
                                    )
                                )
                            )
                            WalletCore.call(
                                ApiMethod.DApp.ConfirmLedgerDappRequest(
                                    signData.update.promiseId,
                                    signedMessages
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }

                    is SignData.SignLedgerProof -> {
                        try {
                            Handler(Looper.getMainLooper()).post {
                                view.unlockView()
                            }
                            val signResult = WalletCore.call(
                                ApiMethod.Ledger.SignTonProof(
                                    AccountStore.activeAccountId!!,
                                    signData.proof,
                                )
                            )
                            WalletCore.call(
                                ApiMethod.DApp.ConfirmDappRequestConnect(
                                    signData.promiseId,
                                    ApiMethod.DApp.ConfirmDappRequestConnect.Request(
                                        accountId = AccountStore.activeAccountId!!,
                                        proofSignature = signResult
                                    )
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }

                    is SignData.SignNftTransfer -> {
                        try {
                            WalletCore.call(
                                ApiMethod.Nft.SubmitNftTransfer(
                                    accountId = signData.accountId,
                                    passcode = "",
                                    nft = signData.nft,
                                    address = signData.toAddress,
                                    comment = signData.comment,
                                    fee = signData.realFee ?: BigInteger.ZERO
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }

                    is SignData.Staking -> {
                        try {
                            if (signData.isStaking)
                                WalletCore.submitStake(
                                    accountId = signData.accountId,
                                    passcode = "",
                                    amount = signData.amount,
                                    stakingState = signData.stakingState,
                                    realFee = signData.realFee,
                                )
                            else
                                WalletCore.submitUnstake(
                                    accountId = signData.accountId,
                                    passcode = "",
                                    amount = signData.amount,
                                    stakingState = signData.stakingState,
                                    realFee = signData.realFee,
                                )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }

                    is SignData.ClaimRewards -> {
                        try {
                            WalletCore.call(
                                ApiMethod.Staking.SubmitStakingClaimOrUnlock(
                                    accountId = AccountStore.activeAccountId!!,
                                    password = "",
                                    state = signData.stakingState,
                                    realFee = signData.realFee
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }

                    is SignData.RenewNfts -> {
                        try {
                            WalletCore.call(
                                ApiMethod.Domains.SubmitDnsRenewal(
                                    accountId = AccountStore.activeAccountId!!,
                                    password = "",
                                    nfts = signData.nfts,
                                    realFee = signData.realFee
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                mode.onDone()
                            }
                        } catch (e: Throwable) {
                            Handler(Looper.getMainLooper()).post {
                                signFailed(e as? JSWebViewBridge.ApiError)
                            }
                        }
                    }
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    finalizeFailed()
                }
            }
        }
    }

    private fun onUpdate(state: LedgerManager.ConnectionState) {
        tryAgainButton.visibility =
            if (state is LedgerManager.ConnectionState.Error) View.VISIBLE else View.INVISIBLE
        connectionTypeView.visibility =
            if (
                state is LedgerManager.ConnectionState.Connecting ||
                state is LedgerManager.ConnectionState.Error
            ) View.VISIBLE else View.GONE
        when (state) {
            LedgerManager.ConnectionState.Connecting -> {
                connectLedgerStep.state =
                    LedgerConnectStepStatusView.State.IN_PROGRESS
                openTonAppStep.state = LedgerConnectStepStatusView.State.WAITING
                signOnDeviceStep.state = LedgerConnectStepStatusView.State.WAITING
            }

            is LedgerManager.ConnectionState.ConnectingToTonApp -> {
                connectLedgerStep.state = LedgerConnectStepStatusView.State.DONE
                openTonAppStep.state = LedgerConnectStepStatusView.State.IN_PROGRESS
                signOnDeviceStep.state = LedgerConnectStepStatusView.State.WAITING
            }

            is LedgerManager.ConnectionState.Done -> {
                connectLedgerStep.state = LedgerConnectStepStatusView.State.DONE

                when (mode) {
                    Mode.AddAccount -> {
                        openTonAppStep.state =
                            LedgerConnectStepStatusView.State.IN_PROGRESS
                        finalizeImport(state.device.id, state.device.name)
                    }

                    is Mode.ConnectToSubmitTransfer -> {
                        openTonAppStep.state = LedgerConnectStepStatusView.State.DONE
                        signOnDeviceStep.state =
                            LedgerConnectStepStatusView.State.IN_PROGRESS
                        finalizeValidation()
                    }
                }
            }

            is LedgerManager.ConnectionState.Error -> {
                when (state.step) {
                    LedgerManager.ConnectionState.Error.Step.CONNECT -> {
                        connectLedgerStep.state = LedgerConnectStepStatusView.State.ERROR
                        connectLedgerStep.setError(state.message)
                        openTonAppStep.state = LedgerConnectStepStatusView.State.WAITING
                        signOnDeviceStep.state = LedgerConnectStepStatusView.State.WAITING
                    }

                    LedgerManager.ConnectionState.Error.Step.TON_APP -> {
                        connectLedgerStep.state = LedgerConnectStepStatusView.State.DONE
                        openTonAppStep.state = LedgerConnectStepStatusView.State.ERROR
                        openTonAppStep.setError(state.message)
                        signOnDeviceStep.state = LedgerConnectStepStatusView.State.WAITING
                    }

                    LedgerManager.ConnectionState.Error.Step.SIGN -> {
                        connectLedgerStep.state = LedgerConnectStepStatusView.State.DONE
                        openTonAppStep.state = LedgerConnectStepStatusView.State.DONE
                        signOnDeviceStep.state = LedgerConnectStepStatusView.State.ERROR
                        signOnDeviceStep.setError(state.message)
                    }
                }
            }

            LedgerManager.ConnectionState.None -> {
                connectLedgerStep.state = LedgerConnectStepStatusView.State.WAITING
                openTonAppStep.state = LedgerConnectStepStatusView.State.WAITING
                signOnDeviceStep.state = LedgerConnectStepStatusView.State.WAITING
            }
        }
    }

    private fun finalizeFailed() {
        Handler(Looper.getMainLooper()).post {
            view.unlockView()
            onUpdate(
                LedgerManager.ConnectionState.Error(
                    step = LedgerManager.ConnectionState.Error.Step.TON_APP,
                    message = null
                )
            )
        }
    }

    private fun signFailed(error: JSWebViewBridge.ApiError?) {
        view.unlockView()
        onUpdate(
            LedgerManager.ConnectionState.Error(
                step = LedgerManager.ConnectionState.Error.Step.SIGN,
                message = error?.parsed?.toShortLocalized
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        window!!.unregisterReceiver(
            bluetoothStateReceiver,
        )
    }

    override fun viewWillAppear() {
        super.viewWillAppear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            configureTryAgainButton(
                toOpenSettings = !LedgerBleManager.isPermissionGranted() &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        window!!,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
            )
        } else {
            configureTryAgainButton(false)
        }
    }
}
