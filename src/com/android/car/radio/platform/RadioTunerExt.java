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

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioPatchHandle;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.media.AudioAttributes;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.util.Log;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Proposed extensions to android.hardware.radio.RadioTuner.
 *
 * They might eventually get pushed to the framework.
 */
public class RadioTunerExt {
    private static final String TAG = "BcRadioApp.tunerext";

    // for now, we only support a single tuner with hardcoded address
    private static final String HARDCODED_TUNER_ADDRESS = "tuner0";

    private final Object mLock = new Object();
    private final Object mTuneLock = new Object();
    private final RadioTuner mTuner;
    private final Car mCar;
    @Nullable private CarAudioManager mCarAudioManager;

    @Nullable private CarAudioPatchHandle mAudioPatch;
    @Nullable private Boolean mPendingMuteOperation;

    @Nullable ProgramSelector mOperationSelector;  // null for seek operations
    @Nullable TuneCallback mOperationResultCb;

    /**
     * A callback handling tune/seek operation result.
     */
    public interface TuneCallback {
        /**
         * Called when tune operation finished.
         *
         * @param succeeded States whether the operation succeeded or not.
         */
        void onFinished(boolean succeeded);

        /**
         * Chains other result callbacks.
         */
        default TuneCallback alsoCall(@NonNull TuneCallback other) {
            return succeeded -> {
                onFinished(succeeded);
                other.onFinished(succeeded);
            };
        }
    }

    RadioTunerExt(@NonNull Context context, @NonNull RadioTuner tuner,
            @NonNull TunerCallbackAdapterExt cbExt) {
        mTuner = Objects.requireNonNull(tuner);
        cbExt.setTuneFailedCallback(this::onTuneFailed);
        cbExt.setProgramInfoCallback(this::onProgramInfoChanged);
        mCar = Car.createCar(context, mCarServiceConnection);
        mCar.connect();
    }

    private final ServiceConnection mCarServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mLock) {
                try {
                    mCarAudioManager = (CarAudioManager)mCar.getCarManager(Car.AUDIO_SERVICE);
                    if (mPendingMuteOperation != null) {
                        boolean mute = mPendingMuteOperation;
                        mPendingMuteOperation = null;
                        Log.d(TAG, "Car connected, executing postponed operation: "
                                + (mute ? "mute" : "unmute"));
                        setMuted(mute);
                    }
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Car is not connected", e);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (mLock) {
                mCarAudioManager = null;
                mAudioPatch = null;
            }
        }
    };

    private boolean isSourceAvailableLocked(@NonNull String address)
            throws CarNotConnectedException {
        String[] sources = mCarAudioManager.getExternalSources();
        return Stream.of(sources).anyMatch(source -> address.equals(source));
    }

    public boolean setMuted(boolean muted) {
        synchronized (mLock) {
            if (mCarAudioManager == null) {
                Log.d(TAG, "Car not connected yet, postponing operation: "
                        + (muted ? "mute" : "unmute"));
                mPendingMuteOperation = muted;
                return true;
            }

            // if it's already (not) muted - no need to (un)mute again
            if ((mAudioPatch == null) == muted) return true;

            try {
                if (!muted) {
                    if (!isSourceAvailableLocked(HARDCODED_TUNER_ADDRESS)) {
                        Log.e(TAG, "Tuner source \"" + HARDCODED_TUNER_ADDRESS
                                + "\" is not available");
                        return false;
                    }
                    Log.v(TAG, "Creating audio patch for " + HARDCODED_TUNER_ADDRESS);
                    mAudioPatch = mCarAudioManager.createAudioPatch(HARDCODED_TUNER_ADDRESS,
                            AudioAttributes.USAGE_MEDIA, 0);
                } else {
                    Log.v(TAG, "Releasing audio patch");
                    mCarAudioManager.releaseAudioPatch(mAudioPatch);
                    mAudioPatch = null;
                }
                return true;
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Can't (un)mute - car is not connected", e);
                return false;
            }
        }
    }

    /**
     * See {@link RadioTuner#scan}.
     */
    public void seek(boolean forward, @Nullable TuneCallback resultCb) {
        synchronized (mTuneLock) {
            synchronized (mLock) {
                markOperationFinishedLocked(false);
                mOperationResultCb = resultCb;
            }

            mTuner.cancel();
            int res = mTuner.scan(
                    forward ? RadioTuner.DIRECTION_UP : RadioTuner.DIRECTION_DOWN, false);
            if (res != RadioManager.STATUS_OK) {
                throw new RuntimeException("Seek failed with result of " + res);
            }
        }
    }

    /**
     * See {@link RadioTuner#tune}.
     */
    public void tune(@NonNull ProgramSelector selector, @Nullable TuneCallback resultCb) {
        synchronized (mTuneLock) {
            synchronized (mLock) {
                markOperationFinishedLocked(false);
                mOperationSelector = selector;
                mOperationResultCb = resultCb;
            }

            mTuner.cancel();
            mTuner.tune(selector);
        }
    }

    private void markOperationFinishedLocked(boolean succeeded) {
        if (mOperationResultCb == null) return;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Tune operation for " + mOperationSelector
                    + (succeeded ? " succeeded" : " failed"));
        }

        TuneCallback cb = mOperationResultCb;
        mOperationSelector = null;
        mOperationResultCb = null;

        cb.onFinished(succeeded);

        if (mOperationSelector != null) {
            throw new IllegalStateException("Can't tune in callback's failed branch. It might "
                    + "interfere with tune operation that requested current one cancellation");
        }
    }

    private boolean isMatching(@NonNull ProgramSelector currentOperation,
            @NonNull ProgramSelector event) {
        ProgramSelector.Identifier pri = currentOperation.getPrimaryId();
        return Stream.of(event.getAllIds(pri.getType())).anyMatch(id -> pri.equals(id));
    }

    private void onProgramInfoChanged(RadioManager.ProgramInfo info) {
        synchronized (mLock) {
            if (mOperationResultCb == null) return;
            // if we're seeking, all program info chanes does match
            if (mOperationSelector != null) {
                if (!isMatching(mOperationSelector, info.getSelector())) return;
            }
            markOperationFinishedLocked(true);
        }
    }

    private void onTuneFailed(int result, @Nullable ProgramSelector selector) {
        synchronized (mLock) {
            if (mOperationResultCb == null) return;
            // if we're seeking and got a failed tune (or vice versa), that's a mismatch
            if ((mOperationSelector == null) != (selector == null)) return;
            if (mOperationSelector != null) {
                if (!isMatching(mOperationSelector, selector)) return;
            }
            markOperationFinishedLocked(false);
        }
    }

    /**
     * See {@link RadioTuner#cancel}.
     */
    public void cancel() {
        synchronized (mTuneLock) {
            synchronized (mLock) {
                markOperationFinishedLocked(false);
            }

            int res = mTuner.cancel();
            if (res != RadioManager.STATUS_OK) {
                Log.e(TAG, "Cancel failed with result of " + res);
            }
        }
    }

    /**
     * See {@link RadioTuner#getDynamicProgramList}.
     */
    public @Nullable ProgramList getDynamicProgramList(@Nullable ProgramList.Filter filter) {
        return mTuner.getDynamicProgramList(filter);
    }

    public void close() {
        synchronized (mLock) {
            markOperationFinishedLocked(false);
        }

        mTuner.close();
        mCar.disconnect();
    }
}
