/*
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

package com.android.car.radio;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;

import com.android.car.radio.service.CurrentProgramListenerAdapter;
import com.android.car.radio.service.ICurrentProgramListener;
import com.android.car.radio.widget.BandToggleButton;

import com.google.android.material.tabs.TabLayout;

/**
 * The main activity for the radio app.
 */
public class RadioActivity extends FragmentActivity {
    private static final String TAG = "BcRadioApp.RadioActivity";

    /**
     * Intent action for notifying that the radio state has changed.
     */
    private static final String ACTION_RADIO_APP_STATE_CHANGE =
            "android.intent.action.RADIO_APP_STATE_CHANGE";

    /**
     * Boolean Intent extra indicating if the radio is the currently in the foreground.
     */
    private static final String EXTRA_RADIO_APP_FOREGROUND =
            "android.intent.action.RADIO_APP_STATE";

    private RadioController mRadioController;
    private View mRootView;
    private BandToggleButton mBandToggleButton;

    private ICurrentProgramListener mCurrentProgramListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.radio_activity);
        mRootView = findViewById(R.id.main_radio_display);
        mBandToggleButton = findViewById(R.id.band_toggle_button);

        mRadioController = new RadioController(this);
        mBandToggleButton.setCallback(mRadioController::switchBand);

        mCurrentProgramListener = new CurrentProgramListenerAdapter(
                mBandToggleButton::onCurrentProgramChanged);
        mRadioController.addRadioServiceConnectionListener(() ->
                mRadioController.addCurrentProgramListener(mCurrentProgramListener));

        RadioPagerAdapter adapter =
                new RadioPagerAdapter(this, getSupportFragmentManager(), mRadioController);
        ViewPager viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(adapter);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.radio_tabs);
        tabLayout.setupWithViewPager(viewPager);

        for (int i = 0; i < adapter.getCount(); i++) {
            LinearLayout tab = (LinearLayout) getLayoutInflater().inflate(R.layout.tab_item, null);
            TextView tabLabel = tab.findViewById(R.id.tab_label);
            ImageView tabIcon = tab.findViewById(R.id.tab_icon);

            tabLabel.setText(adapter.getPageTitle(i));
            tabLabel.setTextColor(getColor(R.color.car_radio_control_button_disabled));
            tabIcon.setImageResource(adapter.getImageResource(i));
            tabIcon.setColorFilter(getColor(R.color.car_radio_control_button_disabled));
            if (i == 0) {
                tabLabel.setTextColor(getColor(R.color.car_radio_control_button));
                tabIcon.setColorFilter(getColor(R.color.car_radio_control_button));
            }
            tabLayout.getTabAt(i).setCustomView(tab);
        }
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                View tabView = tab.getCustomView();
                TextView tabLabel = tabView.findViewById(R.id.tab_label);
                ImageView tabIcon = tabView.findViewById(R.id.tab_icon);

                tabLabel.setTextColor(getColor(R.color.car_radio_control_button));
                tabIcon.setColorFilter(getColor(R.color.car_radio_control_button));
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View tabView = tab.getCustomView();
                TextView tabLabel = tabView.findViewById(R.id.tab_label);
                ImageView tabIcon = tabView.findViewById(R.id.tab_icon);
                tabLabel.setTextColor(getColor(R.color.car_radio_control_button_disabled));
                tabIcon.setColorFilter(getColor(R.color.car_radio_control_button_disabled));
            }
        });
        tabLayout.getTabAt(0).select();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStart");
        }
        mRadioController.initialize(mRootView);
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
        mRadioController.removeCurrentProgramListener(mCurrentProgramListener);

        super.onDestroy();

        mRadioController.shutdown();
    }
}
