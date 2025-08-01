package org.mytonwallet.app_air.uicreatewallet.viewControllers.wordCheck

import android.app.Activity
import org.mytonwallet.app_air.walletcontext.globalStorage.WGlobalStorage
import org.mytonwallet.app_air.walletcontext.helpers.logger.LogMessage
import org.mytonwallet.app_air.walletcontext.helpers.logger.Logger
import org.mytonwallet.app_air.walletcontext.secureStorage.WSecureStorage
import org.mytonwallet.app_air.walletcore.WalletCore
import org.mytonwallet.app_air.walletcore.api.createWallet
import org.mytonwallet.app_air.walletcore.models.MAccount
import org.mytonwallet.app_air.walletcore.models.MBridgeError
import java.lang.ref.WeakReference

class WordCheckVM(delegate: Delegate) {
    interface Delegate {
        fun showError(error: MBridgeError?)
        fun finalizedCreation(createdAccount: MAccount)
    }

    val delegate: WeakReference<Delegate> = WeakReference(delegate)

    // Create and add the account into logics
    fun finalizeAccount(
        window: Activity,
        words: Array<String>,
        passcode: String,
        biometricsActivated: Boolean?,
        retriesLeft: Int
    ) {
        WalletCore.createWallet(words, passcode) { account, error ->
            if (account == null || error != null) {
                if (retriesLeft > 0) {
                    finalizeAccount(window, words, passcode, biometricsActivated, retriesLeft)
                } else {
                    delegate.get()?.showError(error)
                }
            } else {
                val createdAccountId = account.accountId
                Logger.d(
                    Logger.LogTag.ACCOUNT,
                    LogMessage.Builder()
                        .append(
                            createdAccountId,
                            LogMessage.MessagePartPrivacy.PUBLIC
                        )
                        .append(
                            "Created",
                            LogMessage.MessagePartPrivacy.PUBLIC
                        )
                        .append(
                            "Address: ${account.tonAddress}",
                            LogMessage.MessagePartPrivacy.REDACTED
                        ).build()
                )
                WGlobalStorage.addAccount(
                    accountId = createdAccountId,
                    accountType = MAccount.AccountType.MNEMONIC.value,
                    address = account.tonAddress,
                    tronAddress = account.addressByChain["tron"],
                    importedAt = account.importedAt
                )
                if (biometricsActivated != null) {
                    if (biometricsActivated) {
                        WSecureStorage.setBiometricPasscode(window, passcode)
                    } else {
                        WSecureStorage.deleteBiometricPasscode(window)
                    }
                    WGlobalStorage.setIsBiometricActivated(biometricsActivated)
                }
                delegate.get()?.finalizedCreation(account)
            }
        }
    }
}
