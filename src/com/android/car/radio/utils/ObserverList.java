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

package com.android.car.radio.utils;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A helper class to handle observers/listeners.
 *
 * Holds a list of observers and calls the specified callback method on them whether observed value
 * changes. When the new observer is added, it gets the update about the most recent value of the
 * observed entity.
 *
 * Usage example:
 * private final ObserverList<MyValue, IMyValueObserver> mMyValueObservers = new ObserverList<>(
 *         new MyValue('dummy'), IMyValueObserver::onMyValueChanged);
 *
 * mMyValueObservers.update(new MyValue('other'))
 *
 * @param <V> Observed value type
 * @param <O> Observer type; should have a method accepting {@link <V>}
 */
public class ObserverList<V, O> {
    private static final String TAG = "BcRadioApp.ObserverManager";

    private final Object mLock = new Object();

    private @Nullable V mCurrentValue;
    private final Callback<V, O> mCallback;

    private final List<O> mObservers = new ArrayList<>();

    /**
     * Callback trigger.
     *
     * It's meant to be a type of observer's type method, i.e. O::onValueChanged(V value).
     *
     * @param <V> Observed value type
     * @param <O> Observer type; should have a method accepting {@link <V>}
     */
    public interface Callback<V, O> {
        /**
         * Callback trigger method.
         *
         * @param observer Observer to notify about the new value.
         * @param value New value.
         */
        void triggerCallback(O observer, V value) throws RemoteException;
    }

    /**
     * Create new observer list.
     *
     * @param initialValue Initial value to notify newly added observers about, or null
     *        to skip initial notifications until {@link update}.
     * @param callback Callback trigger, see {@link Callback}.
     */
    public ObserverList(@Nullable V initialValue, @NonNull Callback<V, O> callback) {
        mCurrentValue = initialValue;
        mCallback = Objects.requireNonNull(callback);
    }

    /**
     * Adds new observer to the list.
     *
     * When added, the observer gets notified about the most recent value of the observed entity.
     *
     * @param observer Observer to be added.
     */
    public void add(@NonNull O observer) {
        synchronized (mLock) {
            if (mObservers.contains(observer)) {
                throw new IllegalArgumentException("Observer is already in the list");
            }
            mObservers.add(Objects.requireNonNull(observer));
            if (mCurrentValue == null) return;
            try {
                mCallback.triggerCallback(observer, mCurrentValue);
            } catch (RemoteException e) {
                Log.e(TAG, "Couldn't notify new observer about the current value", e);
            }
        }
    }

    /**
     * Removes observer from the list.
     *
     * @param observer Observer to be removed.
     */
    public void remove(O observer) {
        synchronized (mLock) {
            mObservers.remove(observer);
        }
    }

    /**
     * Notify observers about the new value.
     *
     * @param newValue New value to notify the observers about.
     */
    public void update(@NonNull V newValue) {
        synchronized (mLock) {
            if (newValue.equals(mCurrentValue)) return;
            mCurrentValue = newValue;

            for (O observer : mObservers) {
                try {
                    mCallback.triggerCallback(observer, newValue);
                } catch (RemoteException e) {
                    Log.e(TAG, "Couldn't notify observer about the new value", e);
                }
            }
        }
    }
}
