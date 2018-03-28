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

package com.android.car.radio.media;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.android.car.radio.platform.ProgramInfoExt;
import com.android.car.radio.service.IRadioManager;
import com.android.car.radio.service.RadioStation;
import com.android.car.radio.utils.ThrowingRunnable;

import java.util.Objects;

public class TunerSession extends MediaSessionCompat {
    private static final String TAG = "BcRadioApp.msess";

    private final BrowseTree mBrowseTree;
    private final IRadioManager mUiSession;
    private final PlaybackStateCompat.Builder mPlaybackStateBuilder =
            new PlaybackStateCompat.Builder();

    public TunerSession(@NonNull Context context, @NonNull BrowseTree browseTree,
            @NonNull IRadioManager uiSession) {
        super(context, TAG);

        mBrowseTree = Objects.requireNonNull(browseTree);
        mUiSession = Objects.requireNonNull(uiSession);

        // TODO(b/75970985): implement ACTION_STOP, ACTION_PAUSE, ACTION_PLAY
        mPlaybackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID);

        // TODO(b/75970985): ACTION_SET_RATING, setRatingType, onSetRating
        // TODO(b/75970985): setSessionActivity when Car/Media app supports getSessionActivity
        // TODO(b/75970985): notifyProgramInfoChanged for the currently tuned station

        setCallback(new TunerSessionCallback());

        // TODO(b/75970985): track playback state, don't hardcode it
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);

        setActive(true);
    }

    private void setPlaybackState(int state) {
        synchronized (mPlaybackStateBuilder) {
            mPlaybackStateBuilder.setState(state,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            setPlaybackState(mPlaybackStateBuilder.build());
        }
    }

    public void notifyProgramInfoChanged(@NonNull ProgramInfo info) {
        setMetadata(MediaMetadataCompat.fromMediaMetadata(ProgramInfoExt.toMediaMetadata(
                info, false /* TODO(b/75970985): handle isFavorite */)));
    }

    private void exec(ThrowingRunnable<RemoteException> func) {
        try {
            func.run();
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to execute MediaSession callback", ex);
        }
    }

    private class TunerSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onSkipToNext() {
            exec(() -> mUiSession.seekForward());
        }

        @Override
        public void onSkipToPrevious() {
            exec(() -> mUiSession.seekBackward());
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            RadioStation selector = mBrowseTree.parseMediaId(mediaId);
            if (selector != null) {
                exec(() -> mUiSession.tune(selector));
            } else {
                Log.e(TAG, "Invalid media ID: " + mediaId);
            }
        }
    }
}
