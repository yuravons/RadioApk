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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.platform.RadioTunerExt;
import com.android.car.radio.platform.RadioTunerExt.TuneCallback;
import com.android.car.radio.service.RadioAppServiceWrapper;
import com.android.car.radio.util.Log;

class DABProgramType extends ProgramType {
    private static final String TAG = "BcRadioApp.ProgramType";

    DABProgramType(@TypeId int id) {
        super(id);
    }

    @NonNull
    public String getEnglishName() {
        return "DAB";
    }

    public void tuneToDefault(@NonNull RadioTunerExt tuner,
            @NonNull RadioAppServiceWrapper appService, @Nullable TuneCallback result) {
        Log.e(TAG, "Tunning to a default DAB channel is not supported yet");
    }
}
