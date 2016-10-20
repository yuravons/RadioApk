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

import android.content.Context;
import android.hardware.radio.RadioManager;
import android.os.Bundle;
import android.support.car.app.menu.CarMenu;
import android.support.car.app.menu.CarMenuCallbacks;
import android.support.car.app.menu.RootMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link CarMenuCallbacks} that generates the radio drawer. The radio drawer displays a list of
 * the current available radio bands (e.g. AM).
 */
public class CarRadioMenu extends CarMenuCallbacks {
    private static final String RADIO_MENU_ROOT = "root";
    private static final String MANUAL_TUNER_ID = "tuner";

    private static final int[] SUPPORTED_RADIO_BANDS = new int[] {
            RadioManager.BAND_AM, RadioManager.BAND_FM };

    private final Context mContext;
    private final RadioController mRadioController;
    private final ManualTunerStarter mManualTunerStarter;

    /**
     * Interface for a class that is able to start the manual tuner activity.
     */
    public interface ManualTunerStarter {
        /**
         * Start the activity that will allow the user to manually input a radio station.
         */
        void startManualTuner();
    }

    /**
     * The items that should be represented in the drawer.
     */
    private final List<CarMenu.Item> mMenuItems = new ArrayList<>(SUPPORTED_RADIO_BANDS.length);

    public CarRadioMenu(Context context, RadioController radioController,
            ManualTunerStarter manualTunerStarter) {
        mContext = context;
        mRadioController = radioController;
        mManualTunerStarter = manualTunerStarter;

        for (int i = 0; i < SUPPORTED_RADIO_BANDS.length; i++) {
            String bandText = RadioChannelFormatter.formatRadioBand(mContext,
                    SUPPORTED_RADIO_BANDS[i]);

            // The id for each item in the menu is its position in the presets area.
            mMenuItems.add(new CarMenu.Builder(Integer.toString(i))
                    .setTitle(bandText)
                    .build());
        }

        mMenuItems.add(new CarMenu.Builder(MANUAL_TUNER_ID)
                .setTitle(mContext.getString(R.string.manual_tuner_drawer_entry))
                .build());
    }

    @Override
    public RootMenu onGetRoot(Bundle hints) {
        return new RootMenu(RADIO_MENU_ROOT);
    }

    @Override
    public void onLoadChildren(String parentId, CarMenu result) {
        if (parentId.equals(RADIO_MENU_ROOT)) {
            result.sendResult(mMenuItems);
        } else {
            result.sendResult(new ArrayList<>());
        }
    }

    @Override
    public void onItemClicked(String id) {
        if (MANUAL_TUNER_ID.equals(id)) {
            mManualTunerStarter.startManualTuner();
            return;
        }

        // The menu item that was clicked was one of the radio bands, so parse the id to find the
        // corresponding band in the SUPPORTED_RADIO_BANDS array.
        Integer index = Integer.parseInt(id);

        if (index == null || mRadioController == null) {
            return;
        }

        mRadioController.openRadioBand(SUPPORTED_RADIO_BANDS[index]);
    }
}
