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
import android.graphics.Color;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;

import androidx.annotation.ColorInt;
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

import java.util.List;
import java.util.Objects;

/**
 * A controller that handles the display of metadata on the current radio station.
 */
public class RadioController {
    private static final String TAG = "BcRadioApp.RadioController";

    /**
     * The percentage by which to darken the color that should be set on the status bar.
     * This darkening gives the status bar the illusion that it is transparent.
     *
     * @see RadioController#setShouldColorStatusBar(boolean)
     */
    private static final float STATUS_BAR_DARKEN_PERCENTAGE = 0.4f;

    /**
     * The animation time for when the background of the radio shifts to a different color.
     */
    private static final int BACKGROUND_CHANGE_ANIM_TIME_MS = 450;
    private static final int INVALID_BACKGROUND_COLOR = 0;

    private static final int CHANNEL_CHANGE_DURATION_MS = 200;

    private final Object mLock = new Object();

    @Nullable private ProgramInfo mCurrentProgram;

    private final ValueAnimator mAnimator = new ValueAnimator();
    private @Nullable ProgramSelector mCurrentlyDisplayedChannel;  // for animation purposes

    private final FragmentActivity mActivity;
    private RadioAppServiceWrapper mAppService = new RadioAppServiceWrapper();

    private View mRadioBackground;
    private boolean mShouldColorStatusBar;

    @ColorInt private int mCurrentBackgroundColor = INVALID_BACKGROUND_COLOR;

    private final DisplayController mDisplayController;

    private final RadioStorage mRadioStorage;

    private final String mAmBandString;
    private final String mFmBandString;

    public RadioController(@NonNull FragmentActivity activity) {
        mActivity = Objects.requireNonNull(activity);

        mDisplayController = new DisplayController(activity, this);

        mAmBandString = activity.getString(R.string.radio_am_text);
        mFmBandString = activity.getString(R.string.radio_fm_text);

        mRadioStorage = RadioStorage.getInstance(activity);
        mRadioStorage.getFavorites().observe(activity, this::onFavoritesChanged);

        mAppService.addConnectedListener(() -> mDisplayController.setEnabled(true));

        mAppService.getCurrentProgram().observe(activity, this::onCurrentProgramChanged);
    }

    /**
     * Initializes this {@link RadioController} to control the UI whose root is the given container.
     */
    public void initialize(View container) {
        mCurrentBackgroundColor = INVALID_BACKGROUND_COLOR;

        mDisplayController.initialize(container);

        mDisplayController.setBackwardSeekButtonListener(mBackwardSeekClickListener);
        mDisplayController.setForwardSeekButtonListener(mForwardSeekClickListener);
        mDisplayController.setPlayButtonCallback(this::onSwitchToPlayState);
        mDisplayController.setFavoriteToggleListener(this::onFavoriteToggled);

        mRadioBackground = container;
    }

    /**
     * See {@link RadioAppServiceWrapper#addConnectedListener}.
     */
    public void addServiceConnectedListener(@NonNull RadioAppServiceWrapper.ConnectedListener l) {
        mAppService.addConnectedListener(l);
    }

    /**
     * See {@link RadioAppServiceWrapper#removeConnectedListener}.
     */
    public void removeServiceConnectedListener(RadioAppServiceWrapper.ConnectedListener listener) {
        mAppService.removeConnectedListener(listener);
    }

    /**
     * Set whether or not this controller should also update the color of the status bar to match
     * the current background color of the radio. The color that will be set on the status bar
     * will be slightly darker, giving the illusion that the status bar is transparent.
     *
     * <p>This method is needed because of scene transitions. Scene transitions do not take into
     * account padding that is added programmatically. Since there is no way to get the height of
     * the status bar and set it in XML, it needs to be done in code. This breaks the scene
     * transition.
     *
     * <p>To make this work, the status bar is not actually translucent; it is colored to appear
     * that way via this method.
     */
    public void setShouldColorStatusBar(boolean shouldColorStatusBar) {
       mShouldColorStatusBar = shouldColorStatusBar;
    }

    /**
     * Starts the controller and establishes connection with {@link RadioAppService}.
     */
    public void start() {
        mAppService.bind(mActivity);
    }

    /**
     * Closes {@link RadioAppService} connection and cleans up the resources.
     */
    public void shutdown() {
        mAppService.unbind();
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

    private void setBackgroundColor(int backgroundColor) {
        mRadioBackground.setBackgroundColor(backgroundColor);

        if (mShouldColorStatusBar) {
            int red = darkenColor(Color.red(backgroundColor));
            int green = darkenColor(Color.green(backgroundColor));
            int blue = darkenColor(Color.blue(backgroundColor));
            int alpha = Color.alpha(backgroundColor);

            mActivity.getWindow().setStatusBarColor(
                    Color.argb(alpha, red, green, blue));
        }
    }

    /**
     * Darkens the given color by {@link #STATUS_BAR_DARKEN_PERCENTAGE}.
     */
    private int darkenColor(int color) {
        return (int) Math.max(color - (color * STATUS_BAR_DARKEN_PERCENTAGE), 0);
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

    private final View.OnClickListener mBackwardSeekClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // TODO(b/73950974): show some kind of animation
            clearMetadataDisplay();

            // TODO(b/73950974): watch for timeout and if it happens, display metadata back
            mAppService.seekBackward();
        }
    };

    private final View.OnClickListener mForwardSeekClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            clearMetadataDisplay();

            mAppService.seekForward();
        }
    };

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
                Log.w(TAG, "Invalid request to switch to play state " + newPlayState);
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

    private final ValueAnimator.AnimatorUpdateListener mBackgroundColorUpdater =
            animator -> {
                int backgroundColor = (int) animator.getAnimatedValue();
                setBackgroundColor(backgroundColor);
            };
}
