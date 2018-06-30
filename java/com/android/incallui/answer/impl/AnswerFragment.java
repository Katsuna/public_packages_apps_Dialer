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
 * limitations under the License
 */

package com.android.incallui.answer.impl;

import android.Manifest.permission;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ToggleButton;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.MathUtil;
import com.android.dialer.compat.ActivityCompat;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.util.ViewUtil;
import com.android.incallui.answer.impl.CreateCustomSmsDialogFragment.CreateCustomSmsHolder;
import com.android.incallui.answer.impl.SmsBottomSheetFragment.SmsSheetHolder;
import com.android.incallui.answer.impl.affordance.SwipeButtonHelper.Callback;
import com.android.incallui.answer.impl.affordance.SwipeButtonView;
import com.android.incallui.answer.impl.answermethod.AnswerMethod;
import com.android.incallui.answer.impl.answermethod.AnswerMethodFactory;
import com.android.incallui.answer.impl.answermethod.AnswerMethodHolder;
import com.android.incallui.answer.impl.utils.Interpolators;
import com.android.incallui.answer.protocol.AnswerScreen;
import com.android.incallui.answer.protocol.AnswerScreenDelegate;
import com.android.incallui.answer.protocol.AnswerScreenDelegateFactory;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.katsuna.CallInfoUtils;
import com.android.incallui.maps.MapsComponent;
import com.android.incallui.sessiondata.AvatarPresenter;
import com.android.incallui.sessiondata.MultimediaFragment;
import com.android.incallui.util.AccessibilityUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.katsuna.DrawDialerUtils;
import com.katsuna.commons.entities.ColorProfileKeyV2;
import com.katsuna.commons.entities.UserProfile;
import com.katsuna.commons.utils.ColorCalcV2;
import com.katsuna.commons.utils.ProfileReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The new version of the incoming call screen. */
@SuppressLint("ClickableViewAccessibility")
public class AnswerFragment extends Fragment
    implements AnswerScreen,
        InCallScreen,
        SmsSheetHolder,
        CreateCustomSmsHolder,
        AnswerMethodHolder,
        MultimediaFragment.Holder {


  private ToggleButton mSilenceButton;
  private View mAnswerLayout;
  private Button mMessageButton;
  private Button mDeclineButton;
  private Button mAnswerButton;
  private CallInfoUtils mCallInfoUtils;

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_CALL_ID = "call_id";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_IS_VIDEO_CALL = "is_video_call";

  static final String ARG_ALLOW_ANSWER_AND_RELEASE = "allow_answer_and_release";

  static final String ARG_HAS_CALL_ON_HOLD = "has_call_on_hold";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_IS_VIDEO_UPGRADE_REQUEST = "is_video_upgrade_request";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_IS_SELF_MANAGED_CAMERA = "is_self_managed_camera";

  private static final String STATE_HAS_ANIMATED_ENTRY = "hasAnimated";

  private static final int HINT_SECONDARY_SHOW_DURATION_MILLIS = 5000;
  private static final float ANIMATE_LERP_PROGRESS = 0.5f;
  private static final int STATUS_BAR_DISABLE_RECENT = 0x01000000;
  private static final int STATUS_BAR_DISABLE_HOME = 0x00200000;
  private static final int STATUS_BAR_DISABLE_BACK = 0x00400000;

  private static void fadeToward(View view, float newAlpha) {
    view.setAlpha(MathUtil.lerp(view.getAlpha(), newAlpha, ANIMATE_LERP_PROGRESS));
  }

  private static void scaleToward(View view, float newScale) {
    view.setScaleX(MathUtil.lerp(view.getScaleX(), newScale, ANIMATE_LERP_PROGRESS));
    view.setScaleY(MathUtil.lerp(view.getScaleY(), newScale, ANIMATE_LERP_PROGRESS));
  }

  private AnswerScreenDelegate answerScreenDelegate;
  private InCallScreenDelegate inCallScreenDelegate;

  private View importanceBadge;

  // Use these flags to prevent user from clicking accept/reject buttons multiple times.
  // We use separate flags because in some rare cases accepting a call may fail to join the room,
  // and then user is stuck in the incoming call view until it times out. Two flags at least give
  // the user a chance to get out of the CallActivity.
  private boolean buttonAcceptClicked;
  private boolean buttonRejectClicked;
  private boolean hasAnimatedEntry;
  private PrimaryInfo primaryInfo = PrimaryInfo.createEmptyPrimaryInfo();
  private PrimaryCallState primaryCallState;
  private ArrayList<CharSequence> textResponses;
  private SmsBottomSheetFragment textResponsesFragment;
  private CreateCustomSmsDialogFragment createCustomSmsDialogFragment;

  private VideoCallScreen answerVideoCallScreen;
  private Handler handler = new Handler(Looper.getMainLooper());

  private enum SecondaryBehavior {
    REJECT_WITH_SMS(
        R.drawable.quantum_ic_message_white_24,
        R.string.a11y_description_incoming_call_reject_with_sms,
        R.string.a11y_incoming_call_reject_with_sms,
        R.string.call_incoming_swipe_to_decline_with_message) {
      @Override
      public void performAction(AnswerFragment fragment) {
        fragment.showMessageMenu();
      }
    },

    ANSWER_VIDEO_AS_AUDIO(
        R.drawable.quantum_ic_videocam_off_white_24,
        R.string.a11y_description_incoming_call_answer_video_as_audio,
        R.string.a11y_incoming_call_answer_video_as_audio,
        R.string.call_incoming_swipe_to_answer_video_as_audio) {
      @Override
      public void performAction(AnswerFragment fragment) {
        fragment.acceptCallByUser(true /* answerVideoAsAudio */);
      }
    },

    ANSWER_AND_RELEASE(
        R.drawable.ic_end_answer_32,
        R.string.a11y_description_incoming_call_answer_and_release,
        R.string.a11y_incoming_call_answer_and_release,
        R.string.call_incoming_swipe_to_answer_and_release) {
      @Override
      public void performAction(AnswerFragment fragment) {
        fragment.performAnswerAndRelease();
      }
    };

    @DrawableRes public final int icon;
    @StringRes public final int contentDescription;
    @StringRes public final int accessibilityLabel;
    @StringRes public final int hintText;

    SecondaryBehavior(
        @DrawableRes int icon,
        @StringRes int contentDescription,
        @StringRes int accessibilityLabel,
        @StringRes int hintText) {
      this.icon = icon;
      this.contentDescription = contentDescription;
      this.accessibilityLabel = accessibilityLabel;
      this.hintText = hintText;
    }

    public abstract void performAction(AnswerFragment fragment);

    public void applyToView(ImageView view) {
      view.setImageResource(icon);
      view.setContentDescription(view.getContext().getText(contentDescription));
    }
  }

  private void performAnswerAndRelease() {
    answerScreenDelegate.onAnswerAndReleaseCall();
    buttonAcceptClicked = true;
  }

  private final AccessibilityDelegate accessibilityDelegate =
      new AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
          super.onInitializeAccessibilityNodeInfo(host, info);
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
          return super.performAccessibilityAction(host, action, args);
        }
      };

  public static AnswerFragment newInstance(
      String callId,
      boolean isVideoCall,
      boolean isVideoUpgradeRequest,
      boolean isSelfManagedCamera,
      boolean allowAnswerAndRelease,
      boolean hasCallOnHold) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, Assert.isNotNull(callId));
    bundle.putBoolean(ARG_IS_VIDEO_CALL, isVideoCall);
    bundle.putBoolean(ARG_IS_VIDEO_UPGRADE_REQUEST, isVideoUpgradeRequest);
    bundle.putBoolean(ARG_IS_SELF_MANAGED_CAMERA, isSelfManagedCamera);
    bundle.putBoolean(ARG_ALLOW_ANSWER_AND_RELEASE, allowAnswerAndRelease);
    bundle.putBoolean(ARG_HAS_CALL_ON_HOLD, hasCallOnHold);

    AnswerFragment instance = new AnswerFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public boolean isActionTimeout() {
    return (buttonAcceptClicked || buttonRejectClicked) && answerScreenDelegate.isActionTimeout();
  }

  @Override
  @NonNull
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public boolean isVideoUpgradeRequest() {
    return getArguments().getBoolean(ARG_IS_VIDEO_UPGRADE_REQUEST);
  }

  @Override
  public void setTextResponses(List<String> textResponses) {
    if (isVideoCall() || isVideoUpgradeRequest()) {
      LogUtil.i("AnswerFragment.setTextResponses", "no-op for video calls");
    } else if (textResponses == null) {
      LogUtil.i("AnswerFragment.setTextResponses", "no text responses, hiding secondary button");
      this.textResponses = null;
    } else if (ActivityCompat.isInMultiWindowMode(getActivity())) {
      LogUtil.i("AnswerFragment.setTextResponses", "in multiwindow, hiding secondary button");
      this.textResponses = null;
    } else {
      LogUtil.i("AnswerFragment.setTextResponses", "textResponses.size: " + textResponses.size());
      this.textResponses = new ArrayList<>(textResponses);
    }
  }

  @Override
  public boolean allowAnswerAndRelease() {
    return getArguments().getBoolean(ARG_ALLOW_ANSWER_AND_RELEASE);
  }

  private boolean hasCallOnHold() {
    return getArguments().getBoolean(ARG_HAS_CALL_ON_HOLD);
  }

  @Override
  public boolean hasPendingDialogs() {
    boolean hasPendingDialogs =
        textResponsesFragment != null || createCustomSmsDialogFragment != null;
    LogUtil.i("AnswerFragment.hasPendingDialogs", "" + hasPendingDialogs);
    return hasPendingDialogs;
  }

  @Override
  public void dismissPendingDialogs() {
    LogUtil.i("AnswerFragment.dismissPendingDialogs", null);
    if (textResponsesFragment != null) {
      textResponsesFragment.dismiss();
      textResponsesFragment = null;
    }

    if (createCustomSmsDialogFragment != null) {
      createCustomSmsDialogFragment.dismiss();
      createCustomSmsDialogFragment = null;
    }
  }

  @Override
  public boolean isShowingLocationUi() {
    Fragment fragment = getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
    return fragment != null && fragment.isVisible();
  }

  @Override
  public void showLocationUi(@Nullable Fragment locationUi) {
    boolean isShowing = isShowingLocationUi();
    if (!isShowing && locationUi != null) {
      // Show the location fragment.
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_location_holder, locationUi)
          .commitAllowingStateLoss();
    } else if (isShowing && locationUi == null) {
      // Hide the location fragment
      Fragment fragment = getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
      getChildFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
    }
  }

  @Override
  public Fragment getAnswerScreenFragment() {
    return this;
  }

  @Override
  public void setPrimary(PrimaryInfo primaryInfo) {
    LogUtil.i("AnswerFragment.setPrimary", primaryInfo.toString());
    this.primaryInfo = primaryInfo;
    updatePrimaryUI();
  }

  private void updatePrimaryUI() {
    if (getView() == null) {
      return;
    }
    mCallInfoUtils.setPrimary(primaryInfo);
  }

  private void updateDataFragment() {
    if (!isAdded()) {
      return;
    }
    LogUtil.enterBlock("AnswerFragment.updateDataFragment");
    Fragment current = getChildFragmentManager().findFragmentById(R.id.incall_data_container);
    Fragment newFragment = null;

    MultimediaData multimediaData = getSessionData();
    if (multimediaData != null
        && (!TextUtils.isEmpty(multimediaData.getText())
            || (multimediaData.getImageUri() != null)
            || (multimediaData.getLocation() != null && canShowMap()))) {
      // Need message fragment
      String subject = multimediaData.getText();
      Uri imageUri = multimediaData.getImageUri();
      Location location = multimediaData.getLocation();
      if (!(current instanceof MultimediaFragment)
          || !Objects.equals(((MultimediaFragment) current).getSubject(), subject)
          || !Objects.equals(((MultimediaFragment) current).getImageUri(), imageUri)
          || !Objects.equals(((MultimediaFragment) current).getLocation(), location)) {
        LogUtil.i("AnswerFragment.updateDataFragment", "Replacing multimedia fragment");
        // Needs replacement
        newFragment =
            MultimediaFragment.newInstance(
                multimediaData,
                false /* isInteractive */,
                !primaryInfo.isSpam /* showAvatar */,
                primaryInfo.isSpam);
      }
    } else if (shouldShowAvatar()) {
      // Needs Avatar
      if (!(current instanceof AvatarFragment)) {
        LogUtil.i("AnswerFragment.updateDataFragment", "Replacing avatar fragment");
        // Needs replacement
        newFragment = new AvatarFragment();
      }
    } else {
      // Needs empty
      if (current != null) {
        LogUtil.i("AnswerFragment.updateDataFragment", "Removing current fragment");
        getChildFragmentManager().beginTransaction().remove(current).commitNow();
      }
    }

    if (newFragment != null) {
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_data_container, newFragment)
          .commitNow();
    }
  }

  private boolean shouldShowAvatar() {
    return !isVideoCall() && !isVideoUpgradeRequest();
  }

  private boolean canShowMap() {
    return MapsComponent.get(getContext()).getMaps().isAvailable();
  }

  @Override
  public void updateAvatar(AvatarPresenter avatarContainer) {
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {}

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("AnswerFragment.setCallState", primaryCallState.toString());
    this.primaryCallState = primaryCallState;
    mCallInfoUtils.setCallState(primaryCallState);
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {}

  @Override
  public void showManageConferenceCallButton(boolean visible) {}

  @Override
  public boolean isManageConferenceVisible() {
    return false;
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    // Add prompt of how to accept/decline call with swipe gesture.
    if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
      event
          .getText()
          .add(getResources().getString(R.string.a11y_incoming_call_swipe_gesture_prompt));
    }
  }

  @Override
  public void showNoteSentToast() {}

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {}

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Bundle arguments = getArguments();
    Assert.checkState(arguments.containsKey(ARG_CALL_ID));
    Assert.checkState(arguments.containsKey(ARG_IS_VIDEO_CALL));
    Assert.checkState(arguments.containsKey(ARG_IS_VIDEO_UPGRADE_REQUEST));

    buttonAcceptClicked = false;
    buttonRejectClicked = false;

    View view = inflater.inflate(R.layout.katsuna_answer_layout, container, false);
    initKatsunaControls(view);

    answerScreenDelegate =
        FragmentUtils.getParentUnsafe(this, AnswerScreenDelegateFactory.class)
            .newAnswerScreenDelegate(this);

    int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    if (!ActivityCompat.isInMultiWindowMode(getActivity())
        && (getActivity().checkSelfPermission(permission.STATUS_BAR)
            == PackageManager.PERMISSION_GRANTED)) {
      LogUtil.i("AnswerFragment.onCreateView", "STATUS_BAR permission granted, disabling nav bar");
      // These flags will suppress the alert that the activity is in full view mode
      // during an incoming call on a fresh system/factory reset of the app
      flags |= STATUS_BAR_DISABLE_BACK | STATUS_BAR_DISABLE_HOME | STATUS_BAR_DISABLE_RECENT;
    }
    view.setSystemUiVisibility(flags);
    if (isVideoCall() || isVideoUpgradeRequest()) {
      if (VideoUtils.hasCameraPermissionAndShownPrivacyToast(getContext())) {
        if (isSelfManagedCamera()) {
          answerVideoCallScreen = new SelfManagedAnswerVideoCallScreen(getCallId(), this, view);
        } else {
          answerVideoCallScreen = new AnswerVideoCallScreen(getCallId(), this, view);
        }
      }
    }

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    FragmentUtils.checkParent(this, InCallScreenDelegateFactory.class);
  }

  @Override
  public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    createInCallScreenDelegate();
    updateUI();
  }

  @Override
  public void onResume() {
    super.onResume();
    LogUtil.i("AnswerFragment.onResume", null);
    adjustProfile();
    inCallScreenDelegate.onInCallScreenResumed();
  }

  private void initKatsunaControls(View view) {
    mAnswerLayout = view.findViewById(R.id.answer_layout);
    mSilenceButton = view.findViewById(R.id.silence_button);
    mSilenceButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
      AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
      audioManager.setStreamMute(AudioManager.STREAM_RING, isChecked);
    });
    mMessageButton = view.findViewById(R.id.message_button);
    mMessageButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        showMessageMenu();
      }
    });
    mDeclineButton = view.findViewById(R.id.decline_button);
    mDeclineButton.setOnClickListener(v -> rejectCall());
    mAnswerButton = view.findViewById(R.id.answer_button);
    mAnswerButton.setOnClickListener(v -> answerFromMethod());

    mCallInfoUtils = new CallInfoUtils(view, true);

  }

  private void adjustProfile() {
      Context ctx = getContext();
      UserProfile profile = ProfileReader.getUserProfileFromKatsunaServices(ctx);

      int mColorSecondary1 = ColorCalcV2.getColor(ctx, ColorProfileKeyV2.SECONDARY_COLOR_1,
              profile.colorProfile);

      mAnswerLayout.setBackgroundColor(mColorSecondary1);

      DrawDialerUtils.adjustToggleButton(ctx, profile, mSilenceButton);

      Drawable buttonBg = DrawDialerUtils.createButtonBg(ctx, profile);
      DrawDialerUtils.adjustButton(mMessageButton, buttonBg);

      DrawDialerUtils.adjustPrimaryButton(ctx, profile, mDeclineButton);
      DrawDialerUtils.adjustSecondaryButton(ctx, profile, mAnswerButton);
  }

  @Override
  public void onStart() {
    super.onStart();
    LogUtil.i("AnswerFragment.onStart", null);

    updateUI();
    if (answerVideoCallScreen != null) {
      answerVideoCallScreen.onVideoScreenStart();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    LogUtil.i("AnswerFragment.onStop", null);

    if (answerVideoCallScreen != null) {
      answerVideoCallScreen.onVideoScreenStop();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    LogUtil.i("AnswerFragment.onPause", null);
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onDestroyView() {
    LogUtil.i("AnswerFragment.onDestroyView", null);
    if (answerVideoCallScreen != null) {
      answerVideoCallScreen = null;
    }
    super.onDestroyView();
    inCallScreenDelegate.onInCallScreenUnready();
    answerScreenDelegate.onAnswerScreenUnready();
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_HAS_ANIMATED_ENTRY, hasAnimatedEntry);
  }

  private void updateUI() {
    if (getView() == null) {
      return;
    }

    if (primaryInfo != null) {
      updatePrimaryUI();
    }
    if (primaryCallState != null) {
      mCallInfoUtils.setCallState(primaryCallState);
    }

    restoreBackgroundMaskColor();
  }

  @Override
  public boolean isVideoCall() {
    return getArguments().getBoolean(ARG_IS_VIDEO_CALL);
  }

  public boolean isSelfManagedCamera() {
    return getArguments().getBoolean(ARG_IS_SELF_MANAGED_CAMERA);
  }

  @Override
  public void onAnswerProgressUpdate(@FloatRange(from = -1f, to = 1f) float answerProgress) {
    // Don't fade the window background for call waiting or video upgrades. Fading the background
    // shows the system wallpaper which looks bad because on reject we switch to another call.
    if (primaryCallState.state == State.INCOMING && !isVideoCall()) {
      answerScreenDelegate.updateWindowBackgroundColor(answerProgress);
    }
  }

  @Override
  public void answerFromMethod() {
    acceptCallByUser(false /* answerVideoAsAudio */);
  }

  @Override
  public void rejectFromMethod() {
    rejectCall();
  }

  @Override
  public void resetAnswerProgress() {
    restoreBackgroundMaskColor();
  }

  private ObjectAnimator createTranslation(View view) {
    float translationY = view.getTop() * 0.5f;
    ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, translationY, 0);
    animator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
    return animator;
  }

  private void acceptCallByUser(boolean answerVideoAsAudio) {
    LogUtil.i("AnswerFragment.acceptCallByUser", answerVideoAsAudio ? " answerVideoAsAudio" : "");
    if (!buttonAcceptClicked) {
      answerScreenDelegate.onAnswer(answerVideoAsAudio);
      buttonAcceptClicked = true;
    }
  }

  private void rejectCall() {
    LogUtil.i("AnswerFragment.rejectCall", null);
    if (!buttonRejectClicked) {
      Context context = getContext();
      if (context == null) {
        LogUtil.w(
            "AnswerFragment.rejectCall",
            "Null context when rejecting call. Logger call was skipped");
      } else {
        Logger.get(context)
            .logImpression(DialerImpression.Type.REJECT_INCOMING_CALL_FROM_ANSWER_SCREEN);
      }
      buttonRejectClicked = true;
      answerScreenDelegate.onReject();
    }
  }

  private void restoreBackgroundMaskColor() {
    answerScreenDelegate.updateWindowBackgroundColor(0);
  }

  private void showMessageMenu() {
    LogUtil.i("AnswerFragment.showMessageMenu", "Show sms menu.");
    if (getChildFragmentManager().isDestroyed()) {
      return;
    }

    textResponsesFragment = SmsBottomSheetFragment.newInstance(textResponses);
    textResponsesFragment.show(getChildFragmentManager(), null);
  }

  @Override
  public void smsSelected(@Nullable CharSequence text) {
    LogUtil.i("AnswerFragment.smsSelected", null);
    textResponsesFragment = null;

    if (text == null) {
      createCustomSmsDialogFragment = CreateCustomSmsDialogFragment.newInstance();
      createCustomSmsDialogFragment.show(getChildFragmentManager(), null);
      return;
    }

    if (primaryCallState != null && canRejectCallWithSms()) {
      rejectCall();
      answerScreenDelegate.onRejectCallWithMessage(text.toString());
    }
  }

  @Override
  public void smsDismissed() {
    LogUtil.i("AnswerFragment.smsDismissed", null);
    textResponsesFragment = null;
    answerScreenDelegate.onDismissDialog();
  }

  @Override
  public void customSmsCreated(@NonNull CharSequence text) {
    LogUtil.i("AnswerFragment.customSmsCreated", null);
    createCustomSmsDialogFragment = null;
    if (primaryCallState != null && canRejectCallWithSms()) {
      rejectCall();
      answerScreenDelegate.onRejectCallWithMessage(text.toString());
    }
  }

  @Override
  public void customSmsDismissed() {
    LogUtil.i("AnswerFragment.customSmsDismissed", null);
    createCustomSmsDialogFragment = null;
    answerScreenDelegate.onDismissDialog();
  }

  private boolean canRejectCallWithSms() {
    return primaryCallState != null
        && !(primaryCallState.state == State.DISCONNECTED
            || primaryCallState.state == State.DISCONNECTING
            || primaryCallState.state == State.IDLE);
  }

  private void createInCallScreenDelegate() {
    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    Assert.isNotNull(inCallScreenDelegate);
    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
  }

  private void updateImportanceBadgeVisibility() {
    if (!isAdded() || getView() == null) {
      return;
    }

    if (!getResources().getBoolean(R.bool.answer_important_call_allowed) || primaryInfo.isSpam) {
      importanceBadge.setVisibility(View.GONE);
      return;
    }

    MultimediaData multimediaData = getSessionData();
    boolean showImportant = multimediaData != null && multimediaData.isImportant();
    TransitionManager.beginDelayedTransition((ViewGroup) importanceBadge.getParent());
    // TODO (keyboardr): Change this back to being View.INVISIBLE once mocks are available to
    // properly handle smaller screens
    importanceBadge.setVisibility(showImportant ? View.VISIBLE : View.GONE);
  }

  @Nullable
  private MultimediaData getSessionData() {
    if (primaryInfo == null) {
      return null;
    }
    if (isVideoUpgradeRequest()) {
      return null;
    }
    return primaryInfo.multimediaData;
  }

  /** Shows the Avatar image if available. */
  public static class AvatarFragment extends Fragment implements AvatarPresenter {

    private ImageView avatarImageView;

    @Nullable
    @Override
    public View onCreateView(
        LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
      return layoutInflater.inflate(R.layout.fragment_avatar, viewGroup, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle bundle) {
      super.onViewCreated(view, bundle);
      avatarImageView = ((ImageView) view.findViewById(R.id.contactgrid_avatar));
      FragmentUtils.getParentUnsafe(this, MultimediaFragment.Holder.class).updateAvatar(this);
    }

    @NonNull
    @Override
    public ImageView getAvatarImageView() {
      return avatarImageView;
    }

    @Override
    public int getAvatarSize() {
      return getResources().getDimensionPixelSize(R.dimen.answer_avatar_size);
    }

    @Override
    public boolean shouldShowAnonymousAvatar() {
      return false;
    }
  }
}
