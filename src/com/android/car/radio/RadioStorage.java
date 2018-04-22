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

import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.os.AsyncTask;
import android.util.Log;

import com.android.car.radio.media.Program;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class that manages persistent storage of various radio options.
 */
public class RadioStorage {
    private static final String TAG = "Em.RadioStorage";
    private static final String PREF_NAME = "com.android.car.radio.RadioStorage";

    // Keys used for storage in the SharedPreferences.
    private static final String PREF_KEY_RADIO_BAND = "radio_band";
    private static final String PREF_KEY_RADIO_CHANNEL_AM = "radio_channel_am";
    private static final String PREF_KEY_RADIO_CHANNEL_FM = "radio_channel_fm";

    public static final int INVALID_RADIO_CHANNEL = -1;
    public static final int INVALID_RADIO_BAND = -1;

    private static SharedPreferences sSharedPref;
    private static RadioStorage sInstance;
    private static RadioDatabase sRadioDatabase;

    /**
     * Listener that will be called when something in the radio storage changes.
     */
    public interface PresetsChangeListener {
        /**
         * Called when {@link #refreshPresets()} has completed.
         */
        void onPresetsRefreshed();
    }

    private Set<PresetsChangeListener> mPresetListeners = new HashSet<>();

    // TODO(b/73950974): use Set, not List
    @NonNull private List<Program> mPresets = new ArrayList<>();

    private RadioStorage(Context context) {
        if (sSharedPref == null) {
            sSharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        if (sRadioDatabase == null) {
            sRadioDatabase = new RadioDatabase(context);
        }
    }

    public static RadioStorage getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RadioStorage(context.getApplicationContext());

            // When the RadioStorage is first created, load the list of radio presets.
            sInstance.refreshPresets();
        }

