import { Cell, internal, SendMode } from '@ton/core';

import type { DieselStatus } from '../../../global/types';
import type Deferred from '../../../util/Deferred';
import type { CheckTransactionDraftOptions } from '../../methods/types';
import type {
  ApiAccountWithTon,
  ApiAnyDisplayError,
  ApiNetwork,
  ApiSignedTransfer,
  ApiToken,
} from '../../types';
import type {
  AnyPayload,
  ApiCheckMultiTransactionDraftResult,
  ApiCheckTransactionDraftResult,
  ApiEmulationWithFallbackResult,
  ApiFetchEstimateDieselResult,
  ApiSubmitMultiTransferResult,
  ApiSubmitTransferOptions,
  ApiSubmitTransferTonResult,
  ApiSubmitTransferWithDieselResult,
  PreparedTransactionToSign,
  TonTransferParams,
} from './types';
import type { TonWallet } from './util/tonCore';
import { ApiTransactionDraftError, ApiTransactionError } from '../../types';

import { DEFAULT_FEE, DIESEL_ADDRESS } from '../../../config';
import { parseAccountId } from '../../../util/account';
import { bigintMultiplyToNumber } from '../../../util/bigint';
import { fromDecimal, toDecimal } from '../../../util/decimals';
import { getDieselTokenAmount, isDieselAvailable } from '../../../util/fee/transferFee';
import { omit, pick, split } from '../../../util/iteratees';
import { logDebug, logDebugError } from '../../../util/logs';
import { pause } from '../../../util/schedulers';
import { getMaxMessagesInTransaction } from '../../../util/ton/transfer';
import { decryptMessageComment, encryptMessageComment } from './util/encryption';
import { sendExternal } from './util/sendExternal';
import { getSigner } from './util/signer';
import {
  commentToBytes,
  getOurFeePayload,
  getTonClient,
  getWalletPublicKey,
  packBytesAsSnake,
  packBytesAsSnakeCell,
  packBytesAsSnakeForEncryptedData,
  parseAddress,
  parseBase64,
  parseStateInitCell,
} from './util/tonCore';
import { fetchStoredTonAccount, fetchStoredTonWallet } from '../../common/accounts';
import { callBackendGet } from '../../common/backend';
import { waitAndCreatePendingTransfer } from '../../common/pendingTransfers';
import { getTokenByAddress } from '../../common/tokens';
import { base64ToBytes } from '../../common/utils';
import { MINUTE, SEC } from '../../constants';
import { ApiServerError, handleServerError } from '../../errors';
import { checkHasTransaction, fetchNewestActionId } from './activities';
import { resolveAddress } from './address';
import { fetchKeyPair, fetchPrivateKey } from './auth';
import {
  ATTEMPTS,
  FEE_FACTOR,
  LEDGER_VESTING_SUBWALLET_ID,
  TOKEN_TRANSFER_FORWARD_AMOUNT,
  TRANSFER_TIMEOUT_SEC,
} from './constants';
import { emulateTransaction } from './emulation';
import {
  buildTokenTransfer,
  calculateTokenBalanceWithMintless,
  getTokenBalanceWithMintless,
  getToncoinAmountForTransfer,
} from './tokens';
import { buildWallet, getContractInfo, getTonWallet, getWalletBalance, getWalletInfo, getWalletSeqno } from './wallet';

const WAIT_TRANSFER_TIMEOUT = MINUTE;
const WAIT_PAUSE = SEC;

const MAX_BALANCE_WITH_CHECK_DIESEL = 100000000n; // 0.1 TON
const PENDING_DIESEL_TIMEOUT_SEC = 15 * 60; // 15 min

const DIESEL_NOT_AVAILABLE: ApiFetchEstimateDieselResult = {
  status: 'not-available',
  nativeAmount: 0n,
  remainingFee: 0n,
  realFee: 0n,
};

