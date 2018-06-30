package com.android.incallui.katsuna;

import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.telecom.CallAudioState;

import com.android.incallui.R;

public class KatsunaSpeakerButtonInfo {

    @DrawableRes
    public final int icon;

    @StringRes
    public final int label;

    public KatsunaSpeakerButtonInfo(CallAudioState audioState) {
        if ((audioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH)
                == CallAudioState.ROUTE_BLUETOOTH) {

            if ((audioState.getRoute() & CallAudioState.ROUTE_BLUETOOTH)
                    == CallAudioState.ROUTE_BLUETOOTH) {
                icon = R.drawable.katsuna_bluetooth_black54_28dp;
                label = R.string.incall_content_description_bluetooth;
            } else if ((audioState.getRoute() & CallAudioState.ROUTE_SPEAKER)
                    == CallAudioState.ROUTE_SPEAKER) {
                icon = R.drawable.katsuna_volume_up_black54_28dp;
                label = R.string.incall_content_description_speaker;
            } else if ((audioState.getRoute() & CallAudioState.ROUTE_WIRED_HEADSET)
                    == CallAudioState.ROUTE_WIRED_HEADSET) {
                icon = R.drawable.katsuna_headset_black54_28dp;
                label = R.string.incall_content_description_headset;
            } else {
                icon = R.drawable.katsuna_phone_in_talk_black54_28dp;
                label = R.string.incall_content_description_earpiece;
            }
        } else {
            if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
                label = R.string.incall_label_speaker;
                icon = R.drawable.katsuna_volume_up_black54_28dp;
            } else if (audioState.getRoute() == CallAudioState.ROUTE_WIRED_HEADSET) {
                label = R.string.incall_label_audio;
                icon = R.drawable.katsuna_headset_black54_28dp;
            } else {
                label = R.string.incall_label_audio;
                icon = R.drawable.katsuna_phone_in_talk_black54_28dp;
            }
        }
    }
}
