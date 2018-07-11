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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.radio.bands.ProgramType;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link IRadioAppService} wrapper to abstract out some nuances of interactions
 * with remote services.
 */
public class RadioAppServiceWrapper {
    private Context mClientContext;
    @Nullable
    private final AtomicReference<IRadioAppService> mService = new AtomicReference<>();

    private final MutableLiveData<Boolean> mIsConnected = new MutableLiveData<>();
    private final MutableLiveData<Integer> mPlaybackState = new MutableLiveData<>();
    private final MutableLiveData<ProgramInfo> mCurrentProgram = new MutableLiveData<>();

    {
        mPlaybackState.postValue(PlaybackStateCompat.STATE_NONE);
    }

    /**
     * Wraps remote service instance.
     *
     * You must call {@link #bind} once the context is available.
     */
    public RadioAppServiceWrapper() {}

    /**
     * Wraps existing (local) service instance.
     *
     * For use by the RadioAppService itself.
     */
    public RadioAppServiceWrapper(@NonNull IRadioAppService service) {
        Objects.requireNonNull(service);
        mService.set(service);
        initialize(service);
    }

    private void initialize(@NonNull IRadioAppService service) {
        try {
            service.addCallback(mCallback);
        } catch (RemoteException e) {
            throw new RuntimeException("Wrapper initialization failed", e);
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            RadioAppServiceWrapper.this.onServiceConnected(
                    Objects.requireNonNull(IRadioAppService.Stub.asInterface(binder)));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            RadioAppServiceWrapper.this.onServiceDisconnected();
        }
    };

    private final IRadioAppCallback mCallback = new IRadioAppCallback.Stub() {
        @Override
        public void onCurrentProgramChanged(ProgramInfo info) {
            mCurrentProgram.postValue(info);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            mPlaybackState.postValue(state);
        }
    };

    /**
     * Binds to running {@link RadioAppService} instance or starts one if it doesn't exist.
     */
    public void bind(@NonNull Context context) {
        mClientContext = Objects.requireNonNull(context);

        Intent bindIntent = new Intent(RadioAppService.ACTION_APP_SERVICE, null,
                context, RadioAppService.class);
        if (!context.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            throw new RuntimeException("Failed to bind to RadioAppService");
        }
    }

    /**
     * Unbinds from remote radio service.
     */
    public void unbind() {
        if (mClientContext == null) {
            throw new IllegalStateException(
                    "This is not a remote service wrapper, you can't unbind it");
        }
        callService(service -> service.removeCallback(mCallback));
        mClientContext.unbindService(mServiceConnection);
    }

    private void onServiceConnected(@NonNull IRadioAppService service) {
        mService.set(service);
        initialize(service);
        mIsConnected.postValue(true);
    }

    private void onServiceDisconnected() {
        mService.set(null);
        mIsConnected.postValue(false);
    }

    private interface ServiceOperation {
        void execute(@NonNull IRadioAppService service) throws RemoteException;
    }

    private void callService(@NonNull ServiceOperation op) {
        IRadioAppService service = mService.get();
        if (service == null) {
            throw new IllegalStateException("Service is not connected");
        }
        try {
            op.execute(service);
        } catch (RemoteException e) {
            // TODO(b/73950974): don't throw when service disconnect event tracking is implemented
            throw new RuntimeException("Remote call failed", e);
        }
    }

    /**
     * Returns a {@link LiveData} stating if the connection with RadioAppService is alive.
     *
     * Possible values are:
     *  - {@code null} (not set) if the service isn't connected yet
     *  - {@code true} if the connection is alive
     *  - {@code false} if the service is disconnected
     */
    @NonNull
    public LiveData<Boolean> isConnected() {
        return mIsConnected;
    }

    /**
     * Returns a {@link LiveData} containing playback state.
     */
    @NonNull
    public LiveData<Integer> getPlaybackState() {
        return mPlaybackState;
    }

    /**
     * Returns a {@link LiveData} containing currently tuned program info.
     */
    @NonNull
    public LiveData<ProgramInfo> getCurrentProgram() {
        return mCurrentProgram;
    }

    /**
     * Tunes to a given program.
     */
    public void tune(@NonNull ProgramSelector sel) {
        callService(service -> service.tune(sel));
    }

    /**
     * Seeks forward.
     */
    public void seekForward() {
        callService(service -> service.seekForward());
    }

    /**
     * Seeks backwards.
     */
    public void seekBackward() {
        callService(service -> service.seekBackward());
    }

    /**
     * Mutes or resumes audio.
     *
     * @param muted {@code true} to mute, {@code false} to resume audio.
     */
    public void setMuted(boolean muted) {
        callService(service -> service.setMuted(muted));
    }

    /**
     * Tune to a default channel of a given program type (band).
     *
     * Usually, this means tuning to the recently listened program of a given band.
     *
     * @param band Program type to switch to
     */
    public void switchBand(@NonNull ProgramType band) {
        callService(service -> service.switchBand(Objects.requireNonNull(band)));
    }

    /**
     * Returns a list of programs found with the tuner's background scan
     */
    public List<ProgramInfo> getProgramList() {
        IRadioAppService service = mService.get();
        if (service == null) return null;

        try {
            return service.getProgramList();
        } catch (RemoteException e) {
            return null;
        }
    }
}
