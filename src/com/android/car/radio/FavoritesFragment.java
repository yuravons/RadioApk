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

/**
 * Fragment that shows a list of all the current favorite radio stations
 */
public class FavoritesFragment extends Fragment {

    private RadioController mRadioController;
    private BrowseAdapter mBrowseAdapter;
    private RadioStorage mRadioStorage;
    private PagedListView mBrowseList;

    private final ICurrentProgramListener mCurrentProgramListener =
            new CurrentProgramListenerAdapter(this::onCurrentProgramChanged);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.browse_fragment, container, false);
        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Context context = getContext();

        mRadioStorage = RadioStorage.getInstance(context);
        mBrowseAdapter = new BrowseAdapter(this, mRadioStorage.getFavorites());

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
    }

    @Override
    public void onDestroyView() {
        mRadioController.removeCurrentProgramListener(mCurrentProgramListener);
        super.onDestroyView();
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        mBrowseAdapter.onCurrentProgramChanged(info);
    }

    private void onRadioServiceConnected() {
        mRadioController.addCurrentProgramListener(mCurrentProgramListener);
    }

    private void handlePresetItemFavoriteChanged(Program program, boolean saveAsFavorite) {
        if (saveAsFavorite) {
            mRadioStorage.storePreset(program);
        } else {
            mRadioStorage.removePreset(program.getSelector());
        }
    }

    static FavoritesFragment newInstance(RadioController radioController) {
        FavoritesFragment fragment = new FavoritesFragment();
        fragment.mRadioController = radioController;
        return fragment;
    }
}