export async function checkTransactionDraft(
  options: CheckTransactionDraftOptions,
): Promise<ApiCheckTransactionDraftResult> {
  const {
    accountId,
    amount = 0n,
    tokenAddress,
    shouldEncrypt,
    isBase64Data,
    stateInit: stateInitString,
    forwardAmount,
    allowGasless,
  } = options;
  let { toAddress, data } = options;

  const { network } = parseAccountId(accountId);

  let result: ApiCheckTransactionDraftResult = {};

  try {
    result = await checkToAddress(network, toAddress);
    if ('error' in result) {
      return result;
    }

    toAddress = result.resolvedAddress!;

    const { isInitialized } = await getContractInfo(network, toAddress);

    if (stateInitString && !isBase64Data) {
      return {
        ...result,
        error: ApiTransactionDraftError.StateInitWithoutBin,
      };
    }

    let stateInit;

    if (stateInitString) {
      try {
        stateInit = Cell.fromBase64(stateInitString);
      } catch {
        return {
          ...result,
          error: ApiTransactionDraftError.InvalidStateInit,
        };
      }
    }

    if (result.isBounceable && !isInitialized && !stateInit) {
      result.isToAddressNew = !(await checkHasTransaction(network, toAddress));
      return {
        ...result,
        error: ApiTransactionDraftError.InactiveContract,
      };
    }

    result.resolvedAddress = toAddress;

    if (amount < 0n) {
      return {
        ...result,
        error: ApiTransactionDraftError.InvalidAmount,
      };
    }

    const account = await fetchStoredTonAccount(accountId);
    const wallet = getTonWallet(account.ton);

    if (typeof data === 'string' && isBase64Data) {
      data = base64ToBytes(data);
    }

    if (data && typeof data === 'string' && shouldEncrypt) {
      const toPublicKey = await getWalletPublicKey(network, toAddress);
      if (!toPublicKey) {
        return {
          ...result,
          error: ApiTransactionDraftError.WalletNotInitialized,
        };
      }
    }

    const { address, isInitialized: isWalletInitialized } = account.ton;

    if (data && typeof data === 'string' && !isBase64Data) {
      data = commentToBytes(data);
    }

    let toncoinAmount: bigint;
    const toncoinBalance = await getWalletBalance(network, wallet);
    let balance: bigint;
    let fee: bigint;
    let realFee: bigint;

    if (!tokenAddress) {
      balance = toncoinBalance;
      toncoinAmount = amount;
      fee = 0n;
      realFee = 0n;

      if (data instanceof Uint8Array) {
        data = shouldEncrypt ? packBytesAsSnakeForEncryptedData(data) : packBytesAsSnake(data);
      }
    } else {
      const tokenTransfer = await buildTokenTransfer({
        network,
        tokenAddress,
        fromAddress: address,
        toAddress,
        amount,
        payload: data,
        forwardAmount,
      });
      ({ amount: toncoinAmount, toAddress, payload: data } = tokenTransfer);
      const { realAmount: realToncoinAmount, isTokenWalletDeployed, mintlessTokenBalance } = tokenTransfer;

      // When the token is transferred, actually some TON is transferred, and the token sits inside the payload.
      // From the user perspective, this TON amount is a fee.
      fee = toncoinAmount;
      realFee = realToncoinAmount;

      const tokenWalletAddress = toAddress;
      balance = await calculateTokenBalanceWithMintless(
        network, tokenWalletAddress, isTokenWalletDeployed, mintlessTokenBalance,
      );
    }

    const isFullTonTransfer = !tokenAddress && toncoinBalance === amount;

    const signingResult = await signTransaction({
      isMockSigning: true,
      accountId,
      account,
      messages: [{
        toAddress,
        amount: toncoinAmount,
        payload: data,
        stateInit,
        hints: {
          tokenAddress,
        },
      }],
      doPayFeeFromAmount: isFullTonTransfer,
    });
    if ('error' in signingResult) {
      return {
        ...result,
        error: signingResult.error,
      };
    }

    // todo: Use `received` from the emulation to calculate the real fee. Check what happens when the receiver is the same wallet.
    const { networkFee } = applyFeeFactorToEmulationResult(
      await emulateTransactionWithFallback(network, wallet, signingResult.transaction, isWalletInitialized),
    );
    fee += networkFee;
    realFee += networkFee;
    result.fee = fee;
    result.realFee = realFee;
    result.diesel = DIESEL_NOT_AVAILABLE;

    let isEnoughBalance: boolean;

    if (!tokenAddress) {
      isEnoughBalance = toncoinBalance >= fee + (isFullTonTransfer ? 0n : amount);
    } else {
      const canTransferGasfully = toncoinBalance >= fee;

      if (allowGasless) {
        result.diesel = await getDiesel({
          accountId,
          tokenAddress,
          canTransferGasfully,
          toncoinBalance,
          tokenBalance: balance,
        });
      }

      if (isDieselAvailable(result.diesel)) {
        isEnoughBalance = amount + getDieselTokenAmount(result.diesel) <= balance;
      } else {
        isEnoughBalance = canTransferGasfully && amount <= balance;
      }
    }

    return isEnoughBalance ? result : {
      ...result,
      error: ApiTransactionDraftError.InsufficientBalance,
    };
  } catch (err: any) {
    return {
      ...handleServerError(err),
      ...result,
    };
  }
}

