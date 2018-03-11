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
import android.content.Context;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RadioManagerExt {
    private static final String TAG = "BcRadioApp.mgrext";

    private final @NonNull RadioManager mRadioManager;

    public RadioManagerExt(@NonNull Context ctx) {
        mRadioManager = (RadioManager)ctx.getSystemService(Context.RADIO_SERVICE);
        Objects.requireNonNull(mRadioManager, "RadioManager could not be loaded");
    }

    public @Nullable RadioTuner openSession(RadioTuner.Callback callback, Handler handler) {
        Log.i(TAG, "Opening broadcast radio session...");

        List<RadioManager.ModuleProperties> modules = new ArrayList<>();
        int status = mRadioManager.listModules(modules);
        if (status != RadioManager.STATUS_OK) {
            Log.w(TAG, "Couldn't get radio module list: " + status);
            return null;
        }

        if (modules.size() == 0) {
            Log.i(TAG, "No radio modules on this device");
            return null;
        }

        // For now, we open first radio module only.
        return mRadioManager.openTuner(modules.get(0).getId(),
                null,  // BandConfig - let the service automatically select one.
                true,  // withAudio
                callback, handler);
    }
}
