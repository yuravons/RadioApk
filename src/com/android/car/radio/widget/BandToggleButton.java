/**
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.radio.widget;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.android.car.radio.R;
import com.android.car.radio.utils.ProgramSelectorUtils;

/**
 * A widget to toggle band (AM/FM/DAB etc).
 */
public class BandToggleButton extends ImageButton {
    @Nullable private Callback mCallback;

    int mCurrentBand = RadioManager.BAND_INVALID;

    /**
     * Widget's onClick event translated to band callback.
     */
    public interface Callback {
        /**
         * Called when user uses this button to switch the band.
         *
         * @param band Band to switch to
         */
        void onSwitchTo(int band);
    }

    public BandToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this::onClick);
    }

    public void setCallback(@Nullable Callback callback) {
        mCallback = callback;
    }

    private void onClick(View v) {
        Callback callback = mCallback;
        if (callback == null) return;

        int switchTo;
        switch (mCurrentBand) {
            case RadioManager.BAND_FM:
            case RadioManager.BAND_FM_HD:
                switchTo = RadioManager.BAND_AM;
                break;
            case RadioManager.BAND_AM:
            case RadioManager.BAND_AM_HD:
            default:
                switchTo = RadioManager.BAND_FM;
                break;
        }

        callback.onSwitchTo(switchTo);
    }

    /**
     * A callback meant to be hooked to {@link RadioController#addCurrentProgramListener}.
     */
    public void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        int radioBand = ProgramSelectorUtils.getRadioBand(info.getSelector());
        mCurrentBand = radioBand;

        switch (radioBand) {
            case RadioManager.BAND_FM:
            case RadioManager.BAND_FM_HD:
                setImageResource(R.drawable.ic_radio_fm);
                break;
            case RadioManager.BAND_AM:
            case RadioManager.BAND_AM_HD:
                setImageResource(R.drawable.ic_radio_am);
                break;
            default:
                setImageDrawable(null);
        }
    }
}