function estimateDiesel(
  address: string,
  tokenAddress: string,
  toncoinAmount: string,
  isW5?: boolean,
  isStars?: boolean,
) {
  return callBackendGet<{
    status: DieselStatus;
    // The amount is defined only when the status is "available" or "stars-fee": https://github.com/mytonwallet-org/mytonwallet-backend/blob/44c1bf43fb776286152db8901b45fe8341752e35/src/endpoints/diesel.ts#L163
    amount?: string;
    pendingCreatedAt?: string;
  }>('/diesel/estimate', {
    address, tokenAddress, toncoinAmount, isW5, isStars,
  });
}

export async function checkToAddress(network: ApiNetwork, toAddress: string) {
  const result: {
    addressName?: string;
    isScam?: boolean;
    resolvedAddress?: string;
    isToAddressNew?: boolean;
    isBounceable?: boolean;
    isMemoRequired?: boolean;
  } = {};

  const resolved = await resolveAddress(network, toAddress);
  if (resolved === 'dnsNotResolved') return { ...result, error: ApiTransactionDraftError.DomainNotResolved };
  if (resolved === 'invalidAddress') return { ...result, error: ApiTransactionDraftError.InvalidToAddress };
  result.addressName = resolved.name;
  result.resolvedAddress = resolved.address;
  result.isMemoRequired = resolved.isMemoRequired;
  result.isScam = resolved.isScam;
  toAddress = resolved.address;

  const { isUserFriendly, isTestOnly, isBounceable } = parseAddress(toAddress);

  result.isBounceable = isBounceable;

  const regex = /[+=/]/;
  const isUrlSafe = !regex.test(toAddress);

  if (!isUserFriendly || !isUrlSafe || (network === 'mainnet' && isTestOnly)) {
    return {
      ...result,
      error: ApiTransactionDraftError.InvalidAddressFormat,
    };
  }

  return result;
}

export async function submitTransfer(options: ApiSubmitTransferOptions): Promise<ApiSubmitTransferTonResult> {
  const {
    accountId,
    password,
    amount,
    tokenAddress,
    shouldEncrypt,
    isBase64Data,
    forwardAmount = TOKEN_TRANSFER_FORWARD_AMOUNT,
  } = options;
  let { stateInit } = options;

  let { toAddress, data } = options;

  const { network } = parseAccountId(accountId);

  try {
    const account = await fetchStoredTonAccount(accountId);
    if (account.type === 'view') throw new Error('Not available for view-only accounts');
    const { address: fromAddress, isInitialized } = account.ton;
    const wallet = getTonWallet(account.ton);
    const { publicKey, secretKey } = account.type === 'ledger'
      ? {}
      : (await fetchKeyPair(accountId, password, account))!;

    let encryptedComment: string | undefined;

    if (typeof data === 'string') {
      ({ payload: data, encryptedComment } = await stringToPayload({
        network, toAddress, fromAddress, data, secretKey, publicKey, shouldEncrypt, isBase64Data,
      }));
    }

    let toncoinAmount: bigint;

    if (!tokenAddress) {
      toncoinAmount = amount;

      if (data instanceof Uint8Array) {
        data = shouldEncrypt ? packBytesAsSnakeForEncryptedData(data) : packBytesAsSnake(data);
      }
    } else {
      ({
        amount: toncoinAmount,
        toAddress,
        payload: data,
        stateInit,
      } = await buildTokenTransfer({
        network,
        tokenAddress,
        fromAddress,
        toAddress,
        amount,
        payload: data,
        forwardAmount,
      }));
    }

    const { pendingTransfer } = await waitAndCreatePendingTransfer(network, fromAddress);

    const toncoinBalance = await getWalletBalance(network, wallet);
    const isFullTonTransfer = !tokenAddress && toncoinBalance === amount;

    const signingResult = await signTransaction({
      accountId,
      account,
      messages: [{
        toAddress,
        amount: toncoinAmount,
        payload: data,
        stateInit,
        hints: {
          tokenAddress,
        },
      }],
      privateKey: secretKey,
      doPayFeeFromAmount: isFullTonTransfer,
    });
    if ('error' in signingResult) return signingResult;
    const { seqno, transaction } = signingResult;

    const { networkFee } = await emulateTransactionWithFallback(network, wallet, transaction, isInitialized);

    const isEnoughBalance = isFullTonTransfer
      ? toncoinBalance > networkFee
      : toncoinBalance >= toncoinAmount + networkFee;

    if (!isEnoughBalance) {
      return { error: ApiTransactionError.InsufficientBalance };
    }

    const client = getTonClient(network);
    const { msgHash, boc, msgHashNormalized } = await sendExternal(client, wallet, transaction);

    void retrySendBoc(network, fromAddress, boc, seqno, pendingTransfer);

    return {
      amount,
      seqno,
      encryptedComment,
      toAddress,
      msgHash,
      msgHashNormalized,
      toncoinAmount,
    };
  } catch (err: any) {
    logDebugError('submitTransfer', err);

    return { error: resolveTransactionError(err) };
  }
}

