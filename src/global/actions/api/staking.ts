import type { ApiEthenaStakingState, ApiJettonStakingState } from '../../../api/types';
import { StakingState } from '../../types';

import { getDoesUsePinPad } from '../../../util/biometrics';
import { getTonStakingFees } from '../../../util/fee/getTonOperationFees';
import { vibrateOnError, vibrateOnSuccess } from '../../../util/haptics';
import { callActionInMain, callActionInNative } from '../../../util/multitab';
import { pause } from '../../../util/schedulers';
import { getIsActiveStakingState, getIsLongUnstake } from '../../../util/staking';
import { IS_DELEGATED_BOTTOM_SHEET, IS_DELEGATING_BOTTOM_SHEET } from '../../../util/windowEnvironment';
import { callApi } from '../../../api';
import { closeAllOverlays } from '../../helpers/misc';
import { addActionHandler, getGlobal, setGlobal } from '../../index';
import {
  clearCurrentStaking,
  clearIsPinAccepted,
  resetHardware,
  setIsPinAccepted,
  updateAccountStaking,
  updateAccountState,
  updateCurrentStaking,
} from '../../reducers';
import { selectAccountStakingState, selectAccountStakingStatesBySlug, selectIsHardwareAccount } from '../../selectors';
import { switchAccount } from './auth';

const MODAL_CLOSING_DELAY = 50;

addActionHandler('startStaking', (global, actions, payload) => {
  const isOpen = global.currentStaking.state !== StakingState.None;
  const { tokenSlug } = payload || {};

  if (tokenSlug) {
    const stakingState = selectAccountStakingStatesBySlug(global, global.currentAccountId!)[tokenSlug];
    if (stakingState) {
      global = getGlobal();
      global = updateAccountStaking(global, global.currentAccountId!, { stakingId: stakingState.id });
      setGlobal(global);

      global = getGlobal();
    }
  }

  if (IS_DELEGATED_BOTTOM_SHEET && !isOpen) {
    callActionInMain('startStaking', payload);
    return;
  }

  const state = StakingState.StakeInitial;

  setGlobal(updateCurrentStaking(global, {
    state,
    error: undefined,
  }));
});

addActionHandler('startUnstaking', (global, actions, payload) => {
  const isOpen = global.currentStaking.state !== StakingState.None;
  const { stakingId } = payload || {};

  if (stakingId) {
    global = getGlobal();
    global = updateAccountStaking(global, global.currentAccountId!, { stakingId });
    setGlobal(global);

    global = getGlobal();
  }

  if (IS_DELEGATED_BOTTOM_SHEET && !isOpen) {
    callActionInMain('startUnstaking', payload);
    return;
  }

  const state = StakingState.UnstakeInitial;

  setGlobal(updateCurrentStaking(global, {
    state,
    error: undefined,
  }));
});

addActionHandler('fetchStakingFee', async (global, actions, payload) => {
  const { amount } = payload;
  const { currentAccountId } = global;

  if (!currentAccountId) {
    return;
  }

  const state = selectAccountStakingState(global, currentAccountId);

  const result = await callApi(
    'checkStakeDraft',
    currentAccountId,
    amount,
    state,
  );
  if (!result || 'error' in result) {
    return;
  }

  global = getGlobal();
  global = updateCurrentStaking(global, {
    fee: result.fee,
  });
  setGlobal(global);
});

addActionHandler('submitStakingInitial', async (global, actions, payload) => {
  const { isUnstaking, amount } = payload ?? {};
  const { currentAccountId } = global;

  if (!currentAccountId) {
    return;
  }

  setGlobal(updateCurrentStaking(global, { isLoading: true, error: undefined }));

  const state = selectAccountStakingState(global, currentAccountId);

  if (isUnstaking) {
    const result = await callApi('checkUnstakeDraft', currentAccountId, amount!, state);
    global = getGlobal();
    global = updateCurrentStaking(global, { isLoading: false });

    if (result) {
      if ('error' in result) {
        global = updateCurrentStaking(global, { error: result.error });
      } else {
        if (selectIsHardwareAccount(global)) {
          global = resetHardware(global);
          global = updateCurrentStaking(global, { state: StakingState.UnstakeConnectHardware });
        } else {
          global = updateCurrentStaking(global, { state: StakingState.UnstakePassword });
        }

        global = updateCurrentStaking(global, {
          fee: result.fee,
          amount,
          error: undefined,
          tokenAmount: result.tokenAmount,
        });
      }
    }
  } else {
    const result = await callApi(
      'checkStakeDraft',
      currentAccountId,
      amount!,
      state,
    );
    global = getGlobal();
    global = updateCurrentStaking(global, { isLoading: false });

    if (result) {
      if ('error' in result) {
        global = updateCurrentStaking(global, { error: result.error });
      } else {
        if (selectIsHardwareAccount(global)) {
          global = resetHardware(global);
          global = updateCurrentStaking(global, { state: StakingState.StakeConnectHardware });
        } else {
          global = updateCurrentStaking(global, { state: StakingState.StakePassword });
        }

        global = updateCurrentStaking(global, {
          fee: result.fee,
          amount,
          error: undefined,
        });
      }
    }
  }

  setGlobal(global);
});

