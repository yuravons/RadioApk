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

package com.android.car.radio.service;

import android.app.Service;
import android.content.Intent;
import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioTuner;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.media.MediaBrowserServiceCompat;

import com.android.car.broadcastradio.support.media.BrowseTree;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.audio.AudioStreamController;
import com.android.car.radio.audio.IPlaybackStateListener;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.media.TunerSession;
import com.android.car.radio.platform.ImageMemoryCache;
import com.android.car.radio.platform.RadioManagerExt;
import com.android.car.radio.storage.RadioStorage;
import com.android.car.radio.utils.ObserverList;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A persistent {@link Service} that is responsible for opening and closing a {@link RadioTuner}.
 * All radio operations should be delegated to this class. To be notified of any changes in radio
 * metadata, register as a {@link android.hardware.radio.RadioTuner.Callback} on this Service.
 *
 * <p>Utilize the {@link RadioBinder} to perform radio operations.
 */
public class RadioAppService extends MediaBrowserServiceCompat implements LifecycleOwner {

    private static final String TAG = "BcRadioApp.appsrv";

    public static String ACTION_APP_SERVICE = "com.android.car.radio.ACTION_APP_SERVICE";

    private final Object mLock = new Object();

    private final Handler mHandler = new Handler();

    private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    private RadioStorage mRadioStorage;

    private RadioTuner mRadioTuner;

    private RadioManagerExt mRadioManager;
    private ImageMemoryCache mImageCache;

    private AudioStreamController mAudioStreamController;

    private BrowseTree mBrowseTree;
    private TunerSession mMediaSession;
    private ProgramList mProgramList;

    /**
     * An internal {@link android.hardware.radio.RadioTuner.Callback} that will listen for
     * changes in radio metadata and pass these method calls through to
     * {@link #mRadioTunerCallbacks}.
     */
    private RadioTuner.Callback mInternalRadioTunerCallback = new InternalRadioCallback();

    private final ObserverList<ProgramInfo, ICurrentProgramListener> mCurrentProgramListeners =
            new ObserverList<>(null, ICurrentProgramListener::onCurrentProgramChanged);

    @Override
    public void onCreate() {
        super.onCreate();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCreate()");
        }

        mRadioManager = new RadioManagerExt(this);
        mAudioStreamController = new AudioStreamController(this, mRadioManager);
        mRadioStorage = RadioStorage.getInstance(this);
        mImageCache = new ImageMemoryCache(mRadioManager, 1000);

        mBrowseTree = new BrowseTree(this, mImageCache);
        mMediaSession = new TunerSession(this, mBrowseTree, mBinder, mImageCache);
        setSessionToken(mMediaSession.getSessionToken());
        mBrowseTree.setAmFmRegionConfig(mRadioManager.getAmFmRegionConfig());
        mRadioStorage.getFavorites().observe(this,
                favs -> mBrowseTree.setFavorites(new HashSet<>(favs)));

        openRadioBandInternal();

