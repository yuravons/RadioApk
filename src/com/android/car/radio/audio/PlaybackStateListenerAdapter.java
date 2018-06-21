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

package com.android.car.radio.audio;

import android.annotation.NonNull;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.Objects;

/**
 * A class to wrap method reference as {@link IPlaybackStateListener}.
 */
public class PlaybackStateListenerAdapter extends IPlaybackStateListener.Stub {
    @NonNull
    private final Callback mCallback;

    /**
     * {@link IPlaybackStateListener} without binder methods.
     */
    public interface Callback {
        /**
         * See {@link IPlaybackStateListener#onPlaybackStateChanged}.
         */
        void onPlaybackStateChanged(int state);
    }

    /**
     * Wraps method interface.
     */
    public PlaybackStateListenerAdapter(@NonNull Callback callback) {
        mCallback = Objects.requireNonNull(callback);
    }

    @Override
    public void onPlaybackStateChanged(@PlaybackStateCompat.State int state) {
        mCallback.onPlaybackStateChanged(state);
    }
}
