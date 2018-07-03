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

import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.media.BrowseTree;
import com.android.car.broadcastradio.support.platform.ImageResolver;
import com.android.car.broadcastradio.support.platform.ProgramInfoExt;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.R;
import com.android.car.radio.service.RadioAppServiceWrapper;
import com.android.car.radio.storage.RadioStorage;

import java.util.Objects;

/**
 * Implementation of tuner's MediaSession.
 */
public class TunerSession extends MediaSessionCompat {
    private static final String TAG = "BcRadioApp.msess";

    private final Object mLock = new Object();

    private final Context mContext;
    private final BrowseTree mBrowseTree;
    @Nullable private final ImageResolver mImageResolver;
    private final RadioAppServiceWrapper mAppService;

    private final RadioStorage mRadioStorage;

    private final PlaybackStateCompat.Builder mPlaybackStateBuilder =
            new PlaybackStateCompat.Builder();
    @Nullable private ProgramInfo mCurrentProgram;

    public TunerSession(@NonNull Context context, @NonNull BrowseTree browseTree,
            @NonNull RadioAppServiceWrapper appService, @Nullable ImageResolver imageResolver) {
        super(context, TAG);

        mContext = Objects.requireNonNull(context);
        mBrowseTree = Objects.requireNonNull(browseTree);
        mImageResolver = imageResolver;
        mAppService = Objects.requireNonNull(appService);

        mRadioStorage = RadioStorage.getInstance(context);

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

        // TunerSession is a part of RadioAppService, so observeForever is fine here.
        appService.getPlaybackState().observeForever(this::onPlaybackStateChanged);
        appService.getCurrentProgram().observeForever(this::updateMetadata);
        mRadioStorage.getFavorites().observeForever(
                favorites -> updateMetadata(mAppService.getCurrentProgram().getValue()));

        setActive(true);
    }

    private void updateMetadata(@Nullable ProgramInfo info) {
        synchronized (mLock) {
            if (info == null) return;
            boolean fav = mRadioStorage.isFavorite(info.getSelector());
            setMetadata(MediaMetadataCompat.fromMediaMetadata(
                    ProgramInfoExt.toMediaMetadata(info, fav, mImageResolver)));
        }
    }

    private void onPlaybackStateChanged(@PlaybackStateCompat.State int state) {
        synchronized (mPlaybackStateBuilder) {
            mPlaybackStateBuilder.setState(state,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            setPlaybackState(mPlaybackStateBuilder.build());
        }
    }

    private void selectionError() {
        mAppService.setMuted(true);
        mPlaybackStateBuilder.setErrorMessage(mContext.getString(R.string.invalid_selection));
        onPlaybackStateChanged(PlaybackStateCompat.STATE_ERROR);
        mPlaybackStateBuilder.setErrorMessage(null);
    }

    private class TunerSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onStop() {
            mAppService.setMuted(true);
        }

        @Override
        public void onPlay() {
            mAppService.setMuted(false);
        }

        @Override
        public void onSkipToNext() {
            mAppService.seekForward();
        }

        @Override
        public void onSkipToPrevious() {
            mAppService.seekBackward();
        }

        @Override
        public void onSetRating(RatingCompat rating) {
            synchronized (mLock) {
                ProgramInfo info = mAppService.getCurrentProgram().getValue();
                if (info == null) return;

                if (rating.hasHeart()) {
                    mRadioStorage.addFavorite(Program.fromProgramInfo(info));
                } else {
                    mRadioStorage.removeFavorite(info.getSelector());
                }
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            if (mBrowseTree.getRoot().getRootId().equals(mediaId)) {
                // general play command
                onPlay();
                return;
            }

            ProgramSelector selector = mBrowseTree.parseMediaId(mediaId);
            if (selector != null) {
                mAppService.tune(selector);
            } else {
                Log.w(TAG, "Invalid media ID: " + mediaId);
                selectionError();
            }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            ProgramSelector selector = ProgramSelectorExt.fromUri(uri);
            if (selector != null) {
                mAppService.tune(selector);
            } else {
                Log.w(TAG, "Invalid URI: " + uri);
                selectionError();
            }
        }
    }
}