addActionHandler('submitStaking', async (global, actions, payload = {}) => {
  const { password = '', isUnstaking } = payload;
  const { amount, tokenAmount } = global.currentStaking;
  const { currentAccountId } = global;
  const isHardware = selectIsHardwareAccount(global);

  if (!isHardware && !(await callApi('verifyPassword', password))) {
    setGlobal(updateCurrentStaking(getGlobal(), { error: 'Wrong password, please try again.' }));

    return;
  }

  global = getGlobal();

  const state = selectAccountStakingState(global, currentAccountId!);

  if (!isHardware && getDoesUsePinPad()) {
    global = setIsPinAccepted(global);
  }

  global = updateCurrentStaking(global, {
    isLoading: true,
    error: undefined,
    ...(
      isHardware && {
        state: isUnstaking
          ? StakingState.UnstakeConfirmHardware
          : StakingState.StakeConfirmHardware,
      }
    ),
  });
  setGlobal(global);

  if (!isHardware) {
    await vibrateOnSuccess(true);
  }
  global = getGlobal();

  if (isUnstaking) {
    const unstakeAmount = state.type === 'nominators' ? state.balance : tokenAmount!;
    const result = await callApi(
      'submitUnstake',
      global.currentAccountId!,
      password,
      unstakeAmount,
      state,
      getTonStakingFees(state.type).unstake.real,
    );

    const isLongUnstakeRequested = getIsLongUnstake(state, unstakeAmount);

    global = getGlobal();
    global = updateAccountState(global, currentAccountId!, { isLongUnstakeRequested });
    global = updateCurrentStaking(global, { isLoading: false });
    setGlobal(global);

    if (!result) {
      void vibrateOnError();
      actions.showDialog({
        message: 'Unstaking was unsuccessful. Try again later.',
      });
      global = getGlobal();

      if (!isHardware && getDoesUsePinPad()) {
        global = clearIsPinAccepted(global);
        setGlobal(global);
      }
    } else if ('error' in result) {
      global = getGlobal();
      global = updateCurrentStaking(global, {
        isLoading: false,
        error: result.error,
      });
      actions.showError({ error: result.error });
      setGlobal(global);
    } else {
      void vibrateOnSuccess();
      global = getGlobal();
      global = updateCurrentStaking(global, { state: StakingState.UnstakeComplete });
      setGlobal(global);
    }
  } else {
    const result = await callApi(
      'submitStake',
      global.currentAccountId!,
      password,
      amount!,
      state,
      getTonStakingFees(state.type).stake.real,
    );

    global = getGlobal();
    global = updateCurrentStaking(global, { isLoading: false });
    setGlobal(global);

    if (!result) {
      void vibrateOnError();
      actions.showDialog({
        message: 'Staking was unsuccessful. Try again later.',
      });

      global = getGlobal();
      if (!isHardware && getDoesUsePinPad()) {
        global = clearIsPinAccepted(global);
        setGlobal(global);
      }
    } else if ('error' in result) {
      void vibrateOnError();
      global = updateCurrentStaking(getGlobal(), {
        isLoading: false,
        error: result.error,
      });
      setGlobal(global);
      if (!isHardware) actions.showError({ error: result.error });
    } else {
      void vibrateOnSuccess();
      global = getGlobal();
      global = updateCurrentStaking(global, { state: StakingState.StakeComplete });
      setGlobal(global);
    }
  }
});

addActionHandler('clearStakingError', (global) => {
  setGlobal(updateCurrentStaking(global, { error: undefined }));
});

addActionHandler('cancelStaking', (global) => {
  if (getDoesUsePinPad()) {
    global = clearIsPinAccepted(global);
  }

  global = clearCurrentStaking(global);
  setGlobal(global);
});

addActionHandler('setStakingScreen', (global, actions, payload) => {
  const { state } = payload;

  setGlobal(updateCurrentStaking(global, { state }));
});

addActionHandler('fetchStakingHistory', async (global, actions, payload) => {
  const { limit, offset } = payload ?? {};
  const stakingHistory = await callApi('getStakingHistory', global.currentAccountId!, limit, offset);

  if (!stakingHistory) {
    return;
  }

  global = getGlobal();
  global = updateAccountState(global, global.currentAccountId!, { stakingHistory }, true);
  setGlobal(global);
});

addActionHandler('openAnyAccountStakingInfo', async (global, actions, { accountId, network, stakingId }) => {
  if (IS_DELEGATED_BOTTOM_SHEET) {
    callActionInMain('openAnyAccountStakingInfo', { accountId, network, stakingId });
    return;
  }

  await Promise.all([
    closeAllOverlays(),
    switchAccount(global, accountId, network),
  ]);

  actions.changeCurrentStaking({ stakingId });
  actions.openStakingInfo();
});

