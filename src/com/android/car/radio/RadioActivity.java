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
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;

import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.util.Log;
import com.android.car.radio.widget.BandSelector;

import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * The main activity for the radio app.
 */
public class RadioActivity extends FragmentActivity {
    private static final String TAG = "BcRadioApp.activity";

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
    private BandSelector mBandSelector;

    private Object mLock = new Object();
    private TabLayout mTabLayout;
    private RadioPagerAdapter mRadioPagerAdapter;
    private int mCurrentTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Radio app main activity created");

        setContentView(R.layout.radio_activity);
        mBandSelector = findViewById(R.id.band_toggle_button);

        mRadioController = new RadioController(this);
        mBandSelector.setCallback(mRadioController::switchBand);
        mRadioController.getCurrentProgram().observe(this, info ->
                mBandSelector.setType(ProgramType.fromSelector(info.getSelector())));

        mRadioPagerAdapter =
                new RadioPagerAdapter(this, getSupportFragmentManager(), mRadioController);
        ViewPager viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(mRadioPagerAdapter);
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mTabLayout.setupWithViewPager(viewPager);

        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                synchronized (mLock) {
                    mCurrentTab = tab.getPosition();
                    View tabView = tab.getCustomView();

                    // When the number of tabs is changed in mRadioPagerAdapter, the tab's custom
                    // views are reset, and we immediately get this callback with null custom view
                    if (tabView == null) return;

                    TextView tabLabel = tabView.findViewById(R.id.tab_label);
                    ImageView tabIcon = tabView.findViewById(R.id.tab_icon);

                    tabLabel.setTextColor(getColor(R.color.control_button));
                    tabIcon.setColorFilter(getColor(R.color.control_button));
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                synchronized (mLock) {
                    View tabView = tab.getCustomView();
                    if (tabView == null) return;

                    TextView tabLabel = tabView.findViewById(R.id.tab_label);
                    ImageView tabIcon = tabView.findViewById(R.id.tab_icon);
                    tabLabel.setTextColor(
                            getColor(R.color.control_button_disabled));
                    tabIcon.setColorFilter(
                            getColor(R.color.control_button_disabled));
                }
            }
        });

        mTabLayout.getTabAt(0).select();
        refreshCustomTabViews();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mRadioController.start();

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, true);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, false);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mRadioController.shutdown();

        Log.d(TAG, "Radio app main activity destroyed");
    }

    /**
     * Set whether background scanning is supported, to know whether to show the browse tab or not
     */
    public void setProgramListSupported(boolean supported) {
        if (supported && mRadioPagerAdapter.addBrowseTab()) {
            synchronized (mLock) {
                // Need to apply custom view to new tabs
                refreshCustomTabViews();
            }
        }
    }

    /**
     * Sets supported program types.
     */
    public void setSupportedProgramTypes(@NonNull List<ProgramType> supported) {
        mBandSelector.setSupportedProgramTypes(supported);
    }

    private View buildCustomTab(CharSequence text, int image, boolean isSelected) {
        LinearLayout tab = (LinearLayout) getLayoutInflater().inflate(R.layout.tab_item, null);
        TextView tabLabel = tab.findViewById(R.id.tab_label);
        ImageView tabIcon = tab.findViewById(R.id.tab_icon);

        tabLabel.setText(text);
        tabLabel.setTextColor(getColor(R.color.control_button_disabled));
        tabIcon.setImageResource(image);
        tabIcon.setColorFilter(getColor(R.color.control_button_disabled));
        if (isSelected) {
            tabLabel.setTextColor(getColor(R.color.control_button));
            tabIcon.setColorFilter(getColor(R.color.control_button));
        }

        return tab;
    }

    private void refreshCustomTabViews() {
        for (int i = 0; i < mRadioPagerAdapter.getCount(); i++) {
            mTabLayout.getTabAt(i)
                    .setCustomView(buildCustomTab(mRadioPagerAdapter.getPageTitle(i),
                            mRadioPagerAdapter.getImageResource(i), i == mCurrentTab));
        }
    }

}