export async function submitTransferWithDiesel(options: {
  accountId: string;
  password: string;
  toAddress: string;
  amount: bigint;
  data?: AnyPayload;
  tokenAddress: string;
  shouldEncrypt?: boolean;
  dieselAmount: bigint;
  isGaslessWithStars?: boolean;
}): Promise<ApiSubmitTransferWithDieselResult> {
  try {
    const {
      toAddress,
      amount,
      accountId,
      password,
      tokenAddress,
      shouldEncrypt,
      dieselAmount,
      isGaslessWithStars,
    } = options;

    let { data } = options;

    const { network } = parseAccountId(accountId);

    const [{ address: fromAddress, version }, keyPair] = await Promise.all([
      fetchStoredTonWallet(accountId),
      fetchKeyPair(accountId, password),
    ]);

    const { publicKey, secretKey } = keyPair!;

    let encryptedComment: string | undefined;

    if (typeof data === 'string') {
      ({ payload: data, encryptedComment } = await stringToPayload({
        network, toAddress, fromAddress, data, secretKey, publicKey, shouldEncrypt,
      }));
    }

    const messages: TonTransferParams[] = [
      await buildTokenTransfer({
        network,
        tokenAddress,
        fromAddress,
        toAddress,
        amount,
        payload: data,
      }),
    ];

    if (!isGaslessWithStars) {
      messages.push(
        await buildTokenTransfer({
          network,
          tokenAddress,
          fromAddress,
          toAddress: DIESEL_ADDRESS,
          amount: dieselAmount,
          shouldSkipMintless: true,
          payload: getOurFeePayload(),
        }),
      );
    }

    const result = await submitMultiTransfer({
      accountId,
      password,
      messages,
      isGasless: true,
    });

    return {
      ...result,
      encryptedComment,
      withW5Gasless: version === 'W5',
    };
  } catch (err) {
    logDebugError('submitTransferWithDiesel', err);

    return { error: resolveTransactionError(err) };
  }
}

async function stringToPayload({
  network, toAddress, data, shouldEncrypt, publicKey, secretKey, fromAddress, isBase64Data,
}: {
  network: ApiNetwork;
  data: string;
  shouldEncrypt?: boolean;
  toAddress: string;
  publicKey?: Uint8Array;
  secretKey?: Uint8Array;
  fromAddress: string;
  isBase64Data?: boolean;
}): Promise<{
    payload?: Uint8Array | Cell;
    encryptedComment?: string;
  }> {
  let payload: Uint8Array | Cell | undefined;
  let encryptedComment: string | undefined;

  if (!data) {
    payload = undefined;
  } else if (isBase64Data) {
    payload = parseBase64(data);
  } else if (shouldEncrypt) {
    if (!publicKey || !secretKey) {
      throw new Error();
    }
    const toPublicKey = (await getWalletPublicKey(network, toAddress))!;
    payload = await encryptMessageComment(data, publicKey, toPublicKey, secretKey, fromAddress);
    encryptedComment = Buffer.from(payload.slice(4)).toString('base64');
  } else {
    payload = commentToBytes(data);
  }

  return { payload, encryptedComment };
}

export function resolveTransactionError(error: any): ApiAnyDisplayError | string {
  if (error instanceof ApiServerError) {
    if (error.message.includes('exitcode=35,')) {
      return ApiTransactionError.IncorrectDeviceTime;
    } else if (error.statusCode === 400) {
      return error.message;
    } else if (error.displayError) {
      return error.displayError;
    }
  }
  return ApiTransactionError.UnsuccesfulTransfer;
}

export async function getAccountNewestActionId(accountId: string) {
  const { network } = parseAccountId(accountId);
  const { address } = await fetchStoredTonWallet(accountId);

  return fetchNewestActionId(network, address);
}

