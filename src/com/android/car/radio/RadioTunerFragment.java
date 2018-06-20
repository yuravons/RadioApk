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

import android.annotation.NonNull;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.fragment.app.Fragment;

import com.android.car.radio.service.CurrentProgramListenerAdapter;
import com.android.car.radio.service.ICurrentProgramListener;
import com.android.car.radio.utils.ProgramSelectorUtils;

/**
 * Fragment that allows tuning to a specific frequency using a keypad
 */
public class RadioTunerFragment extends Fragment {

    private ManualTunerController mController;
    private RadioController mRadioController;
    private ImageButton mBandToggleButton;

    private final ICurrentProgramListener mCurrentProgramListener =
            new CurrentProgramListenerAdapter(this::onCurrentProgramChanged);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tuner_fragment, container, false);
        mController = new ManualTunerController(getContext(), view, RadioManager.BAND_FM);
        mController.setDoneButtonListener(this::onManualTunerDone);
        mBandToggleButton = view.findViewById(R.id.manual_tuner_band_toggle);
        mBandToggleButton.setOnClickListener(v -> mRadioController.switchBand(
                mRadioController.getCurrentRadioBand() == RadioManager.BAND_FM
                        ? RadioManager.BAND_AM
                        : RadioManager.BAND_FM));
        mRadioController.addRadioServiceConnectionListener(() ->
                mRadioController.addCurrentProgramListener(mCurrentProgramListener));
        return view;
    }

    @Override
    public void onDestroyView() {
        mRadioController.removeCurrentProgramListener(mCurrentProgramListener);
        super.onDestroyView();
    }

    private void onManualTunerDone(ProgramSelector sel) {
        if (sel != null) {
            mRadioController.tune(sel);
        }
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        int radioBand = ProgramSelectorUtils.getRadioBand(info.getSelector());
        mBandToggleButton.setImageResource(radioBand == RadioManager.BAND_FM
                ? R.drawable.ic_radio_fm
                : R.drawable.ic_radio_am);
        mController.updateCurrentRadioBand(radioBand);
    }

    static RadioTunerFragment newInstance(RadioController radioController) {
        RadioTunerFragment fragment = new RadioTunerFragment();
        fragment.mRadioController = radioController;
        return fragment;
    }
}
