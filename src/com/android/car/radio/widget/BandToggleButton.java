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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.R;
import com.android.car.radio.bands.ProgramType;

/**
 * A widget to toggle band (AM/FM/DAB etc).
 */
public class BandToggleButton extends ImageButton {
    @Nullable private Callback mCallback;

    @Nullable ProgramType mCurrentBand;

    /**
     * Widget's onClick event translated to band callback.
     */
    public interface Callback {
        /**
         * Called when user uses this button to switch the band.
         *
         * @param band Band to switch to
         */
        void onSwitchTo(@NonNull ProgramType pt);
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

        ProgramType switchTo;
        switch (mCurrentBand.id) {
            case ProgramType.ID_FM:
                switchTo = ProgramType.AM;
                break;
            case ProgramType.ID_AM:
                switchTo = ProgramType.FM;
                break;
            default:
                switchTo = ProgramType.FM;
                break;
        }

        callback.onSwitchTo(switchTo);
    }

    /**
     * Sets band button state.
     *
     * This method doesn't trigger callback.
     *
     * @param ptype Program type to set.
     */
    public void setType(@NonNull ProgramType ptype) {
        mCurrentBand = ptype;

        switch (ptype.id) {
            case ProgramType.ID_FM:
                setImageResource(R.drawable.ic_radio_fm);
                break;
            case ProgramType.ID_AM:
                setImageResource(R.drawable.ic_radio_am);
                break;
            default:
                setImageDrawable(null);
        }
    }
}
