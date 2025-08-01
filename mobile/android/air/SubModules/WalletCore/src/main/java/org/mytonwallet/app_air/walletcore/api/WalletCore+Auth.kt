package org.mytonwallet.app_air.walletcore.api

import org.json.JSONObject
import org.mytonwallet.app_air.walletcontext.cacheStorage.WCacheStorage
import org.mytonwallet.app_air.walletcontext.globalStorage.WGlobalStorage
import org.mytonwallet.app_air.walletcontext.utils.toJSONString
import org.mytonwallet.app_air.walletcore.MAIN_NETWORK
import org.mytonwallet.app_air.walletcore.POPULAR_WALLET_VERSIONS
import org.mytonwallet.app_air.walletcore.TEST_NETWORK
import org.mytonwallet.app_air.walletcore.WalletCore
import org.mytonwallet.app_air.walletcore.models.MAccount
import org.mytonwallet.app_air.walletcore.models.MBridgeError
import org.mytonwallet.app_air.walletcore.pushNotifications.AirPushNotifications
import org.mytonwallet.app_air.walletcore.stores.AccountStore
import org.mytonwallet.app_air.walletcore.stores.ActivityStore
import org.mytonwallet.app_air.walletcore.stores.BalanceStore
import org.mytonwallet.app_air.walletcore.stores.NftStore
import org.mytonwallet.app_air.walletcore.stores.StakingStore

fun WalletCore.createWallet(
    words: Array<String>,
    passcode: String,
    callback: (MAccount?, MBridgeError?) -> Unit
) {
    // Safely quote network and passcode to prevent injection
    val quotedNetwork = JSONObject.quote(activeNetwork)
    val quotedPasscode = JSONObject.quote(passcode)

    bridge?.callApi(
        "createWallet",
        "[$quotedNetwork, ${words.toJSONString}, $quotedPasscode]"
    ) { result, error ->
        if (error != null || result == null) {
            callback(null, error)
        } else {
            val account = JSONObject(result)
            callback(
                MAccount(
                    account.optString("accountId", ""),
                    mapOf(
                        "ton" to (account.optJSONObject("addressByChain")?.optString("ton") ?: ""),
                        "tron" to (account.optJSONObject("addressByChain")?.optString("tron")
                            ?: ""),
                    ),
                    "",
                    MAccount.AccountType.MNEMONIC,
                    ledger = null,
                    importedAt = null
                ), null
            )
        }
    }
}

fun WalletCore.importWallet(
    words: Array<String>,
    passcode: String,
    callback: (MAccount?, MBridgeError?) -> Unit
) {
    // Safely quote network and passcode to prevent injection
    val quotedNetwork = JSONObject.quote(activeNetwork)
    val quotedPasscode = JSONObject.quote(passcode)

    bridge?.callApi(
        "importMnemonic",
        "[$quotedNetwork, ${words.toJSONString}, $quotedPasscode]"
    ) { result, error ->
        if (error != null || result == null) {
            callback(null, error)
        } else {
            val account = JSONObject(result)
            callback(
                MAccount(
                    account.optString("accountId", ""),
                    mapOf(
                        "ton" to (account.optJSONObject("addressByChain")?.optString("ton") ?: ""),
                        "tron" to (account.optJSONObject("addressByChain")?.optString("tron")
                            ?: ""),
                    ),
                    name = "",
                    accountType = MAccount.AccountType.MNEMONIC,
                    ledger = null,
                    importedAt = System.currentTimeMillis()
                ), null
            )
        }
    }
}

fun WalletCore.importNewWalletVersion(
    prevAccount: MAccount,
    version: String,
    callback: (MAccount?, MBridgeError?) -> Unit
) {
    val quotedAccountId = JSONObject.quote(prevAccount.accountId)
    val quotedVersion = JSONObject.quote(version)

    bridge?.callApi(
        "importNewWalletVersion",
        "[$quotedAccountId, $quotedVersion]"
    ) { result, error ->
        if (error != null || result == null) {
            callback(null, error)
        } else {
            val accountObj = JSONObject(result)
            val ledgerObj = accountObj.optJSONObject("ledger")
            val regex = "\\b(${POPULAR_WALLET_VERSIONS.joinToString("|")})\\b".toRegex()
            val prevName = prevAccount.name.replace(regex, "").trim()
            callback(
                MAccount(
                    accountObj.getString("accountId"),
                    mapOf(
                        "ton" to accountObj.getString("address"),
                    ),
                    name = "$prevName $version",
                    accountType = prevAccount.accountType,
                    ledger = if (ledgerObj != null) MAccount.Ledger(ledgerObj) else null,
                    importedAt = System.currentTimeMillis()
                ), null
            )
        }
    }
}

