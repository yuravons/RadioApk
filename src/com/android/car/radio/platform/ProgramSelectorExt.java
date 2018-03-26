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

package com.android.car.radio.platform;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.util.Log;

import java.text.DecimalFormat;

/**
 * Proposed extensions to android.hardware.radio.ProgramSelector.
 *
 * They might eventually get pushed to the framework.
 */
public class ProgramSelectorExt {
    private static final String TAG = "BcRadioApp.pselext";

    private static final DecimalFormat FORMAT_FM = new DecimalFormat("###.#");

    public static boolean isAmFrequency(long frequencyKhz) {
        return frequencyKhz > 150 && frequencyKhz < 30000;
    }

    public static boolean isFmFrequency(long frequencyKhz) {
        return frequencyKhz > 60000 && frequencyKhz < 110000;
    }

    public static @Nullable String formatAmFmFrequency(long frequencyKhz, boolean withBandName) {
        if (isAmFrequency(frequencyKhz)) {
            return Long.toString(frequencyKhz) + (withBandName ? " AM" : "");
        }
        if (isFmFrequency(frequencyKhz)) {
            return FORMAT_FM.format(frequencyKhz / 1000f) + (withBandName ? " FM" : "");
        }

        Log.w(TAG, "AM/FM frequency out of range: " + frequencyKhz);
        return null;
    }

    public static @NonNull ProgramSelector createAmFmSelector(int frequencyKhz) {
        return ProgramSelector.createAmFmSelector(RadioManager.BAND_INVALID, frequencyKhz);
    }

    /**
     * Returns a channel name that can be displayed to the user.
     *
     * It's implemented only for radio technologies where the channel is meant
     * to be presented to the user.
     *
     * @param sel the program selector
     * @return Channel name or null, if radio technology doesn't present channel names to the user.
     */
    public static @Nullable String getDisplayName(@NonNull ProgramSelector sel) {
        ProgramSelector.Identifier pri = sel.getPrimaryId();

        if (pri.getType() == ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY) {
            return formatAmFmFrequency(pri.getValue(), true);
        }

        return null;
    }
}
