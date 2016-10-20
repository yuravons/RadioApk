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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.car.Car;
import android.support.car.app.menu.CarDrawerActivity;
import android.util.Log;
import com.android.car.radio.service.RadioStation;

/**
 * The main activity for the radio. This activity initializes the radio controls and listener for
 * radio changes.
 */
public class CarRadioActivity extends CarDrawerActivity implements
        RadioPresetsFragment.PresetListExitListener,
        MainRadioFragment.RadioPresetListClickListener,
        CarRadioMenu.ManualTunerStarter,
        ManualTunerFragment.ManualTunerCompletionListener {
    private static final String TAG = "Em.RadioActivity";
    private static final String MANUAL_TUNER_BACKSTACK = "ManualTunerBackstack";

    /**
     * Intent action for notifying that the radio state has changed.
     */
    private static final String ACTION_RADIO_APP_STATE_CHANGE
            = "android.intent.action.RADIO_APP_STATE_CHANGE";

    /**
     * Boolean Intent extra indicating if the radio is the currently in the foreground.
     */
    private static final String EXTRA_RADIO_APP_FOREGROUND
            = "android.intent.action.RADIO_APP_STATE";

    private RadioController mRadioController;
    private MainRadioFragment mMainFragment;
    private boolean mTunerOpened;

    private FragmentWithFade mCurrentFragment;

    public CarRadioActivity(Proxy proxy, Context context, Car car) {
        super(proxy, context, car);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Makes the drawer icon and application text white.
        setLightMode();

        setTitle(getContext().getString(R.string.app_name));

        mRadioController = new RadioController((Activity) getContext());

        mMainFragment = MainRadioFragment.newInstance(mRadioController);
        mMainFragment.setPresetListClickListener(this);

        setContentFragment(mMainFragment);
        mCurrentFragment = mMainFragment;

        setCarMenuCallbacks(new CarRadioMenu(getContext(), mRadioController,
                this /* manualTunerStarter */));
    }

    @Override
    public void onPresetListClicked() {
        mMainFragment.setPresetListClickListener(null);

        RadioPresetsFragment fragment =
                RadioPresetsFragment.newInstance(mRadioController);
        fragment.setPresetListExitListener(this);

        setContentFragment(fragment);

        mCurrentFragment = fragment;
    }

    @Override
    public void OnPresetListExit() {
        mMainFragment.setPresetListClickListener(this);
        setContentFragment(mMainFragment);
        mCurrentFragment = mMainFragment;
    }

    @Override
    public void startManualTuner() {
        closeDrawer();

        if (mTunerOpened) {
            return;
        }

        mCurrentFragment.fadeOutContent();

        ManualTunerFragment fragment =
                ManualTunerFragment.newInstance(mRadioController.getCurrentRadioBand());
        fragment.setManualTunerCompletionListener(this);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_up, R.anim.slide_down,
                        R.anim.slide_up, R.anim.slide_down)
                .add(getFragmentContainerId(), fragment)
                .addToBackStack(MANUAL_TUNER_BACKSTACK)
                .commitAllowingStateLoss();

        mTunerOpened = true;
    }

    @Override
    public void onStationSelected(RadioStation station) {
        // A station can only be selected if the manual tuner fragment has been shown; so, remove
        // that here.
        getSupportFragmentManager().popBackStack();
        mCurrentFragment.fadeInContent();

        if (station != null) {
            mRadioController.tuneToRadioChannel(station);
        }

        mTunerOpened = false;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStart");
        }

        mRadioController.start();

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, true);
        getContext().sendBroadcast(broadcast);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStop");
        }

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, false);
        getContext().sendBroadcast(broadcast);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDestroy");
        }

        mRadioController.shutdown();
    }
}