export async function checkMultiTransactionDraft(
  accountId: string,
  messages: TonTransferParams[],
  withDiesel?: boolean,
): Promise<ApiCheckMultiTransactionDraftResult> {
  let totalAmount: bigint = 0n;

  const { network } = parseAccountId(accountId);
  const account = await fetchStoredTonAccount(accountId);

  try {
    for (const { toAddress, amount } of messages) {
      if (amount < 0n) {
        return { error: ApiTransactionDraftError.InvalidAmount };
      }

      const isMainnet = network === 'mainnet';
      const { isValid, isTestOnly } = parseAddress(toAddress);

      if (!isValid || (isMainnet && isTestOnly)) {
        return { error: ApiTransactionDraftError.InvalidToAddress };
      }
      totalAmount += amount;
    }

    const wallet = getTonWallet(account.ton);
    const { balance } = await getWalletInfo(network, wallet);

    const signingResult = await signTransaction({ isMockSigning: true, accountId, account, messages });
    if ('error' in signingResult) return signingResult;

    const emulation = applyFeeFactorToEmulationResult(
      await emulateTransactionWithFallback(network, wallet, signingResult.transaction, account.ton.isInitialized),
    );
    const result = { emulation };

    // TODO Should `totalAmount` be `0` for `withDiesel`?
    if (!withDiesel && balance < totalAmount + result.emulation.networkFee) {
      return { ...result, error: ApiTransactionDraftError.InsufficientBalance };
    }

    return result;
  } catch (err: any) {
    return handleServerError(err);
  }
}

export type GaslessType = 'diesel' | 'w5';

interface SubmitMultiTransferOptions {
  accountId: string;
  password: string;
  messages: TonTransferParams[];
  expireAt?: number;
  isGasless?: boolean;
}

export async function submitMultiTransfer({
  accountId, password, messages, expireAt, isGasless,
}: SubmitMultiTransferOptions): Promise<ApiSubmitMultiTransferResult> {
  const { network } = parseAccountId(accountId);

  const account = await fetchStoredTonAccount(accountId);
  const { address: fromAddress, isInitialized, version } = account.ton;
  const { pendingTransfer } = await waitAndCreatePendingTransfer(network, fromAddress);

  try {
    const wallet = getTonWallet(account.ton);
    if (account.type === 'view') throw new Error('Not available for view-only accounts');
    const privateKey = account.type === 'ledger' ? undefined : await fetchPrivateKey(accountId, password, account);

    let totalAmount = 0n;
    messages.forEach((message) => {
      totalAmount += BigInt(message.amount);
    });

    const { balance } = await getWalletInfo(network, wallet);

    const gaslessType = isGasless ? version === 'W5' ? 'w5' : 'diesel' : undefined;
    const withW5Gasless = gaslessType === 'w5';

    const signingResult = await signTransaction({
      accountId,
      account,
      messages,
      expireAt: withW5Gasless
        ? Math.round(Date.now() / 1000) + PENDING_DIESEL_TIMEOUT_SEC
        : expireAt,
      privateKey,
      shouldBeInternal: withW5Gasless,
    });
    if ('error' in signingResult) return signingResult;
    const { seqno, transaction } = signingResult;

    if (!isGasless) {
      const { networkFee } = await emulateTransactionWithFallback(network, wallet, transaction, isInitialized);
      if (balance < totalAmount + networkFee) {
        return { error: ApiTransactionError.InsufficientBalance };
      }
    }

    const client = getTonClient(network);
    const { msgHash, boc, paymentLink, msgHashNormalized } = await sendExternal(
      client, wallet, transaction, gaslessType,
    );

    if (!isGasless) {
      void retrySendBoc(network, fromAddress, boc, seqno, pendingTransfer);
    } else {
      // TODO: Wait for gasless transfer
      pendingTransfer.resolve();
    }

    const clearedMessages = messages.map((message) => {
      if (typeof message.payload !== 'string' && typeof message.payload !== 'undefined') {
        return omit(message, ['payload']);
      }
      return message;
    });

    return {
      seqno,
      amount: totalAmount.toString(),
      messages: clearedMessages,
      boc,
      msgHash,
      msgHashNormalized,
      paymentLink,
      withW5Gasless,
    };
  } catch (err) {
    pendingTransfer.resolve();

    logDebugError('submitMultiTransfer', err);
    return { error: resolveTransactionError(err) };
  }
}

