/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.radio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.platform.ProgramInfoExt;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.service.RadioAppService;
import com.android.car.radio.service.RadioAppServiceWrapper;
import com.android.car.radio.storage.RadioStorage;
import com.android.car.radio.util.Log;

import java.util.List;
import java.util.Objects;

/**
 * The main controller of the radio app.
 */
public class RadioController {
    private static final String TAG = "BcRadioApp.controller";

    private static final int CHANNEL_CHANGE_DURATION_MS = 200;

    private final Object mLock = new Object();
    private final Context mContext;

    private final RadioAppServiceWrapper mAppService = new RadioAppServiceWrapper();
    private final DisplayController mDisplayController;
    private final RadioStorage mRadioStorage;

    @Nullable private ProgramInfo mCurrentProgram;

    private final ValueAnimator mAnimator = new ValueAnimator();
    private @Nullable ProgramSelector mCurrentlyDisplayedChannel;

    public RadioController(@NonNull FragmentActivity activity) {
        mContext = Objects.requireNonNull(activity);

        mDisplayController = new DisplayController(activity, this);

        mRadioStorage = RadioStorage.getInstance(activity);
        mRadioStorage.getFavorites().observe(activity, this::onFavoritesChanged);

        mAppService.getCurrentProgram().observe(activity, this::onCurrentProgramChanged);
        mAppService.isConnected().observe(activity, mDisplayController::setEnabled);

        mDisplayController.setBackwardSeekButtonListener(this::onBackwardSeekClick);
        mDisplayController.setForwardSeekButtonListener(this::onForwardSeekClick);
        mDisplayController.setPlayButtonCallback(this::onSwitchToPlayState);
        mDisplayController.setFavoriteToggleListener(this::onFavoriteToggled);
    }

    /**
     * Starts the controller and establishes connection with {@link RadioAppService}.
     */
    public void start() {
        mAppService.bind(mContext);
    }

    /**
     * Closes {@link RadioAppService} connection and cleans up the resources.
     */
    public void shutdown() {
        mAppService.unbind();
    }

    /**
     * See {@link RadioAppServiceWrapper#isConnected}.
     */
    @NonNull
    public LiveData<Boolean> isConnected() {
        return mAppService.isConnected();
    }

    /**
     * See {@link RadioAppServiceWrapper#getPlaybackState}.
     */
    @NonNull
    public LiveData<Integer> getPlaybackState() {
        return mAppService.getPlaybackState();
    }

    /**
     * See {@link RadioAppServiceWrapper#getCurrentProgram}.
     */
    @NonNull
    public LiveData<ProgramInfo> getCurrentProgram() {
        return mAppService.getCurrentProgram();
    }

    /**
     * Tunes the radio to the given channel if it is valid and a {@link RadioTuner} has been opened.
     */
    public void tune(ProgramSelector sel) {
        mAppService.tune(sel);
    }

    /**
     * Switch radio band. Currently, this only supports FM and AM bands.
     *
     * @param pt {@link ProgramType} to switch to.
     */
    public void switchBand(@NonNull ProgramType pt) {
        mAppService.switchBand(pt);
    }

    // TODO(b/73950974): move channel animation to DisplayController
    private void updateRadioChannelDisplay(@NonNull ProgramSelector sel) {
        int priType = sel.getPrimaryId().getType();

        mAnimator.cancel();

        if (!ProgramSelectorExt.isAmFmProgram(sel)
                || !ProgramSelectorExt.hasId(sel, ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)) {
            // channel animation is implemented for AM/FM only
            mCurrentlyDisplayedChannel = null;
            mDisplayController.setChannel(ProgramSelectorExt.getDisplayName(sel, 0));
            return;
        }

        if (ProgramType.fromSelector(mCurrentlyDisplayedChannel) == ProgramType.fromSelector(sel)) {
            int fromFreq = (int) mCurrentlyDisplayedChannel
                    .getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
            int toFreq = (int) sel.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
            mAnimator.setIntValues((int) fromFreq, (int) toFreq);
            mAnimator.setDuration(CHANNEL_CHANGE_DURATION_MS);
            mAnimator.addUpdateListener(animation -> mDisplayController.setChannel(
                    ProgramSelectorExt.formatAmFmFrequency((int) animation.getAnimatedValue(), 0)));
            mAnimator.start();
        } else {
            // it's a different band - don't animate
            mDisplayController.setChannel(ProgramSelectorExt.getDisplayName(sel, 0));
        }
        mCurrentlyDisplayedChannel = sel;
    }

    /**
     * Clears all metadata including song title, artist and station information.
     */
    private void clearMetadataDisplay() {
        mDisplayController.setCurrentStation(null);
        mDisplayController.setCurrentSongTitleAndArtist(null, null);
    }

    private void onFavoritesChanged(List<Program> favorites) {
        synchronized (mLock) {
            if (mCurrentProgram == null) return;
            boolean isFav = RadioStorage.isFavorite(favorites, mCurrentProgram.getSelector());
            mDisplayController.setCurrentIsFavorite(isFav);
        }
    }

    /**
     * Gets a list of programs from the radio tuner's background scan
     */
    public List<ProgramInfo> getProgramList() {
        return mAppService.getProgramList();
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        mCurrentProgram = Objects.requireNonNull(info);
        ProgramSelector sel = info.getSelector();

        updateRadioChannelDisplay(sel);

        mDisplayController.setCurrentStation(
                ProgramInfoExt.getProgramName(info, ProgramInfoExt.NAME_NO_CHANNEL_FALLBACK));
        RadioMetadata meta = ProgramInfoExt.getMetadata(info);
        mDisplayController.setCurrentSongTitleAndArtist(
                meta.getString(RadioMetadata.METADATA_KEY_TITLE),
                meta.getString(RadioMetadata.METADATA_KEY_ARTIST));
        mDisplayController.setCurrentIsFavorite(mRadioStorage.isFavorite(sel));
    }

    private void onBackwardSeekClick(View v) {
        // TODO(b/73950974): show some kind of animation
        clearMetadataDisplay();

        // TODO(b/73950974): watch for timeout and if it happens, display metadata back
        mAppService.seekBackward();
    }

    private void onForwardSeekClick(View v) {
        clearMetadataDisplay();
        mAppService.seekForward();
    }

    private void onSwitchToPlayState(@PlaybackStateCompat.State int newPlayState) {
        switch (newPlayState) {
            case PlaybackStateCompat.STATE_PLAYING:
                mAppService.setMuted(false);
                break;
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
                mAppService.setMuted(true);
                break;
            default:
                Log.e(TAG, "Invalid request to switch to play state " + newPlayState);
        }
    }

    private void onFavoriteToggled(boolean addFavorite) {
        ProgramInfo info = mCurrentProgram;
        if (info == null) return;

        if (addFavorite) {
            mRadioStorage.addFavorite(Program.fromProgramInfo(info));
        } else {
            mRadioStorage.removeFavorite(info.getSelector());
        }
    }
}
