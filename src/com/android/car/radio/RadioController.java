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

import static com.android.car.radio.utils.Remote.exec;
import static com.android.car.radio.utils.Remote.tryExec;

import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.platform.ProgramInfoExt;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.audio.IPlaybackStateListener;
import com.android.car.radio.service.CurrentProgramListenerAdapter;
import com.android.car.radio.service.ICurrentProgramListener;
import com.android.car.radio.service.IRadioAppService;
import com.android.car.radio.service.RadioAppService;
import com.android.car.radio.storage.RadioStorage;
import com.android.car.radio.utils.ProgramSelectorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A controller that handles the display of metadata on the current radio station.
 */
public class RadioController implements RadioStorage.PresetsChangeListener {
    private static final String TAG = "Em.RadioController";

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

    private final ValueAnimator mAnimator = new ValueAnimator();
    private int mCurrentlyDisplayedChannel;  // for animation purposes
    private ProgramInfo mCurrentProgram;  // TODO(b/73950974): remove

    private final Activity mActivity;
    private IRadioAppService mAppService;

    private View mRadioBackground;
    private boolean mShouldColorStatusBar;

    @ColorInt private int mCurrentBackgroundColor = INVALID_BACKGROUND_COLOR;

    private final DisplayController mDisplayController;

    private final RadioStorage mRadioStorage;

    private final String mAmBandString;
    private final String mFmBandString;

    private final List<RadioServiceConnectionListener> mRadioServiceConnectionListeners =
            new ArrayList<>();

    private final ICurrentProgramListener mCurrentProgramListener =
            new CurrentProgramListenerAdapter(this::onCurrentProgramChanged);

    /**
     * Interface for a class that will be notified when RadioService is successfuly bound
     */
    public interface RadioServiceConnectionListener {

        /**
         * Called when the RadioService is successfully connected
         */
        void onRadioServiceConnected();
    }

    public RadioController(Activity activity) {
        mActivity = activity;

        mDisplayController = new DisplayController(mActivity, this);

        mAmBandString = mActivity.getString(R.string.radio_am_text);
        mFmBandString = mActivity.getString(R.string.radio_fm_text);

        mRadioStorage = RadioStorage.getInstance(mActivity);
        mRadioStorage.addPresetsChangeListener(this);
    }

    /**
     * Initializes this {@link RadioController} to control the UI whose root is the given container.
     */
    public void initialize(View container) {
        mCurrentBackgroundColor = INVALID_BACKGROUND_COLOR;

        mDisplayController.initialize(container);

        mDisplayController.setBackwardSeekButtonListener(mBackwardSeekClickListener);
        mDisplayController.setForwardSeekButtonListener(mForwardSeekClickListener);
        mDisplayController.setPlayButtonListener(mPlayPauseClickListener);
        mDisplayController.setAddPresetButtonListener(mPresetButtonClickListener);

        mRadioBackground = container;
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
     * See {@link IRadioAppService#addCurrentProgramListener}.
     */
    public void addCurrentProgramListener(@NonNull ICurrentProgramListener listener) {
        exec(() -> mAppService.addCurrentProgramListener(Objects.requireNonNull(listener)));
    }

    /**
     * See {@link IRadioAppService#removeCurrentProgramListener}.
     */
    public void removeCurrentProgramListener(@Nullable ICurrentProgramListener listener) {
        if (mAppService == null) return;
        exec(() -> mAppService.removeCurrentProgramListener(listener));
    }

    /**
     * See {@link IRadioAppService#addPlaybackStateListener}.
     */
    public void addPlaybackStateListener(@NonNull IPlaybackStateListener listener) {
        exec(() -> mAppService.addPlaybackStateListener(Objects.requireNonNull(listener)));
    }

    /**
     * See {@link IRadioAppService#removePlaybackStateListener}.
     */
    public void removePlaybackStateListener(@Nullable IPlaybackStateListener listener) {
        if (mAppService == null) return;
        exec(() -> mAppService.removePlaybackStateListener(listener));
    }

    /**
     * Sets the listeners that will be notified when the radio service is connected.
     */
    public void addRadioServiceConnectionListener(RadioServiceConnectionListener listener) {
        mRadioServiceConnectionListeners.add(listener);
    }

    /**
     * Removes a listener that will be notified when the radio service is connected.
     */
    public void removeRadioServiceConnectionListener(RadioServiceConnectionListener listener) {
        mRadioServiceConnectionListeners.remove(listener);
    }

    /**
     * Starts the controller to handle radio tuning. This method should be called to begin
     * radio playback.
     */
    public void start() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "starting radio");
        }