interface SignTransactionOptions {
  accountId: string;
  /** Cache (pass it to avoid fetching it) */
  account?: ApiAccountWithTon;
  messages: TonTransferParams[];
  doPayFeeFromAmount?: boolean;
  /** Set `true` if you only need to emulate the transaction */
  isMockSigning?: boolean;
  privateKey?: Uint8Array;
  /** Unix seconds */
  expireAt?: number;
  /** If true, will sign the transaction as an internal message instead of external. Not supported by Ledger. */
  shouldBeInternal?: boolean;
  /** Used for specific transactions on vesting.ton.org */
  ledgerVestingAddress?: string;
}

async function signTransaction(options: Omit<SignTransactionOptions, 'allowManyTransactions'>) {
  const result = await signTransactions({ ...options, allowOnlyOneTransaction: true });
  if ('error' in result) return result;
  return result[0];
}

/**
 * A universal function for signing any number of transactions in any account type.
 *
 * If the account doesn't support signing all the given messages in a single transaction, will produce multiple signed
 * transactions. If you need exactly 1 signed transaction, use `allowOnlyOneTransaction` or `signTransaction` (the
 * function will throw an error in case of multiple transactions).
 */
export async function signTransactions({
  accountId,
  account,
  messages,
  doPayFeeFromAmount,
  isMockSigning,
  privateKey,
  expireAt = Math.round(Date.now() / 1000) + TRANSFER_TIMEOUT_SEC,
  shouldBeInternal,
  allowOnlyOneTransaction,
  ledgerVestingAddress,
}: SignTransactionOptions & { allowOnlyOneTransaction?: boolean }) {
  const { network } = parseAccountId(accountId);
  account ??= await fetchStoredTonAccount(accountId);
  const seqno = await getWalletSeqno(network, ledgerVestingAddress ?? account.ton.address);
  const messagesByTransaction = split(messages, getMaxMessagesInTransaction(account));

  // Each transaction signing with Ledger requires a manual user action, so, to improve the UX, all checks should be done
  // before the signing. For the same reason the transactions are signed by Ledger all at once instead of one by one.
  if (allowOnlyOneTransaction && messagesByTransaction.length !== 1) {
    throw new Error(
      messagesByTransaction.length === 0
        ? 'No messages to sign'
        : `Too many messages for 1 transaction (${messages.length} messages given)`,
    );
  }

  const transactionsToSign = messagesByTransaction.map((transactionMessages, index) => {
    if (!isMockSigning) {
      logDebug('Signing transaction', {
        seqno,
        messages: transactionMessages.map((msg) => pick(msg, ['toAddress', 'amount'])),
      });
    }

    return makePreparedTransactionToSign({
      messages: transactionMessages,
      seqno: seqno + index,
      doPayFeeFromAmount,
      expireAt,
      shouldBeInternal,
    });
  });

  const signer = getSigner(
    network,
    account,
    privateKey,
    isMockSigning,
    ledgerVestingAddress ? LEDGER_VESTING_SUBWALLET_ID : undefined,
  );

  const signedTransactions = await signer.signTransactions(transactionsToSign);
  if ('error' in signedTransactions) return signedTransactions;

  return signedTransactions.map((transaction, index) => ({
    seqno: transactionsToSign[index].seqno,
    transaction,
  }));
}

async function retrySendBoc(
  network: ApiNetwork,
  address: string,
  boc: string,
  seqno: number,
  pendingTransfer?: Deferred,
) {
  const tonClient = getTonClient(network);
  const waitUntil = Date.now() + WAIT_TRANSFER_TIMEOUT;

  while (Date.now() < waitUntil) {
    const [error, walletInfo] = await Promise.all([
      tonClient.sendFile(boc).catch((err) => String(err)),
      getWalletInfo(network, address).catch(() => undefined),
    ]);

    // Errors mean that `seqno` was changed or not enough of balance
    if (error?.match(/(exitcode=33|exitcode=133|inbound external message rejected by account)/)) {
      break;
    }

    // seqno here may change before exit code appears
    if (walletInfo && walletInfo.seqno > seqno) {
      break;
    }

    await pause(WAIT_PAUSE);
  }

  pendingTransfer?.resolve();
}

async function emulateTransactionWithFallback(
  network: ApiNetwork,
  wallet: TonWallet,
  transaction: Cell,
  isInitialized?: boolean,
): Promise<ApiEmulationWithFallbackResult> {
  try {
    const emulation = await emulateTransaction(network, wallet, transaction, isInitialized);
    return { isFallback: false, ...emulation };
  } catch (err) {
    logDebugError('Failed to emulate a transaction', err);
  }

  // Falling back to the legacy fee estimation method just in case.
  // It doesn't support estimating more than 20 messages (inside the transaction) at once.
  // eslint-disable-next-line no-null/no-null
  const { code = null, data = null } = !isInitialized ? wallet.init : {};
  const { source_fees: fees } = await getTonClient(network).estimateExternalMessageFee(wallet.address, {
    body: transaction,
    initCode: code,
    initData: data,
    ignoreSignature: true,
  });
  const networkFee = BigInt(fees.in_fwd_fee + fees.storage_fee + fees.gas_fee + fees.fwd_fee);
  return { isFallback: true, networkFee };
}

