package com.android.incallui.katsuna;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ToggleButton;

import com.android.incallui.R;
import com.katsuna.commons.entities.ColorProfile;
import com.katsuna.commons.entities.ColorProfileKeyV2;
import com.katsuna.commons.entities.UserProfile;
import com.katsuna.commons.utils.ColorCalcV2;
import com.katsuna.commons.utils.DrawUtils;

public class DrawDialerUtils {


    private static Drawable createToggleBg(Context context, UserProfile profile) {
        StateListDrawable out = new StateListDrawable();

        int bgColor = ColorCalcV2.getColor(context, ColorProfileKeyV2.PRIMARY_COLOR_1,
                profile.colorProfile);

        Drawable onDrawable = getRoundedBackground(context, bgColor);

        bgColor = ContextCompat.getColor(context, R.color.common_grey50);
        Drawable offDrawable = getRoundedBackground(context, bgColor);

        out.addState(new int[]{android.R.attr.state_checked}, onDrawable);
        out.addState(new int[]{-android.R.attr.state_checked}, offDrawable);
        return out;
    }

    public static Drawable createButtonBg(Context context, UserProfile profile) {
        StateListDrawable out = new StateListDrawable();

        int bgColor;
        if (profile.colorProfile == ColorProfile.CONTRAST) {
            bgColor = ContextCompat.getColor(context, R.color.common_grey50);
        } else {
            bgColor = ColorCalcV2.getColor(context, ColorProfileKeyV2.PRIMARY_COLOR_1,
                    profile.colorProfile);
        }

        Drawable pressedDrawable = getRoundedBackground(context, bgColor);

        bgColor = ContextCompat.getColor(context, R.color.common_grey50);
        Drawable offDrawable = getRoundedBackground(context, bgColor);

        out.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        out.addState(new int[]{-android.R.attr.state_pressed}, offDrawable);
        return out;
    }

    public static void adjustPrimaryButton(Context context, UserProfile profile, View view) {
        int bgColor;
        if (profile.colorProfile == ColorProfile.CONTRAST) {
            bgColor = ContextCompat.getColor(context, R.color.common_grey50);
        } else {
            bgColor = ColorCalcV2.getColor(context, ColorProfileKeyV2.PRIMARY_COLOR_1,
                    profile.colorProfile);
        }

        Drawable bg = getRoundedBackground(context, bgColor);
        view.setBackground(bg);
    }

    public static void adjustSecondaryButton(Context context, UserProfile profile, Button button) {
        int bgColor;
        if (profile.colorProfile == ColorProfile.CONTRAST) {
            bgColor = ContextCompat.getColor(context, R.color.common_black);
        } else {
            bgColor = ColorCalcV2.getColor(context, ColorProfileKeyV2.PRIMARY_COLOR_2,
                    profile.colorProfile);
        }

        Drawable bg = getRoundedBackground(context, bgColor);
        button.setBackground(bg);
    }


    private static Drawable getRoundedBackground(Context context, int bgColor) {
        // create bg drawable
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        int radius = context.getResources().getDimensionPixelSize(R.dimen.common_8dp);
        bg.setCornerRadius(radius);

        // set bg
        bg.setColor(bgColor);

        return bg;
    }

    public static void adjustToggleButton(Context context, UserProfile profile,
                                          ToggleButton toggleButton) {
        Drawable background = createToggleBg(context, profile);
        toggleButton.setBackground(background);

        ColorStateList colorStateList = createTextColorSelector(context, profile);
        toggleButton.setTextColor(colorStateList);

        Drawable[] drawables = toggleButton.getCompoundDrawablesRelative();
        for(Drawable drawable: drawables) {
            // we expect only drawable start so:
            if (drawable != null) {
                Drawable dr = createDrawableSelector(context, profile, drawable);
                toggleButton.setCompoundDrawablesRelativeWithIntrinsicBounds(dr, null, null, null);
                break;
            }
        }
    }

    public static void adjustButton(Button toggleButton, Drawable background) {
        toggleButton.setBackground(clone(background));
    }

    private static Drawable clone(Drawable drawable) {
        Drawable.ConstantState constantState = drawable.getConstantState();
        return  constantState == null ? null : constantState.newDrawable();
    }

    private static ColorStateList createTextColorSelector(Context context, UserProfile profile) {
        int checkedColor;
        int color;

        if (profile.colorProfile == ColorProfile.CONTRAST) {
            checkedColor = ContextCompat.getColor(context, R.color.common_grey50);
            color = ContextCompat.getColor(context, R.color.common_black87);
        } else {
            checkedColor = ContextCompat.getColor(context, R.color.common_black87);
            color = ContextCompat.getColor(context, R.color.common_black87);
        }


        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked},
                new int[] { -android.R.attr.state_checked}
        };

        int[] colors = new int[] { checkedColor, color };
        return new ColorStateList(states, colors);
    }

    private static Drawable createDrawableSelector(Context context, UserProfile profile,
                                                   Drawable drawable) {


        int color = ContextCompat.getColor(context, R.color.common_black54);
        int checkedColor;
        if (profile.colorProfile == ColorProfile.CONTRAST) {
            checkedColor = ContextCompat.getColor(context, R.color.common_grey50);
        } else {
            checkedColor = ContextCompat.getColor(context, R.color.common_black54);
        }

        Drawable uncheckedDrawable = clone(drawable);
        DrawUtils.setColor(uncheckedDrawable, color);

        Drawable checkedDrawable = clone(drawable);
        DrawUtils.setColor(checkedDrawable, checkedColor);

        StateListDrawable out = new StateListDrawable();
        out.addState(new int[]{android.R.attr.state_checked}, checkedDrawable);
        out.addState(new int[]{-android.R.attr.state_checked}, uncheckedDrawable);
        return out;
    }
}
