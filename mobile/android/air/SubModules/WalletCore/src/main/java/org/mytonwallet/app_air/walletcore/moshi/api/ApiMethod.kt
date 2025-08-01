package org.mytonwallet.app_air.walletcore.moshi.api

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import org.json.JSONArray
import org.json.JSONObject
import org.mytonwallet.app_air.walletcore.api.ArgumentsBuilder
import org.mytonwallet.app_air.walletcore.models.MBlockchain
import org.mytonwallet.app_air.walletcore.moshi.ApiDapp
import org.mytonwallet.app_air.walletcore.moshi.ApiNft
import org.mytonwallet.app_air.walletcore.moshi.ApiNotificationAddress
import org.mytonwallet.app_air.walletcore.moshi.ApiTonConnectProof
import org.mytonwallet.app_air.walletcore.moshi.ApiTransferToSign
import org.mytonwallet.app_air.walletcore.moshi.DeviceInfo
import org.mytonwallet.app_air.walletcore.moshi.MApiCheckNftDraftOptions
import org.mytonwallet.app_air.walletcore.moshi.MApiCheckStakeDraftResult
import org.mytonwallet.app_air.walletcore.moshi.MApiCheckTransactionDraftOptions
import org.mytonwallet.app_air.walletcore.moshi.MApiCheckTransactionDraftResult
import org.mytonwallet.app_air.walletcore.moshi.MApiSubmitTransferOptions
import org.mytonwallet.app_air.walletcore.moshi.MApiSubmitTransferResult
import org.mytonwallet.app_air.walletcore.moshi.MApiSwapEstimateRequest
import org.mytonwallet.app_air.walletcore.moshi.MApiSwapEstimateResponse
import org.mytonwallet.app_air.walletcore.moshi.MApiTonWallet
import org.mytonwallet.app_air.walletcore.moshi.MApiTonWalletVersion
import org.mytonwallet.app_air.walletcore.moshi.MApiTransaction
import org.mytonwallet.app_air.walletcore.moshi.MImportedWalletResponse
import org.mytonwallet.app_air.walletcore.moshi.MLedgerWalletInfo
import org.mytonwallet.app_air.walletcore.moshi.ReturnStrategy
import org.mytonwallet.app_air.walletcore.moshi.StakingState
import java.lang.reflect.Type
import java.math.BigInteger

sealed class ApiMethod<T> {
    abstract val name: String
    abstract val type: Type
    abstract val arguments: String

    /* Auth */
    object Auth {
        class GenerateMnemonic : ApiMethod<Array<String>>() {
            override val name: String = "generateMnemonic"
            override val type: Type = Array<String>::class.java
            override val arguments: String = ArgumentsBuilder()
                .build()
        }

        class AddressFromPublicKey(
            publicKey: List<Int>,
            network: String,
            version: MApiTonWalletVersion
        ) : ApiMethod<MApiTonWallet>() {
            override val name: String = "addressFromPublicKey"
            override val type: Type = MApiTonWallet::class.java
            override val arguments: String = ArgumentsBuilder()
                .jsArray(publicKey, Integer::class.java)
                .string(network)
                .jsObject(version, MApiTonWalletVersion::class.java)
                .build()
        }

        class ImportLedgerWallet(
            network: String,
            walletInfo: MLedgerWalletInfo
        ) : ApiMethod<MImportedWalletResponse>() {
            override val name: String = "importLedgerWallet"
            override val type: Type = MImportedWalletResponse::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(network)
                .jsObject(walletInfo, MLedgerWalletInfo::class.java)
                .string(network)
                .string(null)
                .build()
        }
    }

    /* Wallet Data */
    object WalletData {
        class GetWalletBalance(
            chain: String,
            network: String,
            address: String,
        ) : ApiMethod<BigInteger>() {
            override val name: String = "getWalletBalance"
            override val type: Type = BigInteger::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(chain)
                .string(network)
                .string(address)
                .build()
        }

        class DecryptComment(
            accountId: String,
            encryptedComment: String,
            fromAddress: String,
            passcode: String
        ) : ApiMethod<String>() {
            override val name: String = "decryptComment"
            override val type: Type = String::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(encryptedComment)
                .string(fromAddress)
                .string(passcode)
                .build()
        }

