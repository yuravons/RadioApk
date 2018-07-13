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

package com.android.car.radio.bands;

import android.hardware.radio.RadioManager.BandDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.platform.RadioTunerExt;
import com.android.car.radio.platform.RadioTunerExt.TuneCallback;
import com.android.car.radio.service.RadioAppServiceWrapper;
import com.android.car.radio.util.Log;

import java.util.List;
import java.util.Random;

abstract class AMFMProgramType extends ProgramType {
    private static final String TAG = "BcRadioApp.ProgramType";

    AMFMProgramType(@TypeId int id) {
        super(id);
    }

    public void tuneToDefault(@NonNull RadioTunerExt tuner,
            @NonNull RadioAppServiceWrapper appService, @Nullable TuneCallback result) {
        List<BandDescriptor> bands = appService.getAmFmRegionConfig(this);
        if (bands.size() == 0) {
            Log.e(TAG, "No " + getEnglishName() + " bands provided by the hardware");
            return;
        }

        /* Select random initial frequency to give some fairness in picking the initial station.
         * Please note it does not give uniform fairness for all radio stations (i.e. this
         * algorithm is biased towards stations that have a lot of unused channels before them),
         * but is a fair compromise between complexity and distribution.
         */
        Random rnd = new Random();
        BandDescriptor band = bands.get(rnd.nextInt(bands.size()));
        int freq = rnd.nextInt(band.getUpperLimit() - band.getLowerLimit()) + band.getLowerLimit();
        freq /= band.getSpacing();
        freq *= band.getSpacing();

        // tune to that frequency and seek forward, to find any station
        tuner.tune(ProgramSelectorExt.createAmFmSelector(freq), succeeded -> {
            if (!succeeded) {
                result.onFinished(false);
                return;
            }
            tuner.seek(true, result);
        });
    }
}