// Should be called only when you're sure that the staking is active. Otherwise, call `openStakingInfoOrStart`.
addActionHandler('openStakingInfo', (global) => {
  if (IS_DELEGATED_BOTTOM_SHEET) {
    callActionInMain('openStakingInfo');
    return;
  }

  global = { ...global, isStakingInfoModalOpen: true };
  setGlobal(global);
});

addActionHandler('closeStakingInfo', (global) => {
  global = { ...global, isStakingInfoModalOpen: undefined };
  setGlobal(global);
});

addActionHandler('changeCurrentStaking', async (global, actions, { stakingId, shouldReopenModal }) => {
  if (IS_DELEGATED_BOTTOM_SHEET && shouldReopenModal) {
    callActionInMain('changeCurrentStaking', { stakingId, shouldReopenModal });
    return;
  }

  if (shouldReopenModal) {
    await pause(MODAL_CLOSING_DELAY);
  }

  global = getGlobal();
  global = updateAccountStaking(global, global.currentAccountId!, { stakingId });
  setGlobal(global);

  if (shouldReopenModal) {
    actions.openStakingInfoOrStart();
  }
});

addActionHandler('startStakingClaim', (global, actions, payload) => {
  const { stakingId } = payload || {};

  if (stakingId) {
    global = getGlobal();
    global = updateAccountStaking(global, global.currentAccountId!, { stakingId });
    setGlobal(global);

    global = getGlobal();
  }

  if (IS_DELEGATED_BOTTOM_SHEET) {
    callActionInMain('startStakingClaim', payload);
    return;
  }

  if (selectIsHardwareAccount(global)) {
    global = resetHardware(global);
    global = updateCurrentStaking(global, { state: StakingState.ClaimConnectHardware });
  } else {
    global = updateCurrentStaking(global, { state: StakingState.ClaimPassword });
  }
  setGlobal(global);
});

addActionHandler('cancelStakingClaim', (global) => {
  global = updateCurrentStaking(global, { state: StakingState.None });
  setGlobal(global);
});

addActionHandler('submitStakingClaim', async (global, actions, { password = '' } = {}) => {
  const accountId = global.currentAccountId!;
  const isHardware = selectIsHardwareAccount(global);
  if (!isHardware && !(await callApi('verifyPassword', password))) {
    setGlobal(updateCurrentStaking(getGlobal(), { error: 'Wrong password, please try again.' }));
    return;
  }
  global = getGlobal();

  if (!isHardware && getDoesUsePinPad()) {
    global = setIsPinAccepted(global);
  }

  global = updateCurrentStaking(global, {
    isLoading: true,
    error: undefined,
    ...(isHardware && { state: StakingState.ClaimConfirmHardware }),
  });
  setGlobal(global);
  if (!isHardware) {
    await vibrateOnSuccess(true);
  }

  if (IS_DELEGATED_BOTTOM_SHEET) {
    callActionInMain('submitStakingClaim', { password });
    return;
  }

  global = getGlobal();

  const stakingState = selectAccountStakingState(global, accountId) as ApiEthenaStakingState | ApiJettonStakingState;
  const isEthenaStaking = stakingState.type === 'ethena';

  const result = await callApi(
    'submitStakingClaimOrUnlock',
    accountId,
    password,
    stakingState,
    getTonStakingFees(stakingState.type).claim?.real,
  );

  global = getGlobal();
  global = updateCurrentStaking(global, { isLoading: false });
  setGlobal(global);

  if (!result || 'error' in result) {
    if (result?.error) {
      global = updateCurrentStaking(global, { error: result.error });
    }
    if (!isHardware && getDoesUsePinPad()) {
      global = getGlobal();
      global = clearIsPinAccepted(global);
    }
    setGlobal(global);
    void vibrateOnError();
    actions.showError({ error: result?.error });
    return;
  } else {
    void vibrateOnSuccess();
  }

  global = getGlobal();
  global = updateCurrentStaking(global, {
    state: isEthenaStaking ? StakingState.ClaimComplete : StakingState.None,
  });
  setGlobal(global);

  if (IS_DELEGATING_BOTTOM_SHEET) {
    callActionInNative('setStakingScreen', {
      state: isEthenaStaking ? StakingState.ClaimComplete : StakingState.None,
    });
  }
});

// Opens the staking info modal if the modal is available. Otherwise, opens the staking start modal.
addActionHandler('openStakingInfoOrStart', (global, actions) => {
  if (!global.currentAccountId) {
    return;
  }

  const stakingState = selectAccountStakingState(global, global.currentAccountId);

  if (getIsActiveStakingState(stakingState)) {
    actions.openStakingInfo();
  } else {
    actions.startStaking();
  }
});