fun WalletCore.validateMnemonic(
    words: Array<String>,
    callback: (Boolean, MBridgeError?) -> Unit
) {
    val sanitizedWords = words.map { word ->
        val trimmed = word.trim().lowercase()
        if (trimmed.isEmpty() || !trimmed.matches(Regex("^[a-z]+$")) || trimmed.length > 20) {
            callback(false, MBridgeError.INVALID_MNEMONIC)
            return
        }
        trimmed
    }.toTypedArray()

    bridge?.callApi(
        "validateMnemonic",
        "[${sanitizedWords.toJSONString}]"
    ) { result, error ->
        if (error != null || result != "true") {
            callback(false, error ?: MBridgeError.INVALID_MNEMONIC)
        } else {
            callback(true, null)
        }
    }
}

fun WalletCore.activateAccount(
    accountId: String,
    notifySDK: Boolean,
    callback: (MAccount?, MBridgeError?) -> Unit
) {
    val newestActivitiesBySlug = WGlobalStorage.getNewestActivitiesBySlug(accountId)
    val newestActivitiesTimestampBySlug = JSONObject()
    newestActivitiesBySlug?.keys()?.let { keys ->
        for (key in keys) {
            val ts = newestActivitiesBySlug.optJSONObject(key)?.optLong("timestamp")
            newestActivitiesTimestampBySlug.put(key, ts)
        }
    }
    fun fetch() {
        fetchAccount(accountId) { account, err ->
            if (account == null || err != null) {
                callback(null, err)
            } else {
                activeNetwork =
                    if (accountId.split("-")[1] == MAIN_NETWORK) MAIN_NETWORK else TEST_NETWORK
                isMultichain = account.isMultichain
                notifyAccountChanged(account)
                callback(account, null)
                WCacheStorage.setInitialScreen(
                    if (AccountStore.activeAccount?.isPasscodeProtected == true)
                        WCacheStorage.InitialScreen.LOCK
                    else
                        WCacheStorage.InitialScreen.HOME
                )
            }
        }
    }
    if (notifySDK) {
        bridge?.callApi(
            "activateAccount",
            "[${JSONObject.quote(accountId)}, ${newestActivitiesTimestampBySlug}]"
        ) { result, error ->
            if (error != null || result == null) {
                callback(null, error)
            } else {
                fetch()
            }
        }
    } else {
        fetch()
    }
}

fun WalletCore.fetchAccount(
    accountId: String,
    callback: (MAccount?, MBridgeError?) -> Unit
) {
    if (accountId != AccountStore.activeAccount?.accountId)
        AccountStore.activeAccount = null
    try {
        val globalAccountData = WGlobalStorage.getAccount(accountId) ?: throw Exception()
        val account = MAccount(
            accountId = accountId,
            globalJSON = globalAccountData
        )

        AccountStore.activeAccount = account
        callback(account, null)

    } catch (e: Exception) {
        callback(null, MBridgeError.UNKNOWN)
    }
}

fun WalletCore.resetAccounts(
    callback: (Boolean?, MBridgeError?) -> Unit
) {
    val accountIds = WGlobalStorage.accountIds()
    AccountStore.updateActiveAccount(null)
    bridge?.callApi(
        "resetAccounts",
        "[]"
    ) { result, error ->
        if (error != null || result == null) {
            callback(null, error)
        } else {
            AirPushNotifications.unsubscribeAll()
            AccountStore.clean()
            ActivityStore.clean()
            BalanceStore.clean()
            NftStore.clean()
            StakingStore.clean()
            WCacheStorage.clean(accountIds)
            WCacheStorage.setInitialScreen(WCacheStorage.InitialScreen.INTRO)
            callback(true, null)
        }
    }
}

fun WalletCore.removeAccount(
    accountId: String,
    nextAccountId: String,
    callback: (Boolean?, MBridgeError?) -> Unit
) {
    AccountStore.updateActiveAccount(null)
    val quotedAccountId = JSONObject.quote(accountId)
    val quotedNextAccountId = JSONObject.quote(nextAccountId)

    bridge?.callApi(
        "removeAccount",
        "[$quotedAccountId, $quotedNextAccountId]"
    ) { result, error ->
        if (error != null || result == null) {
            callback(null, error)
        } else {
            callback(true, null)
        }
    }
}

fun WalletCore.verifyPassword(
    password: String,
    callback: (Boolean?, MBridgeError?) -> Unit
) {
    val quotedPassword = JSONObject.quote(password)

    bridge?.callApi(
        "verifyPassword",
        "[$quotedPassword]"
    ) { result, error ->
        callback(result == "true", error)
    }
}