        Intent bindIntent = new Intent(RadioAppService.ACTION_APP_SERVICE, null /* uri */,
                mActivity, RadioAppService.class);
        if (!mActivity.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to connect to RadioAppService.");
        }
    }

    /**
     * Tunes the radio to the given channel if it is valid and a {@link RadioTuner} has been opened.
     */
    public void tune(ProgramSelector sel) {
        exec(() -> mAppService.tune(sel));
    }

    /**
     * Returns the band this radio is currently tuned to.
     *
     * TODO(b/73950974): don't be AM/FM exclusive
     */
    public int getCurrentRadioBand() {
        return ProgramSelectorUtils.getRadioBand(mCurrentProgram.getSelector());
    }

    /**
     * Switch radio band. Currently, this only supports FM and AM bands.
     *
     * @param radioBand One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM}.
     */
    public void switchBand(int radioBand) {
        exec(() -> mAppService.switchBand(radioBand));
    }

    /**
     * Delegates to the {@link DisplayController} to highlight the radio band.
     */
    private void updateAmFmDisplayState(int band) {
        switch (band) {
            case RadioManager.BAND_FM:
                mDisplayController.setChannelBand(mFmBandString);
                break;

            case RadioManager.BAND_AM:
                mDisplayController.setChannelBand(mAmBandString);
                break;

            // TODO: Support BAND_FM_HD and BAND_AM_HD.

            default:
                mDisplayController.setChannelBand(null);
        }
    }

    // TODO(b/73950974): move channel animation to DisplayController
    private void updateRadioChannelDisplay(@NonNull ProgramSelector sel) {
        int priType = sel.getPrimaryId().getType();

        mAnimator.cancel();

        if (!ProgramSelectorExt.isAmFmProgram(sel)
                || !ProgramSelectorExt.hasId(sel, ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)) {
            // channel animation is implemented for AM/FM only
            mCurrentlyDisplayedChannel = 0;
            mDisplayController.setChannelNumber("");

            updateAmFmDisplayState(RadioStorage.INVALID_RADIO_BAND);
            return;
        }

        int freq = (int)sel.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);

        boolean wasAm = ProgramSelectorExt.isAmFrequency(mCurrentlyDisplayedChannel);
        boolean wasFm = ProgramSelectorExt.isFmFrequency(mCurrentlyDisplayedChannel);
        boolean isAm = ProgramSelectorExt.isAmFrequency(freq);
        int band = isAm ? RadioManager.BAND_AM : RadioManager.BAND_FM;

        updateAmFmDisplayState(band);

        if (isAm && wasAm || !isAm && wasFm) {
            mAnimator.setIntValues((int)mCurrentlyDisplayedChannel, (int)freq);
            mAnimator.setDuration(CHANNEL_CHANGE_DURATION_MS);
            mAnimator.addUpdateListener(animation -> mDisplayController.setChannelNumber(
                    ProgramSelectorExt.formatAmFmFrequency((int)animation.getAnimatedValue(),
                            ProgramSelectorExt.NAME_NO_MODULATION)));
            mAnimator.start();
        } else {
            // it's a different band - don't animate
            mDisplayController.setChannelNumber(
                    ProgramSelectorExt.getDisplayName(sel, ProgramSelectorExt.NAME_NO_MODULATION));
        }
        mCurrentlyDisplayedChannel = freq;
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

    /**
     * Closes all active connections in the {@link RadioController}.
     */
    public void shutdown() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "shutdown()");
        }

        mActivity.unbindService(mServiceConnection);
        mRadioStorage.removePresetsChangeListener(this);

        if (mAppService != null) {
            tryExec(() -> mAppService.removeCurrentProgramListener(mCurrentProgramListener));
        }
    }

    @Override
    public void onPresetsRefreshed() {
        // Check if the current channel's preset status has changed.
        ProgramInfo info = mCurrentProgram;
        boolean isPreset = (info != null) && mRadioStorage.isPreset(info.getSelector());
        mDisplayController.setChannelIsPreset(isPreset);
    }

    /**
     * Gets a list of programs from the radio tuner's background scan
     */
    public List<ProgramInfo> getProgramList() {
        if (mAppService != null) {
            return exec(() -> mAppService.getProgramList());
        }
        return null;
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        mCurrentProgram = Objects.requireNonNull(info);
        ProgramSelector sel = info.getSelector();

        updateRadioChannelDisplay(sel);

        mDisplayController.setCurrentStation(
                ProgramInfoExt.getProgramName(info, ProgramInfoExt.NAME_NO_CHANNEL_FALLBACK));
        RadioMetadata meta = ProgramInfoExt.getMetadata(mCurrentProgram);
        mDisplayController.setCurrentSongTitleAndArtist(
                meta.getString(RadioMetadata.METADATA_KEY_TITLE),
                meta.getString(RadioMetadata.METADATA_KEY_ARTIST));
        mDisplayController.setChannelIsPreset(mRadioStorage.isPreset(sel));
    }

    private final View.OnClickListener mBackwardSeekClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mAppService == null) return;

            // TODO(b/73950974): show some kind of animation
            clearMetadataDisplay();

            // TODO(b/73950974): watch for timeout and if it happens, display metadata back
            exec(() -> mAppService.seekBackward());
        }
    };

    private final View.OnClickListener mForwardSeekClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mAppService == null) return;

            clearMetadataDisplay();

            exec(() -> mAppService.seekForward());
        }
    };

    /**
     * Click listener for the play/pause button. Currently, all this does is mute/unmute the radio
     * because the {@link RadioManager} does not support the ability to pause/start again.
     */
    private final View.OnClickListener mPlayPauseClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mAppService == null) {
                return;
            }

            try {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Play button clicked. Currently muted: " + mAppService.isMuted());
                }

                if (mAppService.isMuted()) {
                    mAppService.unMute();
                } else {
                    mAppService.mute();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "playPauseClickListener(); remote exception: " + e.getMessage());
            }
        }
    };

    private final View.OnClickListener mPresetButtonClickListener = new View.OnClickListener() {
        // TODO: Maybe add a check to send a store/remove preset event after a delay so that
        // there aren't multiple writes if the user presses the button quickly.
        @Override
        public void onClick(View v) {
            ProgramInfo info = mCurrentProgram;
            if (info == null) return;

            ProgramSelector sel = mCurrentProgram.getSelector();
            boolean isPreset = mRadioStorage.isPreset(sel);

            if (isPreset) {
                mRadioStorage.removePreset(sel);
            } else {
                mRadioStorage.storePreset(Program.fromProgramInfo(info));
            }

            // Update the UI immediately. If the preset failed for some reason, the RadioStorage
            // will notify us and UI update will happen then.
            mDisplayController.setChannelIsPreset(!isPreset);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            mAppService = (IRadioAppService) binder;

            try {
                if (mAppService == null) {
                    mDisplayController.setEnabled(false);
                }

                mDisplayController.setEnabled(true);

                mAppService.addCurrentProgramListener(mCurrentProgramListener);

                // Notify listeners
                for (RadioServiceConnectionListener listener : mRadioServiceConnectionListeners) {
                    listener.onRadioServiceConnected();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onServiceConnected(); remote exception: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mAppService = null;
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mBackgroundColorUpdater =
            animator -> {
                int backgroundColor = (int) animator.getAnimatedValue();
                setBackgroundColor(backgroundColor);
            };
}