        class FetchActivityDetails(
            accountId: String,
            activity: MApiTransaction,
        ) : ApiMethod<MApiTransaction>() {
            override val name: String = "fetchTonActivityDetails"
            override val type: Type = MApiTransaction::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .jsObject(activity, MApiTransaction::class.java)
                .build()
        }
    }

    object Settings {
        class FetchMnemonic(
            accountId: String,
            password: String
        ) : ApiMethod<Array<String>>() {
            override val name: String = "fetchMnemonic"
            override val type: Type = Array<String>::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(password)
                .build()
        }

        class ChangePassword(
            oldPasscode: String,
            newPasscode: String
        ) : ApiMethod<Nothing>() {
            override val name: String = "changePassword"
            override val type: Type = Nothing::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(oldPasscode)
                .string(newPasscode)
                .build()
        }
    }

    /* Transfer */

    object Transfer {
        class CheckTransactionDraft(
            chain: MBlockchain,
            options: MApiCheckTransactionDraftOptions
        ) : ApiMethod<MApiCheckTransactionDraftResult>() {
            override val name: String = "checkTransactionDraft"
            override val type: Type = MApiCheckTransactionDraftResult::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(chain.name)
                .jsObject(options, MApiCheckTransactionDraftOptions::class.java)
                .build()
        }

        fun SubmitTransfer(
            chain: MBlockchain,
            options: MApiSubmitTransferOptions
        ): ApiMethod<out MApiSubmitTransferResult> {
            return when (chain) {
                MBlockchain.ton -> SubmitTransferTON(options)
                MBlockchain.tron -> SubmitTransferTRON(options)
                else -> throw NotImplementedError()
            }
        }

        class SubmitTransferTON(
            options: MApiSubmitTransferOptions
        ) : ApiMethod<MApiSubmitTransferResult.Ton>() {
            override val name: String = "submitTransfer"
            override val type: Type = MApiSubmitTransferResult.Ton::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(MBlockchain.ton.name)
                .jsObject(options, MApiSubmitTransferOptions::class.java)
                .build()
        }

        class SubmitTransferTRON(
            options: MApiSubmitTransferOptions
        ) : ApiMethod<MApiSubmitTransferResult.Tron>() {
            override val name: String = "submitTransfer"
            override val type: Type = MApiSubmitTransferResult.Tron::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(MBlockchain.tron.name)
                .jsObject(options, MApiSubmitTransferOptions::class.java)
                .build()
        }

        class SignLedgerTransactions(
            accountId: String,
            transactions: List<ApiTransferToSign>,
            options: Options
        ) : ApiMethod<JSONArray>() {
            data class Options(
                val validUntil: Long?,
                val vestingAddress: String?
            )

            override val name: String = "signTransactions"
            override val type: Type = JSONArray::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .jsArray(transactions, ApiTransferToSign::class.java)
                .jsObject(options, Options::class.java)
                .build()
        }
    }


    /* Swap */

    object Swap {
        class SwapEstimate(
            accountId: String,
            request: MApiSwapEstimateRequest
        ) : ApiMethod<MApiSwapEstimateResponse>() {
            override val name: String = "swapEstimate"
            override val type: Type = MApiSwapEstimateResponse::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .jsObject(request, MApiSwapEstimateRequest::class.java)
                .build()
        }
    }


    /* Ton Connect */

    object DApp {
        class GetDapps(
            accountId: String
        ) : ApiMethod<List<ApiDapp>>() {
            override val name: String = "getDapps"
            override val type: Type =
                Types.newParameterizedType(List::class.java, ApiDapp::class.java)
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .build()
        }

        class StartSseConnection(
            request: Request
        ) : ApiMethod<ReturnStrategy?>() {
            override val name: String = "startSseConnection"
            override val type: Type = ReturnStrategy::class.java
            override val arguments: String = ArgumentsBuilder()
                .jsObject(request, Request::class.java)
                .build()

            @JsonClass(generateAdapter = true)
            data class Request(
                val url: String,
                val deviceInfo: DeviceInfo,
                val isFromInAppBrowser: Boolean? = null,
                val identifier: String? = null
            )
        }

