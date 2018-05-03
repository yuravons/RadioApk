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
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.media.BrowseTree;
import com.android.car.broadcastradio.support.platform.ImageResolver;
import com.android.car.broadcastradio.support.platform.ProgramInfoExt;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.audio.IPlaybackStateListener;
import com.android.car.radio.service.IRadioManager;
import com.android.car.radio.utils.ThrowingRunnable;

import java.util.Objects;

/**
 * Implementation of tuner's MediaSession.
 */
public class TunerSession extends MediaSessionCompat implements IPlaybackStateListener {
    private static final String TAG = "BcRadioApp.msess";

    private final Object mLock = new Object();

    private final BrowseTree mBrowseTree;
    @Nullable private final ImageResolver mImageResolver;
    private final IRadioManager mUiSession;
    private final PlaybackStateCompat.Builder mPlaybackStateBuilder =
            new PlaybackStateCompat.Builder();
    @Nullable private ProgramInfo mCurrentProgram;

    public TunerSession(@NonNull Context context, @NonNull BrowseTree browseTree,
            @NonNull IRadioManager uiSession, @Nullable ImageResolver imageResolver) {
        super(context, TAG);

        mBrowseTree = Objects.requireNonNull(browseTree);
        mImageResolver = imageResolver;
        mUiSession = Objects.requireNonNull(uiSession);

        // ACTION_PAUSE is reserved for time-shifted playback
        mPlaybackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SET_RATING
                | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_URI);
        setRatingType(RatingCompat.RATING_HEART);
        onPlaybackStateChanged(PlaybackStateCompat.STATE_NONE);
        setCallback(new TunerSessionCallback());

        setActive(true);
    }

    private void updateMetadata() {
        synchronized (mLock) {
            if (mCurrentProgram == null) return;
            boolean fav = mBrowseTree.isFavorite(mCurrentProgram.getSelector());
            setMetadata(MediaMetadataCompat.fromMediaMetadata(
                    ProgramInfoExt.toMediaMetadata(mCurrentProgram, fav, mImageResolver)));
        }
    }

    public void notifyProgramInfoChanged(@NonNull ProgramInfo info) {
        synchronized (mLock) {
            mCurrentProgram = info;
            updateMetadata();
        }
    }

    @Override
    public void onPlaybackStateChanged(@PlaybackStateCompat.State int state) {
        synchronized (mPlaybackStateBuilder) {
            mPlaybackStateBuilder.setState(state,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            setPlaybackState(mPlaybackStateBuilder.build());
        }
    }

    public void notifyFavoritesChanged() {
        updateMetadata();
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
        public void onStop() {
            exec(() -> mUiSession.mute());
        }

        @Override
        public void onPlay() {
            exec(() -> mUiSession.unMute());
        }

        @Override
        public void onSkipToNext() {
            exec(() -> mUiSession.seekForward());
        }

        @Override
        public void onSkipToPrevious() {
            exec(() -> mUiSession.seekBackward());
        }

        @Override
        public void onSetRating(RatingCompat rating) {
            synchronized (mLock) {
                if (mCurrentProgram == null) return;
                if (rating.hasHeart()) {
                    Program fav = Program.fromProgramInfo(mCurrentProgram);
                    exec(() -> mUiSession.addFavorite(fav));
                } else {
                    ProgramSelector fav = mCurrentProgram.getSelector();
                    exec(() -> mUiSession.removeFavorite(fav));
                }
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            ProgramSelector selector = mBrowseTree.parseMediaId(mediaId);
            if (selector != null) {
                exec(() -> mUiSession.tune(selector));
            } else {
                Log.e(TAG, "Invalid media ID: " + mediaId);
            }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            ProgramSelector selector = ProgramSelectorExt.fromUri(uri);
            if (selector != null) {
                exec(() -> mUiSession.tune(selector));
            } else {
                Log.e(TAG, "Invalid URI: " + uri);
            }
        }
    }

    @Override
    public IBinder asBinder() {
        throw new UnsupportedOperationException("Not a binder");
    }
}