        mLifecycleRegistry.markState(Lifecycle.State.CREATED);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mLifecycleRegistry.markState(Lifecycle.State.STARTED);
        if (ACTION_APP_SERVICE.equals(intent.getAction())) {
            return mBinder;
        }
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mLifecycleRegistry.markState(Lifecycle.State.CREATED);
        return false;
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDestroy()");
        }

        mLifecycleRegistry.markState(Lifecycle.State.DESTROYED);

        mMediaSession.release();
        mRadioManager.getRadioTunerExt().close();
        close();

        super.onDestroy();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    /**
     * Opens the current radio band. Currently, this only supports FM and AM bands.
     *
     * TODO(b/73950974): remove
     *
     * @return {@link RadioManager#STATUS_OK} if successful; otherwise,
     * {@link RadioManager#STATUS_ERROR}.
     */
    private int openRadioBandInternal() {
        if (!mAudioStreamController.requestMuted(false)) return RadioManager.STATUS_ERROR;

        if (mRadioTuner == null) {
            mRadioTuner = mRadioManager.openSession(mInternalRadioTunerCallback, null);
            mProgramList = mRadioTuner.getDynamicProgramList(null);
            mBrowseTree.setProgramList(mProgramList);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "openRadioBandInternal() STATUS_OK");
        }

        tuneToDefault(null);

        return RadioManager.STATUS_OK;
    }

    private void tuneToDefault(@Nullable ProgramType pt) {
        if (!mAudioStreamController.preparePlayback(Optional.empty())) return;

        ProgramSelector sel = mRadioStorage.getRecentlySelected(pt);
        if (sel != null) {
            Log.i(TAG, "Restoring recently selected program: " + sel);
            mRadioTuner.tune(sel);
        } else {
            Log.i(TAG, "No recently selected program, seeking forward to not play static");

            // TODO(b/80500464): don't hardcode, pull from tuner config
            long lastChannel;
            if (pt == ProgramType.AM) lastChannel = 1620;
            else lastChannel = 108000;
            mRadioTuner.tune(ProgramSelectorExt.createAmFmSelector(lastChannel));

            mRadioTuner.scan(RadioTuner.DIRECTION_UP, true);
        }
    }

    /**
     * Closes {@link RadioTuner} and releases audio streams.
     */
    private void close() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "close()");
        }

        mAudioStreamController.requestMuted(true);

        if (mProgramList != null) {
            mProgramList.close();
            mProgramList = null;
        }
        if (mRadioTuner != null) {
            mRadioTuner.close();
            mRadioTuner = null;
        }
    }

    private IRadioAppService.Stub mBinder = new IRadioAppService.Stub() {
        /**
         * Tunes the radio to the given frequency. To be notified of a successful tune, register
         * as a {@link android.hardware.radio.RadioTuner.Callback}.
         */
        @Override
        public void tune(ProgramSelector sel) {
            if (!mAudioStreamController.preparePlayback(Optional.empty())) return;
            mRadioTuner.tune(sel);
        }

        @Override
        public List<ProgramInfo> getProgramList() {
            return mRadioTuner.getDynamicProgramList(null).toList();
        }

        /**
         * Seeks the radio forward. To be notified of a successful tune, register as a
         * {@link android.hardware.radio.RadioTuner.Callback}.
         */
        @Override
        public void seekForward() {
            if (!mAudioStreamController.preparePlayback(Optional.of(true))) return;
            mRadioTuner.scan(RadioTuner.DIRECTION_UP, true);
        }

        /**
         * Seeks the radio backwards. To be notified of a successful tune, register as a
         * {@link android.hardware.radio.RadioTuner.Callback}.
         */
        @Override
        public void seekBackward() {
            if (!mAudioStreamController.preparePlayback(Optional.of(false))) return;
            mRadioTuner.scan(RadioTuner.DIRECTION_DOWN, true);
        }

        @Override
        public void setMuted(boolean muted) {
            mAudioStreamController.requestMuted(muted);
        }

        @Override
        public void switchBand(ProgramType band) {
            tuneToDefault(band);
        }

        @Override
        public void addCurrentProgramListener(ICurrentProgramListener listener) {
            mCurrentProgramListeners.add(listener);
        }

        @Override
        public void removeCurrentProgramListener(ICurrentProgramListener listener) {
            mCurrentProgramListeners.remove(listener);
        }

        @Override
        public void addPlaybackStateListener(IPlaybackStateListener callback) {
            mAudioStreamController.addPlaybackStateListener(callback);
        }

        @Override
        public void removePlaybackStateListener(IPlaybackStateListener callback) {
            mAudioStreamController.removePlaybackStateListener(callback);
        }
    };

    /**
     * A extension of {@link android.hardware.radio.RadioTuner.Callback} that delegates to a
     * callback registered on this service.
     */
    private class InternalRadioCallback extends RadioTuner.Callback {
        @Override
        public void onProgramInfoChanged(ProgramInfo info) {
            Objects.requireNonNull(info);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Program info changed: " + info);
            }

            mAudioStreamController.notifyProgramInfoChanged();

            /* This might be in response only to explicit tune calls (including next/prev seek),
             * but it would be nontrivial with current API. */
            mRadioStorage.setRecentlySelected(info.getSelector());

            mCurrentProgramListeners.update(info);
        }

        @Override
        public void onError(int status) {
            switch (status) {
                case RadioTuner.ERROR_HARDWARE_FAILURE:
                case RadioTuner.ERROR_SERVER_DIED:
                    // this should be handled by RadioService, not an app
                    Log.e(TAG, "Fatal hardware error: " + status);
                    close();
                    stopSelf();
                    break;
                default:
                    Log.w(TAG, "Hardware error: " + status);
            }
        }

        @Override
        public void onControlChanged(boolean control) {
            if (control) return;
            close();
            stopSelf();
        }
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        /* Radio application may restrict who can read its MediaBrowser tree.
         * Our implementation doesn't.
         */
        return mBrowseTree.getRoot();
    }

    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        mBrowseTree.loadChildren(parentMediaId, result);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mLifecycleRegistry.markState(Lifecycle.State.STARTED);
        if (BrowseTree.ACTION_PLAY_BROADCASTRADIO.equals(intent.getAction())) {
            Log.i(TAG, "Executing general play radio intent");
            mMediaSession.getController().getTransportControls().playFromMediaId(
                    mBrowseTree.getRoot().getRootId(), null);
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }
}