        class ConfirmDappRequest(
            promiseId: String,
            password: String
        ) : ApiMethod<Unit>() {
            override val name: String = "confirmDappRequest"
            override val type: Type = Unit::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(promiseId)
                .string(password)
                .build()
        }

        class ConfirmLedgerDappRequest(
            promiseId: String,
            signedMessages: JSONArray
        ) : ApiMethod<Unit>() {
            override val name: String = "confirmDappRequest"
            override val type: Type = Unit::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(promiseId)
                .jsObject(signedMessages, JSONArray::class.java)
                .build()
        }

        class ConfirmDappRequestConnect(
            promiseId: String,
            request: Request
        ) : ApiMethod<Unit>() {
            override val name: String = "confirmDappRequestConnect"
            override val type: Type = Unit::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(promiseId)
                .jsObject(request, Request::class.java)
                .build()

            @JsonClass(generateAdapter = true)
            data class Request(
                val accountId: String? = null,
                val proofSignature: String? = null
            )
        }

        class CancelDappRequest(
            promiseId: String,
            reason: String?
        ) : ApiMethod<Unit>() {
            override val name: String = "cancelDappRequest"
            override val type: Type = Unit::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(promiseId)
                .apply { reason?.let { string(it) } }
                .build()
        }

        class DeleteDapp(
            accountId: String,
            origin: String
        ) : ApiMethod<Boolean>() {
            override val name: String = "deleteDapp"
            override val type: Type = Boolean::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(origin)
                .build()
        }

        class DeleteAllDapps(
            accountId: String
        ) : ApiMethod<Boolean>() {
            override val name: String = "deleteAllDapps"
            override val type: Type = Boolean::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .build()
        }


        object Inject {
            @JsonClass(generateAdapter = true)
            data class DAppArg(
                val url: String,
                val isUrlEnsured: Boolean,
                val accountId: String,
            )

            class TonConnectConnect(
                dApp: DAppArg,
                request: Any,
                requestId: Int
            ) : ApiMethod<JSONObject>() {
                override val name: String = "tonConnect_connect"
                override val type: Type = JSONObject::class.java
                override val arguments: String = ArgumentsBuilder()
                    .jsObject(dApp, DAppArg::class.java)
                    .jsObject(request, Any::class.java)
                    .number(requestId)
                    .build()
            }

            class TonConnectReconnect(
                dApp: DAppArg,
                requestId: Int
            ) : ApiMethod<JSONObject>() {
                override val name: String = "tonConnect_reconnect"
                override val type: Type = JSONObject::class.java
                override val arguments: String = ArgumentsBuilder()
                    .jsObject(dApp, DAppArg::class.java)
                    .number(requestId)
                    .build()
            }

            class TonConnectDisconnect(
                dApp: DAppArg,
                request: Request
            ) : ApiMethod<JSONObject>() {
                override val name: String = "tonConnect_disconnect"
                override val type: Type = JSONObject::class.java
                override val arguments: String = ArgumentsBuilder()
                    .jsObject(dApp, DAppArg::class.java)
                    .jsObject(request, Request::class.java)
                    .build()

                @JsonClass(generateAdapter = true)
                data class Request(
                    val method: String = "disconnect",
                    val params: List<Any> = emptyList(),
                    val id: String
                )
            }

            class TonConnectSendTransaction(
                dApp: DAppArg,
                request: Request
            ) : ApiMethod<JSONObject>() {
                override val name: String = "tonConnect_sendTransaction"
                override val type: Type = JSONObject::class.java
                override val arguments: String = ArgumentsBuilder()
                    .jsObject(dApp, DAppArg::class.java)
                    .jsObject(request, Request::class.java)
                    .build()

                @JsonClass(generateAdapter = true)
                data class Request(
                    val method: String = "sendTransaction",
                    val params: List<String>,
                    val id: String
                )
            }
        }
    }

    /* Domains */
    object Domains {
        class CheckDnsRenewalDraft(accountId: String, nfts: List<ApiNft>) :
            ApiMethod<CheckDnsRenewalDraft.Result>() {

            @JsonClass(generateAdapter = true)
            data class Result(val realFee: BigInteger)

            override val name: String = "checkDnsRenewalDraft"
            override val type: Type = Result::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .jsArray(nfts, ApiNft::class.java)
                .build()
        }

