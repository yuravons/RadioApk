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
 * limitations under the License
 */

package com.android.car.radio;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;

import com.android.car.radio.media.Program;
import com.android.car.radio.media.BrowseTree;
import com.android.car.radio.media.TunerSession;
import com.android.car.radio.service.IRadioCallback;
import com.android.car.radio.service.IRadioManager;
import com.android.car.radio.platform.ImageMemoryCache;
import com.android.car.radio.platform.ProgramInfoExt;
import com.android.car.radio.platform.ProgramSelectorExt;
import com.android.car.radio.platform.RadioManagerExt;
import com.android.car.radio.platform.RadioTunerExt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A persistent {@link Service} that is responsible for opening and closing a {@link RadioTuner}.
 * All radio operations should be delegated to this class. To be notified of any changes in radio
 * metadata, register as a {@link android.hardware.radio.RadioTuner.Callback} on this Service.
 *
 * <p>Utilize the {@link RadioBinder} to perform radio operations.
 */
public class RadioService extends MediaBrowserServiceCompat
        implements AudioManager.OnAudioFocusChangeListener {

    private static String TAG = "BcRadioApp.uisrv";

    public static String ACTION_UI_SERVICE = "com.android.car.radio.ACTION_UI_SERVICE";

    /**
     * The amount of time to wait before re-trying to open the {@link #mRadioTuner}.
     */
    private static final int RADIO_TUNER_REOPEN_DELAY_MS = 5000;

    private final Object mLock = new Object();

    private int mReOpenRadioTunerCount = 0;
    private final Handler mHandler = new Handler();

    private RadioStorage mRadioStorage;
    private final RadioStorage.PresetsChangeListener mPresetsListener = this::onPresetsChanged;

    private RadioTuner mRadioTuner;

    private boolean mRadioSuccessfullyInitialized;

    private ProgramInfo mCurrentProgram;
    private int mCurrentRadioBand = RadioManager.BAND_FM;

    private RadioManagerExt mRadioManager;
    private ImageMemoryCache mImageCache;

    private AudioManager mAudioManager;
    private AudioAttributes mRadioAudioAttributes;

    private BrowseTree mBrowseTree;
    private TunerSession mMediaSession;
    private ProgramList mProgramList;

    /**
     * Whether or not this {@link RadioService} currently has audio focus, meaning it is the
     * primary driver of media. Usually, interaction with the radio will be prefaced with an
     * explicit request for audio focus. However, this is not ideal when muting the radio, so this
     * state needs to be tracked.
     */
    private boolean mHasAudioFocus;

    /**
     * An internal {@link android.hardware.radio.RadioTuner.Callback} that will listen for
     * changes in radio metadata and pass these method calls through to
     * {@link #mRadioTunerCallbacks}.
     */
    private RadioTuner.Callback mInternalRadioTunerCallback = new InternalRadioCallback();
    private List<IRadioCallback> mRadioTunerCallbacks = new ArrayList<>();

    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION_UI_SERVICE.equals(intent.getAction())) {
            return mBinder;
        }
        return super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCreate()");
        }

        mRadioStorage = RadioStorage.getInstance(this);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mRadioAudioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        mRadioManager = new RadioManagerExt(this);
        mImageCache = new ImageMemoryCache(mRadioManager, 1000);
        mBrowseTree = new BrowseTree(this, mImageCache);
        mMediaSession = new TunerSession(this, mBrowseTree, mBinder, mImageCache);
        setSessionToken(mMediaSession.getSessionToken());

        mBrowseTree.setAmFmRegionConfig(mRadioManager.getAmFmRegionConfig());
        openRadioBandInternal(mCurrentRadioBand);

        mRadioStorage.addPresetsChangeListener(mPresetsListener);
        onPresetsChanged();

        mRadioSuccessfullyInitialized = true;
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDestroy()");
        }

        mRadioStorage.removePresetsChangeListener(mPresetsListener);
        mMediaSession.release();
        mRadioManager.getRadioTunerExt().close();
        close();

        super.onDestroy();
    }

    private void onPresetsChanged() {
        synchronized (mLock) {
            mBrowseTree.setFavorites(new HashSet<>(mRadioStorage.getPresets()));
            mMediaSession.notifyFavoritesChanged();
        }
    }

    /**
     * Opens the current radio band. Currently, this only supports FM and AM bands.
     *
     * @param radioBand One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM},
     *                  {@link RadioManager#BAND_FM_HD} or {@link RadioManager#BAND_AM_HD}.
     * @return {@link RadioManager#STATUS_OK} if successful; otherwise,
     * {@link RadioManager#STATUS_ERROR}.
     */
    private int openRadioBandInternal(int radioBand) {
        if (requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e(TAG, "openRadioBandInternal() audio focus request fail");
            return RadioManager.STATUS_ERROR;
        }

        mCurrentRadioBand = radioBand;
        if (mRadioTuner == null) {
            mRadioTuner = mRadioManager.openSession(mInternalRadioTunerCallback, null);
            mProgramList = mRadioTuner.getDynamicProgramList(null);
            mBrowseTree.setProgramList(mProgramList);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "openRadioBandInternal() STATUS_OK");
        }

        // Reset the counter for exponential backoff each time the radio tuner has been successfully
        // opened.
        mReOpenRadioTunerCount = 0;

        return RadioManager.STATUS_OK;
    }

    private int requestAudioFocus() {
        int status = mAudioManager.requestAudioFocus(this, mRadioAudioAttributes,
                    AudioManager.AUDIOFOCUS_GAIN, 0);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "requestAudioFocus status: " + status);
        }

        if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mHasAudioFocus = true;

            mRadioManager.getRadioTunerExt().setMuted(false);

            for (IRadioCallback callback : mRadioTunerCallbacks) {
                try {
                    callback.onRadioMuteChanged(false);
                } catch (RemoteException e) {
                    Log.e(TAG, "requestAudioFocus(); onRadioMuteChanged() notify failed: "
                            + e.getMessage());
                }
            }
        }

        return status;
    }

    private void abandonAudioFocus() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "abandonAudioFocus()");
        }

        mAudioManager.abandonAudioFocus(this, mRadioAudioAttributes);
        mHasAudioFocus = false;

        for (IRadioCallback callback : mRadioTunerCallbacks) {
            try {
                callback.onRadioMuteChanged(true);
            } catch (RemoteException e) {
                Log.e(TAG, "abandonAudioFocus(); onRadioMutechanged() notify failed: "
                        + e.getMessage());
            }
        }
    }

    /**
     * Closes any active {@link RadioTuner}s and releases audio focus.
     */
    private void close() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "close()");
        }

        abandonAudioFocus();

        if (mProgramList != null) {
            mProgramList.close();
            mProgramList = null;
        }
        if (mRadioTuner != null) {
            mRadioTuner.close();
            mRadioTuner = null;
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "focus change: " + focusChange);
        }

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                mHasAudioFocus = true;
                openRadioBandInternal(mCurrentRadioBand);
                break;

            // For a transient loss, just allow the focus to be released. The radio will stop
            // itself automatically. There is no need for an explicit abandon audio focus call
            // because this removes the AudioFocusChangeListener.
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mHasAudioFocus = false;
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                close();
                break;

            default:
                // Do nothing for all other cases.
        }
    }

    private IRadioManager.Stub mBinder = new IRadioManager.Stub() {
        /**
         * Tunes the radio to the given frequency. To be notified of a successful tune, register
         * as a {@link android.hardware.radio.RadioTuner.Callback}.
         */
        @Override
        public void tune(ProgramSelector sel) {
            if (mRadioManager == null || sel == null
                    || requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return;
            }

            mRadioTuner.tune(sel);
        }

        /**
         * Seeks the radio forward. To be notified of a successful tune, register as a
         * {@link android.hardware.radio.RadioTuner.Callback}.
         */
        @Override
        public void seekForward() {
            if (mRadioManager == null
                    || requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return;
            }

            if (mRadioTuner == null) {
                int radioStatus = openRadioBandInternal(mCurrentRadioBand);
                if (radioStatus == RadioManager.STATUS_ERROR) {
                    return;
                }
            }

            mRadioTuner.scan(RadioTuner.DIRECTION_UP, true);
        }

        /**
         * Seeks the radio backwards. To be notified of a successful tune, register as a
         * {@link android.hardware.radio.RadioTuner.Callback}.
         */
        @Override
        public void seekBackward() {
            if (mRadioManager == null
                    || requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return;
            }

            if (mRadioTuner == null) {
                int radioStatus = openRadioBandInternal(mCurrentRadioBand);
                if (radioStatus == RadioManager.STATUS_ERROR) {
                    return;
                }
            }

            mRadioTuner.scan(RadioTuner.DIRECTION_DOWN, true);
        }

        private boolean setMuted(boolean mute) {
            if (!mRadioManager.getRadioTunerExt().setMuted(mute)) return false;

            for (IRadioCallback callback : mRadioTunerCallbacks) {
                try {
                    callback.onRadioMuteChanged(mute);
                } catch (RemoteException e) {
                    Log.e(TAG, "onRadioMuteChanged callback failed", e);
                }
            }

            return true;
        }

        /**
         * Mutes the radio.
         *
         * @return {@code true} if the mute was successful.
         */
        @Override
        public boolean mute() {
            return setMuted(true);
        }

        /**
         * Un-mutes the radio and causes audio to play.
         *
         * @return {@code true} if the un-mute was successful.
         */
        @Override
        public boolean unMute() {
            if (!setMuted(false)) return false;

            return requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        /**
         * Returns {@code true} if the radio is currently muted.
         */
        @Override
        public boolean isMuted() {
            return mRadioManager.getRadioTunerExt().isMuted();
        }

        @Override
        public void addFavorite(Program program) {
            mRadioStorage.storePreset(program);
        }

        @Override
        public void removeFavorite(ProgramSelector sel) {
            mRadioStorage.removePreset(sel);
        }

        /**
         * Opens the radio for the given band.
         *
         * @param radioBand One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM},
         *                  {@link RadioManager#BAND_FM_HD} or {@link RadioManager#BAND_AM_HD}.
         * @return {@link RadioManager#STATUS_OK} if successful; otherwise,
         * {@link RadioManager#STATUS_ERROR}.
         */
        @Override
        public int openRadioBand(int radioBand) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "openRadioBand() for band: " + radioBand);
            }

            if (mRadioManager == null) {
                return RadioManager.STATUS_ERROR;
            }

            return openRadioBandInternal(radioBand);
        }

        /**
         * Adds the given {@link android.hardware.radio.RadioTuner.Callback} to be notified
         * of any radio metadata changes.
         */
        @Override
        public void addRadioTunerCallback(IRadioCallback callback) {
            if (callback == null) {
                return;
            }

            mRadioTunerCallbacks.add(callback);
        }

        /**
         * Removes the given {@link android.hardware.radio.RadioTuner.Callback} from receiving
         * any radio metadata chagnes.
         */
        @Override
        public void removeRadioTunerCallback(IRadioCallback callback) {
            if (callback == null) {
                return;
            }

            mRadioTunerCallbacks.remove(callback);
        }

        @Override
        public ProgramInfo getCurrentProgramInfo() {
            return mCurrentProgram;
        }

        /**
         * Returns {@code true} if the radio was able to successfully initialize. A value of
         * {@code false} here could mean that the {@code RadioService} was not able to connect to
         * the {@link RadioManager} or there were no radio modules on the current device.
         */
        @Override
        public boolean isInitialized() {
            return mRadioSuccessfullyInitialized;
        }

        /**
         * Returns {@code true} if the radio currently has focus and is therefore the application
         * that is supplying music.
         */
        @Override
        public boolean hasFocus() {
            return mHasAudioFocus;
        }
    };

    /**
     * A extension of {@link android.hardware.radio.RadioTuner.Callback} that delegates to a
     * callback registered on this service.
     */
    private class InternalRadioCallback extends RadioTuner.Callback {
        @Override
        public void onProgramInfoChanged(ProgramInfo info) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Program info changed: " + info);
            }

            mCurrentProgram = Objects.requireNonNull(info);
            mMediaSession.notifyProgramInfoChanged(info);

            for (IRadioCallback callback : mRadioTunerCallbacks) {
                try {
                    callback.onCurrentProgramInfoChanged(info);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to notify about changed radio station", e);
                }
            }
        }

        @Override
        public void onConfigurationChanged(RadioManager.BandConfig config) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConfigurationChanged(): config: " + config);
            }

            if (config != null) {
                mCurrentRadioBand = config.getType();

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onConfigurationChanged(): config type: " + mCurrentRadioBand);
                }

            }

            try {
                for (IRadioCallback callback : mRadioTunerCallbacks) {
                    callback.onRadioBandChanged(mCurrentRadioBand);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onConfigurationChanged(); "
                        + "Failed to notify IRadioCallbacks: " + e.getMessage());
            }
        }

        @Override
        public void onError(int status) {
            Log.e(TAG, "onError(); status: " + status);

            // If there is a hardware failure or the radio service died, then this requires a
            // re-opening of the radio tuner.
            if (status == RadioTuner.ERROR_HARDWARE_FAILURE
                    || status == RadioTuner.ERROR_SERVER_DIED) {
                close();

                // Attempt to re-open the RadioTuner. Each time the radio tuner fails to open, the
                // mReOpenRadioTunerCount will be incremented.
                mHandler.removeCallbacks(mOpenRadioTunerRunnable);
                mHandler.postDelayed(mOpenRadioTunerRunnable,
                        mReOpenRadioTunerCount * RADIO_TUNER_REOPEN_DELAY_MS);

                mReOpenRadioTunerCount++;
            }

            try {
                for (IRadioCallback callback : mRadioTunerCallbacks) {
                    callback.onError(status);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onError(); Failed to notify IRadioCallbacks: " + e.getMessage());
            }
        }

        @Override
        public void onControlChanged(boolean control) {
            // If the radio loses control of the RadioTuner, then close it and allow it to be
            // re-opened when control has been gained.
            if (!control) {
                close();
                return;
            }

            if (mRadioTuner == null) {
                openRadioBandInternal(mCurrentRadioBand);
            }
        }
    }

    private final Runnable mOpenRadioTunerRunnable = () -> openRadioBandInternal(mCurrentRadioBand);

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // TODO(b/75970985): check permissions, if necessary
        return mBrowseTree.getRoot();
    }

    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        mBrowseTree.loadChildren(parentMediaId, result);
    }
}
