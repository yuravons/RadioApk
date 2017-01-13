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

import android.content.Intent;
import android.hardware.radio.RadioManager;
import android.os.Bundle;
import android.support.car.ui.PagedListView;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ViewGroup;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.DrawerItemClickListener;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.radio.service.RadioStation;

import java.util.LinkedList;
import java.util.List;

/**
 * The main activity for the radio. This activity initializes the radio controls and listener for
 * radio changes.
 */
public class CarRadioActivity extends CarDrawerActivity implements
        RadioPresetsFragment.PresetListExitListener,
        MainRadioFragment.RadioPresetListClickListener,
        ManualTunerFragment.ManualTunerCompletionListener {
    private static final String TAG = "Em.RadioActivity";
    private static final String MANUAL_TUNER_BACKSTACK = "ManualTunerBackstack";

    private static final int[] SUPPORTED_RADIO_BANDS = new int[] {
        RadioManager.BAND_AM, RadioManager.BAND_FM };

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
    private PagedListView mDrawerList;
    private MainRadioFragment mMainFragment;
    private boolean mTunerOpened;

    private FragmentWithFade mCurrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRadioController = new RadioController(this);
        mDrawerList = getDrawerListView();
        populateDrawerContents();

        mMainFragment = MainRadioFragment.newInstance(mRadioController);
        mMainFragment.setPresetListClickListener(this);
        setContentFragment(mMainFragment);
        mCurrentFragment = mMainFragment;
    }

    private void populateDrawerContents() {
        // The order of items in drawer is hardcoded. The OnItemClickListener depends on it.
        List<String> drawerOptions = new LinkedList<>();
        for (int band : SUPPORTED_RADIO_BANDS) {
            String bandText = RadioChannelFormatter.formatRadioBand(this, band);
            drawerOptions.add(bandText);
        }
        drawerOptions.add(getString(R.string.manual_tuner_drawer_entry));

        mDrawerList.setAdapter(new RadioDrawerAdapter(drawerOptions, (position) -> {
            closeDrawer();
            if (position < SUPPORTED_RADIO_BANDS.length) {
                mRadioController.openRadioBand(SUPPORTED_RADIO_BANDS[position]);
            } else if (position == SUPPORTED_RADIO_BANDS.length) {
                startManualTuner();
            } else {
                Log.w(TAG, "Unexpected position: " + position);
            }
        }));
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

    public void startManualTuner() {
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
                .add(getContentContainerId(), fragment)
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
        sendBroadcast(broadcast);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStop");
        }

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, false);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDestroy");
        }

        mRadioController.shutdown();
    }

    private void setContentFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(getContentContainerId(), fragment)
                .commit();
    }

    private static class RadioDrawerAdapter extends RecyclerView.Adapter<DrawerItemViewHolder>
            implements PagedListView.ItemCap {
        private final List<String> mDrawerOptions;
        private final DrawerItemClickListener mDrawerItemClickListener;
        private int mMaxItems = -1;

        RadioDrawerAdapter(List<String> drawerOptions, DrawerItemClickListener listener) {
            mDrawerOptions = drawerOptions;
            mDrawerItemClickListener = listener;
        }

        @Override
        public DrawerItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return DrawerItemViewHolder.create(parent, mDrawerItemClickListener);
        }

        @Override
        public void onBindViewHolder(DrawerItemViewHolder holder, int position) {
            holder.getTitle().setText(mDrawerOptions.get(position));
        }

        @Override
        public int getItemCount() {
            return mMaxItems >= 0
                    ? Math.min(mMaxItems, mDrawerOptions.size()) : mDrawerOptions.size();
        }

        @Override
        public void setMaxItems(int maxItems) {
            mMaxItems = maxItems;
        }
    }

}