        return sInstance;
    }

    /**
     * Registers the given {@link PresetsChangeListener} to be notified when any radio preset state
     * has changed.
     */
    public void addPresetsChangeListener(PresetsChangeListener listener) {
        mPresetListeners.add(listener);
    }

    /**
     * Unregisters the given {@link PresetsChangeListener}.
     */
    public void removePresetsChangeListener(PresetsChangeListener listener) {
        mPresetListeners.remove(listener);
    }

    /**
     * Requests a load of all currently stored presets. This operation runs asynchronously. When
     * the presets have been loaded, any registered {@link PresetsChangeListener}s are
     * notified via the {@link PresetsChangeListener#onPresetsRefreshed()} method.
     */
    private void refreshPresets() {
        new GetAllPresetsAsyncTask().execute();
    }

    /**
     * Returns all currently loaded presets. If there are no stored presets, this method will
     * return an empty {@link List}.
     *
     * <p>Register as a {@link PresetsChangeListener} to be notified of any changes in the
     * preset list.
     */
    public @NonNull List<Program> getPresets() {
        return Objects.requireNonNull(mPresets);
    }

    /**
     * Returns {@code true} if the given {@link ProgramSelector} is a user saved favorite.
     */
    public boolean isPreset(@NonNull ProgramSelector selector) {
        return mPresets.contains(new Program(selector, ""));
    }

    /**
     * Stores that given {@link Program} as a preset. This operation will override any
     * previously stored preset that matches the given preset.
     *
     * <p>Upon a successful store, the presets list will be refreshed via a call to
     * {@link #refreshPresets()}.
     *
     * @see #refreshPresets()
     */
    public void storePreset(@NonNull Program preset) {
        new StorePresetAsyncTask().execute(Objects.requireNonNull(preset));
    }

    /**
     * Removes the given {@link Program} as a preset.
     *
     * <p>Upon a successful removal, the presets list will be refreshed via a call to
     * {@link #refreshPresets()}.
     *
     * @see #refreshPresets()
     */
    public void removePreset(@NonNull ProgramSelector preset) {
        new RemovePresetAsyncTask().execute(Objects.requireNonNull(preset));
    }

    /**
     * Returns the stored radio band that was set in {@link #storeRadioBand(int)}. If a radio band
     * has not previously been stored, then {@link RadioManager#BAND_FM} is returned.
     *
     * @return One of {@link RadioManager#BAND_FM}, {@link RadioManager#BAND_AM},
     * {@link RadioManager#BAND_FM_HD} or {@link RadioManager#BAND_AM_HD}.
     */
    public int getStoredRadioBand() {
        // No need to verify that the returned value is one of AM_BAND or FM_BAND because this is
        // done in storeRadioBand(int).
        return sSharedPref.getInt(PREF_KEY_RADIO_BAND, RadioManager.BAND_FM);
    }

    /**
     * Stores a radio band for later retrieval via {@link #getStoredRadioBand()}.
     */
    public void storeRadioBand(int radioBand) {
        // Ensure that an incorrect radio band is not stored. Currently only FM and AM supported.
        if (radioBand != RadioManager.BAND_FM && radioBand != RadioManager.BAND_AM) {
            return;
        }

        sSharedPref.edit().putInt(PREF_KEY_RADIO_BAND, radioBand).apply();
    }

    /**
     * Returns the stored radio channel that was set in {@link #storeRadioChannel(int, int)}. If a
     * radio channel for the given band has not been previously stored, then
     * {@link #INVALID_RADIO_CHANNEL} is returned.
     *
     * @param band One of the BAND_* values from {@link RadioManager}. For example,
     *             {@link RadioManager#BAND_AM}.
     */
    public int getStoredRadioChannel(int band) {
        switch (band) {
            case RadioManager.BAND_AM:
                return sSharedPref.getInt(PREF_KEY_RADIO_CHANNEL_AM, INVALID_RADIO_CHANNEL);

            case RadioManager.BAND_FM:
                return sSharedPref.getInt(PREF_KEY_RADIO_CHANNEL_FM, INVALID_RADIO_CHANNEL);

            default:
                return INVALID_RADIO_CHANNEL;
        }
    }

    /**
     * Stores a radio channel (i.e. the radio frequency) for a particular band so it can be later
     * retrieved via {@link #getStoredRadioChannel(int band)}.
     */
    public void storeRadioChannel(int band, int channel) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("storeRadioChannel(); band: %s, channel %s", band, channel));
        }

        if (channel <= 0) {
            return;
        }

        // TODO(b/73950974): don't store if it's already the same
        switch (band) {
            case RadioManager.BAND_AM:
                sSharedPref.edit().putInt(PREF_KEY_RADIO_CHANNEL_AM, channel).apply();
                break;

            case RadioManager.BAND_FM:
                sSharedPref.edit().putInt(PREF_KEY_RADIO_CHANNEL_FM, channel).apply();
                break;

            default:
                Log.w(TAG, "Attempting to store channel for invalid band: " + band);
        }
    }

    /**
     * Calls {@link PresetsChangeListener#onPresetsRefreshed()} for all registered
     * {@link PresetsChangeListener}s.
     */
    private void notifyPresetsListeners() {
        for (PresetsChangeListener listener : mPresetListeners) {
            listener.onPresetsRefreshed();
        }
    }

    private void loadPresetsInternal() {
        mPresets = sRadioDatabase.getAllPresets().stream().map(RadioStation::toProgram).collect(Collectors.toList());
    }

    /**
     * {@link AsyncTask} that will fetch all stored radio presets.
     */
    private class GetAllPresetsAsyncTask extends AsyncTask<Void, Void, Void> {
        private static final String TAG = "Em.GetAllPresetsAT";

        @Override
        protected Void doInBackground(Void... voids) {
            loadPresetsInternal();

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Loaded presets: " + mPresets);
            }

            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            notifyPresetsListeners();
        }
    }

    /**
     * {@link AsyncTask} that will store a single {@link Program} that is passed to its
     * {@link AsyncTask#execute(Object[])}.
     */
    private class StorePresetAsyncTask extends AsyncTask<Program, Void, Boolean> {
        private static final String TAG = "Em.StorePresetAT";

        @Override
        protected Boolean doInBackground(Program... programs) {
            boolean result = sRadioDatabase.insertPreset(new RadioStation(programs[0]));

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Store preset success: " + result);
            }

            if (result) {
                loadPresetsInternal();
            }

            return result;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                notifyPresetsListeners();
            }
        }
    }

    /**
     * {@link AsyncTask} that will remove a single {@link Program} that is passed to its
     * {@link AsyncTask#execute(Object[])}.
     */
    private class RemovePresetAsyncTask extends AsyncTask<ProgramSelector, Void, Boolean> {
        private static final String TAG = "Em.RemovePresetAT";

        @Override
        protected Boolean doInBackground(ProgramSelector... selectors) {
            boolean result = sRadioDatabase.deletePreset(new RadioStation(selectors[0], null));

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Remove preset success: " + result);
            }

            if (result) {
                loadPresetsInternal();
            }

            return result;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                notifyPresetsListeners();
            }
        }
    }
}