export async function sendSignedMessages(accountId: string, messages: ApiSignedTransfer[]) {
  const { network } = parseAccountId(accountId);
  const { address: fromAddress, publicKey, version } = await fetchStoredTonWallet(accountId);
  const client = getTonClient(network);
  const wallet = buildWallet(publicKey!, version);

  const attempts = ATTEMPTS + messages.length;
  let index = 0;
  let attempt = 0;

  let firstBoc: string | undefined;

  const msgHashes: string[] = [];
  const msgHashesNormalized: string[] = [];
  let pendingTransfer: Deferred | undefined;

  while (index < messages.length && attempt < attempts) {
    const { base64, seqno } = messages[index];
    try {
      ({ pendingTransfer } = await waitAndCreatePendingTransfer(network, fromAddress));

      const { msgHash, boc, msgHashNormalized } = await sendExternal(client, wallet, Cell.fromBase64(base64));
      msgHashes.push(msgHash);
      msgHashesNormalized.push(msgHashNormalized);

      if (index === 0) {
        firstBoc = boc;
      }

      void retrySendBoc(network, fromAddress, boc, seqno, pendingTransfer);

      index++;
    } catch (err) {
      pendingTransfer?.resolve();
      logDebugError('sendSignedMessages', err);
    }
    attempt++;
  }

  return { successNumber: index, firstBoc, msgHashes, msgHashesNormalized };
}

export async function decryptComment(
  accountId: string, encryptedComment: string, fromAddress: string, password: string,
) {
  const keyPair = await fetchKeyPair(accountId, password);
  if (!keyPair) {
    return undefined;
  }

  const { secretKey, publicKey } = keyPair;

  const buffer = Buffer.from(encryptedComment, 'base64');

  return decryptMessageComment(buffer, publicKey, secretKey, fromAddress);
}

/**
 * The goal of the function is acting like `checkTransactionDraft` but return only the diesel information
 */
export function fetchEstimateDiesel(
  accountId: string, tokenAddress: string,
): Promise<ApiFetchEstimateDieselResult> {
  return getDiesel({
    accountId,
    tokenAddress,
    // We pass `false` because `fetchEstimateDiesel` assumes that the transfer is gasless anyway
    canTransferGasfully: false,
  });
}

/**
 * Decides whether the transfer must be gasless and fetches the diesel estimate from the backend.
 */
async function getDiesel({
  accountId,
  tokenAddress,
  canTransferGasfully,
  toncoinBalance,
  tokenBalance,
}: {
  accountId: string;
  tokenAddress: string;
  canTransferGasfully: boolean;
  // The below fields allow to avoid network requests if you already have these data
  toncoinBalance?: bigint;
  tokenBalance?: bigint;
}): Promise<ApiFetchEstimateDieselResult> {
  const { network } = parseAccountId(accountId);
  if (network !== 'mainnet') return DIESEL_NOT_AVAILABLE;

  const storedTonWallet = await fetchStoredTonWallet(accountId);
  const wallet = getTonWallet(storedTonWallet);

  const token = getTokenByAddress(tokenAddress);
  if (!token.isGaslessEnabled && !token.isStarsEnabled) return DIESEL_NOT_AVAILABLE;

  const { address, version } = storedTonWallet;
  toncoinBalance ??= await getWalletBalance(network, wallet);
  const fee = getDieselToncoinFee(token);
  const toncoinNeeded = fee.amount - toncoinBalance;

  if (toncoinBalance >= MAX_BALANCE_WITH_CHECK_DIESEL || toncoinNeeded <= 0n) return DIESEL_NOT_AVAILABLE;

  const rawDiesel = await estimateDiesel(
    address,
    tokenAddress,
    toDecimal(toncoinNeeded),
    version === 'W5',
    fee.isStars,
  );
  const diesel: ApiFetchEstimateDieselResult = {
    status: rawDiesel.status,
    amount: rawDiesel.amount === undefined
      ? undefined
      : fromDecimal(rawDiesel.amount, rawDiesel.status === 'stars-fee' ? 0 : token.decimals),
    nativeAmount: toncoinNeeded,
    remainingFee: toncoinBalance,
    realFee: fee.realFee,
  };

  const tokenAmount = getDieselTokenAmount(diesel);
  if (tokenAmount === 0n) {
    return diesel;
  }

  tokenBalance ??= await getTokenBalanceWithMintless(network, address, tokenAddress);
  const canPayDiesel = tokenBalance >= tokenAmount;
  const isAwaitingNotExpiredPrevious = Boolean(
    rawDiesel.pendingCreatedAt
    && Date.now() - new Date(rawDiesel.pendingCreatedAt).getTime() < PENDING_DIESEL_TIMEOUT_SEC * SEC,
  );

  // When both TON and diesel are insufficient, we want to show the TON fee
  const shouldBeGasless = (!canTransferGasfully && canPayDiesel) || isAwaitingNotExpiredPrevious;
  return shouldBeGasless ? diesel : DIESEL_NOT_AVAILABLE;
}

