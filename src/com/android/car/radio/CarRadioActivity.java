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
import android.annotation.Nullable;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.radio.RadioManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.car.radio.service.RadioStation;

import java.util.LinkedList;
import java.util.List;

/**
 * The main activity for the radio. This activity initializes the radio controls and listener for
 * radio changes.
 */
public class CarRadioActivity extends AppCompatActivity implements
        RadioPresetsFragment.PresetListExitListener,
        MainRadioFragment.RadioPresetListClickListener,
        CarRadioMenu.ManualTunerStarter,
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

    private static final float COLOR_SWITCH_SLIDE_OFFSET = 0.25f;

    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private RadioController mRadioController;
    private ListView mDrawerList;
    private DrawerLayout mDrawerLayout;
    private Toolbar mToolbar;
    private ActionBarDrawerToggle mDrawerToggle;
    private MainRadioFragment mMainFragment;
    private boolean mTunerOpened;

    private FragmentWithFade mCurrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        setContentView(R.layout.radio_activity);
        mRadioController = new RadioController(this);
        mDrawerList = (ListView)findViewById(R.id.left_drawer);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);

        setupDrawerToggling();
        populateDrawerContents();

        mMainFragment = MainRadioFragment.newInstance(mRadioController);
        mMainFragment.setPresetListClickListener(this);
        setContentFragment(mMainFragment);
        mCurrentFragment = mMainFragment;
    }

    // Consider moving this to support-lib.
    private void setupDrawerToggling() {
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                // The string id's below are for accessibility. However
                // since they won't be used in cars, we just pass app_name.
                R.string.app_name,
                R.string.app_name
        );
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                setTitleAndArrowColor(slideOffset >= COLOR_SWITCH_SLIDE_OFFSET);
            }
            @Override
            public void onDrawerOpened(View drawerView) {}
            @Override
            public void onDrawerClosed(View drawerView) {}
            @Override
            public void onDrawerStateChanged(int newState) {}
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void setTitleAndArrowColor(boolean drawerOpen) {
        // When drawer open, use car_title, which resolves to appropriate color depending on
        // day-night mode. When drawer is closed, we always use light color.
        int titleColorResId =  drawerOpen ?
                R.color.car_title : R.color.car_title_light;
        int titleColor = getColor(titleColorResId);
        mToolbar.setTitleTextColor(titleColor);
        mDrawerToggle.getDrawerArrowDrawable().setColor(titleColor);
    }

    private void populateDrawerContents() {
        // The order of items in drawer is hardcoded. The OnItemClickListener depends on it.
        List<String> drawerOptions = new LinkedList<>();
        for (int band : SUPPORTED_RADIO_BANDS) {
            String bandText = RadioChannelFormatter.formatRadioBand(this, band);
            drawerOptions.add(bandText);
        }
        drawerOptions.add(getString(R.string.manual_tuner_drawer_entry));

        ListAdapter drawerAdapter =
                new ArrayAdapter<String>(this, R.layout.car_list_item_1, R.id.text, drawerOptions) {
                    @Override
                    public View getView(int position, @Nullable View convertView,
                            @NonNull ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        // We need this hack since car_list_item1 produces focusable views and that
                        // prevents the onItemClickListener from working.
                        view.setFocusable(false);
                        return view;
                    }
                };
        mDrawerList.setAdapter(drawerAdapter);
        mDrawerList.setOnItemClickListener((parent, view, position, id) -> {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
            if (position < SUPPORTED_RADIO_BANDS.length) {
                mRadioController.openRadioBand(SUPPORTED_RADIO_BANDS[position]);
            } else if (position == SUPPORTED_RADIO_BANDS.length) {
                startManualTuner();
            } else {
                Log.w(TAG, "Unexpected position: " + position);
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
        // NOTE: isDrawerOpen must be passed the second child of the DrawerLayout.
        setTitleAndArrowColor(mDrawerLayout.isDrawerOpen(mDrawerList));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
                .add(R.id.content_frame, fragment)
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
                .replace(R.id.content_frame, fragment)
                .commit();
    }
}
