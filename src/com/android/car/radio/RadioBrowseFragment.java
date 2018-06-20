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
import android.content.Context;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.car.widget.DayNightStyle;
import androidx.car.widget.PagedListView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.broadcastradio.support.Program;
import com.android.car.radio.service.CurrentProgramListenerAdapter;
import com.android.car.radio.service.ICurrentProgramListener;
import com.android.car.radio.storage.RadioStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that shows all browseable radio stations from background scan
 */
public class RadioBrowseFragment extends Fragment {

    private RadioController mRadioController;
    private BrowseAdapter mBrowseAdapter = new BrowseAdapter();
    private RadioStorage mRadioStorage;
    private View mRootView;
    private PagedListView mBrowseList;

    private final ICurrentProgramListener mCurrentProgramListener =
            new CurrentProgramListenerAdapter(this::onCurrentProgramChanged);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.browse_fragment, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Context context = getContext();

        mRadioController.addRadioServiceConnectionListener(this::onRadioServiceConnected);
        mBrowseAdapter.setOnItemClickListener(mRadioController::tune);
        mBrowseAdapter.setOnItemFavoriteListener(this::handlePresetItemFavoriteChanged);

        mBrowseList = view.findViewById(R.id.browse_list);
        mBrowseList.setDayNightStyle(DayNightStyle.ALWAYS_LIGHT);
        mBrowseList.setAdapter(mBrowseAdapter);
        RecyclerView recyclerView = mBrowseList.getRecyclerView();
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_4));

        mRadioStorage = RadioStorage.getInstance(context);
        mRadioStorage.addPresetsChangeListener(this::onPresetsRefreshed);

        updateProgramList();
    }

    @Override
    public void onDestroyView() {
        mRadioController.removeCurrentProgramListener(mCurrentProgramListener);
        super.onDestroyView();
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        mBrowseAdapter.setActiveProgram(Program.fromProgramInfo(info));
    }

    private void onPresetsRefreshed() {
        mBrowseAdapter.updateFavorites(mRadioStorage.getPresets());
    }

    private void onRadioServiceConnected() {
        mRadioController.addCurrentProgramListener(mCurrentProgramListener);
        updateProgramList();
    }

    private void handlePresetItemFavoriteChanged(Program program, boolean saveAsFavorite) {
        if (saveAsFavorite) {
            mRadioStorage.storePreset(program);
        } else {
            mRadioStorage.removePreset(program.getSelector());
        }
    }

    static RadioBrowseFragment newInstance(RadioController radioController) {
        RadioBrowseFragment fragment = new RadioBrowseFragment();
        fragment.mRadioController = radioController;
        return fragment;
    }

    private void updateProgramList() {
        List<ProgramInfo> list = mRadioController.getProgramList();
        if (list != null && list.size() > 0) {
            List<Program> browseList = new ArrayList<>(list.size());
            for (ProgramInfo programInfo : list) {
                browseList.add(Program.fromProgramInfo(programInfo));
            }
            mBrowseAdapter.setBrowseList(browseList);
        }
        mBrowseAdapter.updateFavorites(mRadioStorage.getPresets());
    }
}