/**
 * Guesses the total TON fee (including the gas attached to the transaction) that will be spent on a diesel transfer.
 *
 * `amount` is what will be taken from the wallet;
 * `realFee` is approximately what will be actually spent (the rest will return in the excess);
 * `isStars` tells whether the fee is estimated considering that the diesel will be paid in stars.
 */
function getDieselToncoinFee(token: ApiToken) {
  const isStars = !token.isGaslessEnabled && token.isStarsEnabled;
  let { amount, realAmount: realFee } = getToncoinAmountForTransfer(token, false);

  // Multiplying by 2 because the diesel transfer has 2 transactions:
  // - for the transfer itself,
  // - for sending the diesel to the MTW wallet.
  if (!isStars) {
    amount *= 2n;
    realFee *= 2n;
  }

  amount += DEFAULT_FEE;
  realFee += DEFAULT_FEE;

  return { amount, realFee, isStars };
}

function applyFeeFactorToEmulationResult(estimation: ApiEmulationWithFallbackResult): ApiEmulationWithFallbackResult {
  estimation = {
    ...estimation,
    networkFee: bigintMultiplyToNumber(estimation.networkFee, FEE_FACTOR),
  };

  if ('byTransactionIndex' in estimation) {
    estimation.byTransactionIndex = estimation.byTransactionIndex.map((transaction) => ({
      ...transaction,
      networkFee: bigintMultiplyToNumber(transaction.networkFee, FEE_FACTOR),
    }));
  }

  return estimation;
}

function makePreparedTransactionToSign({
  messages,
  seqno,
  doPayFeeFromAmount,
  expireAt,
  shouldBeInternal,
}: Pick<SignTransactionOptions, 'messages' | 'doPayFeeFromAmount' | 'expireAt' | 'shouldBeInternal'>
  & { seqno: number }): PreparedTransactionToSign {
  return {
    authType: shouldBeInternal ? 'internal' : undefined,
    seqno,
    messages: messages.map((message) => {
      const { amount, toAddress, stateInit } = message;
      return internal({
        value: amount,
        to: toAddress,
        body: getPayloadFromTransfer(message),
        bounce: parseAddress(toAddress).isBounceable,
        init: parseStateInitCell(stateInit),
      });
    }),
    sendMode: (doPayFeeFromAmount ? SendMode.CARRY_ALL_REMAINING_BALANCE : SendMode.PAY_GAS_SEPARATELY)
      // It's important to add IGNORE_ERRORS to every transaction. Otherwise, failed transactions may repeat and drain
      // the wallet balance: https://docs.ton.org/v3/documentation/smart-contracts/message-management/sending-messages#behavior-without-2-flag
      + SendMode.IGNORE_ERRORS,
    timeout: expireAt,
    hints: messages[0].hints, // Currently hints are used only by Ledger, which has only 1 message per transaction
  };
}

function getPayloadFromTransfer(
  { payload, isBase64Payload }: Pick<TonTransferParams, 'payload' | 'isBase64Payload'>,
): Cell | undefined {
  if (payload === undefined) {
    return undefined;
  }

  if (payload instanceof Cell) {
    return payload;
  }

  if (typeof payload === 'string') {
    if (isBase64Payload) {
      return Cell.fromBase64(payload);
    }

    // This is what @ton/core does under the hood when a string payload is passed to `internal()`
    return packBytesAsSnakeCell(commentToBytes(payload));
  }

  if (payload instanceof Uint8Array) {
    return payload.length ? packBytesAsSnakeCell(payload) : undefined;
  }

  throw new TypeError(`Unexpected payload type ${typeof payload}`);
}
