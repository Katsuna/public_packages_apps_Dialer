/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.incallui.incall.impl;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.telecom.CallAudioState;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.widget.LockableViewPager;
import com.android.incallui.InCallActivity;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.impl.ButtonController.SpeakerButtonController;
import com.android.incallui.incall.impl.InCallButtonGridFragment.OnButtonGridCreatedListener;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.katsuna.CallInfoUtils;
import com.android.incallui.katsuna.DrawDialerUtils;
import com.android.incallui.katsuna.KatsunaSpeakerButtonInfo;
import com.katsuna.commons.entities.ColorProfileKeyV2;
import com.katsuna.commons.entities.UserProfile;
import com.katsuna.commons.utils.ColorCalcV2;
import com.katsuna.commons.utils.ProfileReader;

import java.util.ArrayList;
import java.util.List;

/** Fragment that shows UI for an ongoing voice call. */
public class InCallFragment extends Fragment
    implements InCallScreen,
        InCallButtonUi,
        OnClickListener,
        AudioRouteSelectorPresenter,
        OnButtonGridCreatedListener {

  private List<ButtonController> buttonControllers = new ArrayList<>();
  private InCallPagerAdapter adapter;
  private InCallScreenDelegate inCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private InCallButtonGridFragment inCallButtonGridFragment;
  @Nullable private ButtonChooser buttonChooser;
  private SecondaryInfo savedSecondaryInfo;
  private int voiceNetworkType;
  private int phoneType;
  private boolean stateRestored;

  private View mInCallLayout;
  private Button mBluetoothButton;
  private ToggleButton mMuteButton;
  private ToggleButton mHoldButton;
  private Button mDialpadButton;
  private View mEndCallButton;
  private InCallButtonUi mKatsunaInCallButtonUi;
  private CallInfoUtils mCallInfoUtils;


  private static boolean isSupportedButton(@InCallButtonIds int id) {
    return id == InCallButtonIds.BUTTON_AUDIO
        || id == InCallButtonIds.BUTTON_MUTE
        || id == InCallButtonIds.BUTTON_DIALPAD
        || id == InCallButtonIds.BUTTON_HOLD
        || id == InCallButtonIds.BUTTON_SWAP
        || id == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO
        || id == InCallButtonIds.BUTTON_ADD_CALL
        || id == InCallButtonIds.BUTTON_MERGE
        || id == InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
      stateRestored = true;
    }
  }

  private boolean mDialPadVisible;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onCreateView", null);
    final View view = layoutInflater.inflate(R.layout.frag_incall_voice, viewGroup, false);
    mCallInfoUtils = new CallInfoUtils(view, false);


    if (ContextCompat.checkSelfPermission(getContext(), permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED) {
      voiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    } else {

      voiceNetworkType =
          VERSION.SDK_INT >= VERSION_CODES.N
              ? getContext().getSystemService(TelephonyManager.class).getVoiceNetworkType()
              : TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }
    phoneType = getContext().getSystemService(TelephonyManager.class).getPhoneType();

    mInCallLayout = view.findViewById(R.id.incall_layout);
    mBluetoothButton = view.findViewById(R.id.bluetooth_button);
    mBluetoothButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          inCallButtonUiDelegate.showAudioRouteSelector();
        }
    });
    mMuteButton = view.findViewById(R.id.mute_button);
    mMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
       @Override
       public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
           inCallButtonUiDelegate.muteClicked(isChecked, true);

           //TelecomAdapter.getInstance().mute(isChecked);
       }
    });
    mDialpadButton = view.findViewById(R.id.dialpad_button);
    mDialpadButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        ((InCallActivity) getActivity()).showDialpadFragment(true /* show */,
                true /* animate */);
        //inCallButtonUiDelegate.showDialpadClicked();
        mDialPadVisible = !mDialPadVisible;
      }
    });
    mHoldButton = view.findViewById(R.id.hold_button);
    mHoldButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        inCallButtonUiDelegate.holdClicked(isChecked);
      }
    });
    mEndCallButton = view.findViewById(R.id.end_call_button);
    mEndCallButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        LogUtil.i("InCallFragment.onClick", "end call button clicked");
        inCallScreenDelegate.onEndCallClicked();
      }
    });

    mKatsunaInCallButtonUi = new InCallButtonUi() {
      @Override
      public void showButton(int buttonId, boolean show) {

      }

      @Override
      public void enableButton(int buttonId, boolean enable) {

      }

      @Override
      public void setEnabled(boolean on) {

      }

      @Override
      public void setHold(boolean on) {
        if (mHoldButton != null) {
          if (mHoldButton.isChecked() ^ on) {
            mHoldButton.setChecked(on);
          }
        }
      }

      @Override
      public void setCameraSwitched(boolean isBackFacingCamera) {

      }

      @Override
      public void setVideoPaused(boolean isPaused) {

      }

      @Override
      public void setAudioState(CallAudioState audioState) {
        LogUtil.i("InCallFragment.setAudioState", "audioState: " + audioState);
        if (mMuteButton != null) {
          if (audioState.isMuted()) {
            if (!mMuteButton.isChecked()) {
              mMuteButton.setChecked(true);
            }
          } else {
            if (mMuteButton.isChecked()) {
              mMuteButton.setChecked(false);
            }
          }
        }

        KatsunaSpeakerButtonInfo speakerButtonInfo = new KatsunaSpeakerButtonInfo(audioState);

        int label = speakerButtonInfo.label;
        String labelStr = getString(label);

        mBluetoothButton.setText(labelStr);

        Drawable icon = getContext().getDrawable(speakerButtonInfo.icon);
        mBluetoothButton.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
      }

      @Override
      public void updateButtonStates() {

      }

      @Override
      public void updateInCallButtonUiColors() {

      }

      @Override
      public Fragment getInCallButtonUiFragment() {
        return null;
      }

      @Override
      public void showAudioRouteSelector() {
        AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
                .show(getChildFragmentManager(), null);
      }
    };


    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    inCallButtonUiDelegate.refreshMuteState();
    inCallScreenDelegate.onInCallScreenResumed();

    adjustProfiles();
  }

  private UserProfile mUserProfile;

  private void adjustProfiles() {
    Context ctx = getContext();
    mUserProfile = ProfileReader.getUserProfileFromKatsunaServices(ctx);

    int mColorSecondary1 = ColorCalcV2.getColor(ctx, ColorProfileKeyV2.SECONDARY_COLOR_1,
            mUserProfile.colorProfile);

    mInCallLayout.setBackgroundColor(mColorSecondary1);


    DrawDialerUtils.adjustToggleButton(ctx, mUserProfile, mMuteButton);

    Drawable buttonBg = DrawDialerUtils.createButtonBg(ctx, mUserProfile);
    DrawDialerUtils.adjustButton(mDialpadButton, buttonBg);
    DrawDialerUtils.adjustButton(mBluetoothButton, buttonBg);

    DrawDialerUtils.adjustToggleButton(ctx, mUserProfile, mHoldButton);

    DrawDialerUtils.adjustPrimaryButton(ctx, mUserProfile, mEndCallButton);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onViewCreated", null);
    super.onViewCreated(view, bundle);
    inCallScreenDelegate =
        FragmentUtils.getParent(this, InCallScreenDelegateFactory.class).newInCallScreenDelegate();
    Assert.isNotNull(inCallScreenDelegate);

    buttonControllers.add(new ButtonController.MuteButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SpeakerButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.DialpadButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.HoldButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.AddCallButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SwapButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.MergeButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.UpgradeToVideoButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.ManageConferenceButtonController(inCallScreenDelegate));
    buttonControllers.add(
        new ButtonController.SwitchToSecondaryButtonController(inCallScreenDelegate));

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
    inCallButtonUiDelegate.onInCallButtonUiReady(mKatsunaInCallButtonUi);
  }

  @Override
  public void onPause() {
    super.onPause();
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    inCallScreenDelegate.onInCallScreenUnready();
    inCallButtonUiDelegate.onInCallButtonUiUnready();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    inCallButtonUiDelegate.onSaveInstanceState(outState);
  }

  @Override
  public void onClick(View view) {
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("InCallFragment.setPrimary", primaryInfo.toString());
    setAdapterMedia(primaryInfo.multimediaData);
    mCallInfoUtils.setPrimary(primaryInfo);

  }

  private void setAdapterMedia(MultimediaData multimediaData) {
    if (adapter == null) {
      adapter = new InCallPagerAdapter(getChildFragmentManager(), multimediaData);
    } else {
      adapter.setAttachments(multimediaData);
    }
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("InCallFragment.setSecondary", secondaryInfo.toString());
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setEnabled(secondaryInfo.shouldShow);
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setAllowed(secondaryInfo.shouldShow);
    updateButtonStates();

    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.incall_on_hold_banner);
    if (secondaryInfo.shouldShow) {
      transaction.replace(R.id.incall_on_hold_banner, OnHoldFragment.newInstance(secondaryInfo));
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitAllowingStateLoss();
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("InCallFragment.setCallState", primaryCallState.toString());
    mCallInfoUtils.setCallState(primaryCallState);
    buttonChooser =
        ButtonChooserFactory.newButtonChooser(voiceNetworkType, primaryCallState.isWifi, phoneType);
    updateButtonStates();
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
  }

  @Override
  public void showManageConferenceCallButton(boolean visible) {
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setAllowed(visible);
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setEnabled(visible);
    updateButtonStates();
  }

  @Override
  public boolean isManageConferenceVisible() {
    return getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).isAllowed();
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
  }

  @Override
  public void showNoteSentToast() {
    LogUtil.i("InCallFragment.showNoteSentToast", null);
    Toast.makeText(getContext(), R.string.incall_note_sent, Toast.LENGTH_LONG).show();
  }

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    LogUtil.i("InCallFragment.onInCallScreenDialpadVisibilityChange", "isShowing: " + isShowing);
    // Take note that the dialpad button isShowing
    getButtonController(InCallButtonIds.BUTTON_DIALPAD).setChecked(isShowing);

    // This check is needed because there is a race condition where we attempt to update
    // ButtonGridFragment before it is ready, so we check whether it is ready first and once it is
    // ready, #onButtonGridCreated will mark the dialpad button as isShowing.
    if (inCallButtonGridFragment != null) {
      // Update the Android Button's state to isShowing.
      inCallButtonGridFragment.onInCallScreenDialpadVisibilityChange(isShowing);
    }
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.incall_dialpad_container;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(@InCallButtonIds int buttonId, boolean show) {
    LogUtil.v(
        "InCallFragment.showButton",
        "buttionId: %s, show: %b",
        InCallButtonIdsExtension.toString(buttonId),
        show);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setAllowed(show);
      if (buttonId == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO && show) {
        Logger.get(getContext())
            .logImpression(DialerImpression.Type.UPGRADE_TO_VIDEO_CALL_BUTTON_SHOWN);
      }
    }
  }

  @Override
  public void enableButton(@InCallButtonIds int buttonId, boolean enable) {
    LogUtil.v(
        "InCallFragment.enableButton",
        "buttonId: %s, enable: %b",
        InCallButtonIdsExtension.toString(buttonId),
        enable);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setEnabled(enable);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    LogUtil.v("InCallFragment.setEnabled", "enabled: " + enabled);
    for (ButtonController buttonController : buttonControllers) {
      buttonController.setEnabled(enabled);
    }
  }

  @Override
  public void setHold(boolean value) {
    getButtonController(InCallButtonIds.BUTTON_HOLD).setChecked(value);
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("InCallFragment.setAudioState", "audioState: " + audioState);
    ((SpeakerButtonController) getButtonController(InCallButtonIds.BUTTON_AUDIO))
        .setAudioState(audioState);
    getButtonController(InCallButtonIds.BUTTON_MUTE).setChecked(audioState.isMuted());
  }

  @Override
  public void updateButtonStates() {
  }

  @Override
  public void updateInCallButtonUiColors() {}

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
        .show(getChildFragmentManager(), null);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @NonNull
  @Override
  public ButtonController getButtonController(@InCallButtonIds int id) {
    for (ButtonController buttonController : buttonControllers) {
      if (buttonController.getInCallButtonId() == id) {
        return buttonController;
      }
    }
    Assert.fail();
    return null;
  }

  @Override
  public void onButtonGridCreated(InCallButtonGridFragment inCallButtonGridFragment) {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiReady");
    this.inCallButtonGridFragment = inCallButtonGridFragment;
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
    updateButtonStates();
  }

  @Override
  public void onButtonGridDestroyed() {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiUnready");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    this.inCallButtonGridFragment = null;
  }

  @Override
  public boolean isShowingLocationUi() {
    return false;
  }

  @Override
  public void showLocationUi(@Nullable Fragment locationUi) {
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    super.onMultiWindowModeChanged(isInMultiWindowMode);
    if (isInMultiWindowMode == isShowingLocationUi()) {
      LogUtil.i("InCallFragment.onMultiWindowModeChanged", "hide = " + isInMultiWindowMode);
    }
  }
}
