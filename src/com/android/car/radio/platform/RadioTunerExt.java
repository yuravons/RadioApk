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

import android.content.Context;
import android.util.Log;

/**
 * Proposed extensions to android.hardware.radio.RadioTuner.
 *
 * They might eventually get pushed to the framework.
 */
public class RadioTunerExt {
    private static final String TAG = "BcRadioApp.tunerext";

    private final Object mLock = new Object();

    RadioTunerExt(Context context) {
        // TODO(b/77863406): initialize AudioManager here
    }

    public void setMuted(boolean muted) {
        synchronized (mLock) {
            if (muted) {
                // TODO(b/77863406): audio patch should be removed here or gain set to 0
                Log.e(TAG, "mute not implemented yet");
            } else {
                // TODO(b/77863406): create audio patch here
                Log.e(TAG, "createAudioPatch should happen here, it's just not implemented yet");
            }
        }
    }
}
