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

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

/**
 * Adapter containing all fragments used in the view pager
 */
public class RadioFragmentPagerAdapter extends FragmentPagerAdapter {

    private static final int PAGE_COUNT = 3;
    private static final int[] TAB_LABELS =
            new int[] {R.string.home_tab, R.string.favorites_tab, R.string.tune_tab};
    private static final int[] TAB_ICONS = new int[] {R.drawable.ic_home, R.drawable.ic_star_filled,
            R.drawable.ic_input_antenna};
    private RadioController mRadioController;
    private Context mContext;

    public RadioFragmentPagerAdapter(Context context, FragmentManager fragmentManager,
            RadioController controller) {
        super(fragmentManager);
        mRadioController = controller;
        mContext = context;
    }

    @Override
    public Fragment getItem(int i) {
        switch(i) {
            case 0:
                return RadioBrowseFragment.newInstance(mRadioController);
            case 1:
                return RadioFavoritesFragment.newInstance(mRadioController);
            case 2:
                return RadioTunerFragment.newInstance(mRadioController);
        }
        return null;
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mContext.getResources().getString(TAB_LABELS[position]);
    }

    /**
     * Returns resource id of the icon to use for the tab at position
     */
    public int getImageResource(int position) {
        return TAB_ICONS[position];
    }
}