        class SubmitDnsRenewal(
            accountId: String,
            password: String,
            nfts: List<ApiNft>,
            realFee: BigInteger
        ) : ApiMethod<Any>() {
            override val name: String = "submitDnsRenewal"
            override val type: Type = Any::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(password)
                .jsArray(nfts, ApiNft::class.java)
                .bigInt(realFee)
                .build()
        }
    }

    /* Nft */
    object Nft {
        class CheckNftTransferDraft(
            options: MApiCheckNftDraftOptions
        ) : ApiMethod<MApiCheckTransactionDraftResult>() {
            override val name: String = "checkNftTransferDraft"
            override val type: Type = MApiCheckTransactionDraftResult::class.java
            override val arguments: String = ArgumentsBuilder()
                .jsObject(options, MApiCheckNftDraftOptions::class.java)
                .build()
        }

        class SubmitNftTransfer(
            accountId: String,
            passcode: String,
            nft: ApiNft,
            address: String,
            comment: String?,
            fee: BigInteger
        ) : ApiMethod<Any>() {
            override val name: String = "submitNftTransfers"
            override val type: Type = Any::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(passcode)
                .jsObject(arrayOf(nft.toDictionary()), Array<JSONObject>::class.java)
                .string(address)
                .string(comment)
                .bigInt(fee)
                .build()
        }

        class CheckNftOwnership(
            accountId: String,
            nftAddress: String,
        ) : ApiMethod<Any>() {
            override val name: String = "checkNftOwnership"
            override val type: Type = Boolean::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(nftAddress)
                .build()
        }
    }

    object Ledger {
        class SignTonProof(
            accountId: String,
            proofData: ApiTonConnectProof,
        ) : ApiMethod<String>() {
            override val name: String = "signTonProof"
            override val type: Type = String::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .jsObject(proofData, ApiTonConnectProof::class.java)
                .string("")
                .build()
        }
    }

    /* Staking */
    object Staking {
        class CheckStakeDraft(
            accountId: String,
            amount: BigInteger,
            state: StakingState
        ) : ApiMethod<MApiCheckStakeDraftResult>() {
            override val name: String = "checkStakeDraft"
            override val type: Type = MApiCheckStakeDraftResult::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .bigInt(amount)
                .jsObject(state, StakingState::class.java)
                .build()
        }

        class CheckUnstakeDraft(
            accountId: String,
            amount: BigInteger,
            state: StakingState
        ) : ApiMethod<MApiCheckStakeDraftResult>() {
            override val name: String = "checkUnstakeDraft"
            override val type: Type = MApiCheckStakeDraftResult::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .bigInt(amount)
                .jsObject(state, StakingState::class.java)
                .build()
        }

        class SubmitStakingClaimOrUnlock(
            accountId: String,
            password: String,
            state: StakingState,
            realFee: BigInteger
        ) : ApiMethod<Any>() {
            override val name: String = "submitStakingClaimOrUnlock"
            override val type: Type = Any::class.java
            override val arguments: String = ArgumentsBuilder()
                .string(accountId)
                .string(password)
                .jsObject(state, StakingState::class.java)
                .bigInt(realFee)
                .build()
        }
    }

    /* Notifications */
    object Notifications {
        class SubscribeNotifications(props: Props) : ApiMethod<JSONObject>() {

            data class Props(
                val userToken: String,
                val addresses: List<ApiNotificationAddress>,
                val platform: String = "android",
            )

            override val name: String = "subscribeNotifications"
            override val type: Type = JSONObject::class.java
            override val arguments: String = ArgumentsBuilder()
                .jsObject(props, Props::class.java)
                .build()
        }

        class UnsubscribeNotifications(props: Props) : ApiMethod<Any>() {

            data class Props(
                val userToken: String,
                val addresses: List<ApiNotificationAddress>
            )

            override val name: String = "unsubscribeNotifications"
            override val type: Type = Any::class.java
            override val arguments: String = ArgumentsBuilder()
                .jsObject(props, Props::class.java)
                .build()
        }
    }
}
