package com.android.incallui.katsuna;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;

import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.dialer.common.LogUtil;
import com.android.incallui.R;
import com.android.incallui.contactgrid.BottomRow;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;

/* inspired from com.android.incallui.contactgrid.ContactGridManager */
public class CallInfoUtils {

    private final Context context;
    private final TextView mContactName;
    private final TextView mStatusLabel;
    private final Chronometer mTimer;
    private boolean isTimerStarted;
    private PrimaryInfo primaryInfo = PrimaryInfo.createEmptyPrimaryInfo();
    private PrimaryCallState primaryCallState = PrimaryCallState.createEmptyPrimaryCallState();

    public CallInfoUtils(View view, boolean showStatus) {
        context = view.getContext();

        mContactName = view.findViewById(R.id.katsuna_contact_name);
        mStatusLabel = view.findViewById(R.id.katsuna_status_label);
        mTimer = view.findViewById(R.id.katsuna_timer);
        if (showStatus) {
            mStatusLabel.setVisibility(View.VISIBLE);
        }
    }

    public void setPrimary(PrimaryInfo primaryInfo) {
        this.primaryInfo = primaryInfo;
        updatePrimaryNameAndPhoto();
        updateBottomRow();
    }

    public void setCallState(PrimaryCallState primaryCallState) {
        this.primaryCallState = primaryCallState;
        updatePrimaryNameAndPhoto();
        updateBottomRow();
    }

    private void updatePrimaryNameAndPhoto() {
        if (TextUtils.isEmpty(primaryInfo.name)) {
            mContactName.setText(null);
        } else {
            mContactName.setText(
                    primaryInfo.nameIsNumber
                            ? PhoneNumberUtilsCompat.createTtsSpannable(primaryInfo.name)
                            : primaryInfo.name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (primaryInfo.nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mContactName.setTextDirection(nameDirection);
        }
    }

    private void updateBottomRow() {
        BottomRow.Info info = BottomRow.getInfo(context, primaryCallState, primaryInfo);

        if (info.isTimerVisible) {
            mTimer.setBase(
                    primaryCallState.connectTimeMillis
                            - System.currentTimeMillis()
                            + SystemClock.elapsedRealtime());
            if (!isTimerStarted) {
                LogUtil.i(
                        "CallInfoUtils.updateBottomRow",
                        "starting timer with base: %d",
                        mTimer.getBase());
                mTimer.start();
                isTimerStarted = true;
            }
            mTimer.setVisibility(View.VISIBLE);
        } else {
            mTimer.stop();
            isTimerStarted = false;

            mTimer.setVisibility(View.INVISIBLE);
        }
    }
}
