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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.car.widget.PagedListView;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.broadcastradio.support.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Adapter that will display a list of radio stations that represent the user's presets.
 */
public class BrowseAdapter extends RecyclerView.Adapter<ProgramViewHolder>
        implements PagedListView.ItemCap {
    private static final String TAG = "BcRadioApp.BrowseAdapter";

    // Only one type of view in this adapter.
    private static final int PRESETS_VIEW_TYPE = 0;

    private Program mActiveProgram;
    private boolean mHasFavorites;

    private @NonNull List<FavoritableProgram> mPrograms = new ArrayList<>();
    private OnItemClickListener mItemClickListener;
    private OnItemFavoriteListener mItemFavoriteListener;

    /**
     * Interface for a listener that will be notified when an item in the program list has been
     * clicked.
     */
    public interface OnItemClickListener {
        /**
         * Method called when an item in the list has been clicked.
         *
         * @param selector The {@link ProgramSelector} corresponding to the clicked preset.
         */
        void onItemClicked(ProgramSelector selector);
    }

    /**
     * Interface for a listener that will be notified when a favorite in the list has been
     * toggled.
     */
    public interface OnItemFavoriteListener {

        /**
         * Method called when an item's favorite status has been toggled
         *
         * @param program The {@link Program} corresponding to the clicked item.
         * @param saveAsFavorite Whether the program should be saved or removed as a favorite.
         */
        void onItemFavoriteChanged(Program program, boolean saveAsFavorite);
    }

    /**
     * Set a listener to be notified whenever a program card is pressed.
     */
    public void setOnItemClickListener(@NonNull OnItemClickListener listener) {
        mItemClickListener = Objects.requireNonNull(listener);
    }

    /**
     * Set a listener to be notified whenever a program favorite is changed.
     */
    public void setOnItemFavoriteListener(@NonNull OnItemFavoriteListener listener) {
        mItemFavoriteListener = Objects.requireNonNull(listener);
    }

    /**
     * Sets the given list as the list of programs to display.
     */
    public void setBrowseList(List<Program> programs) {
        mPrograms = new ArrayList<>();
        for (Program p : programs) {
            mPrograms.add(new FavoritableProgram(p, true));
        }
        if (mPrograms.size() == 0 && mActiveProgram != null) {
            mPrograms.add(new FavoritableProgram(mActiveProgram, false));
        }
        notifyDataSetChanged();
    }

    /**
     * Updates the stations that are favorites, while keeping unfavorited stations in the list
     */
    public void updateFavorites(List<Program> favorites) {
        List<Program> newPrograms = new ArrayList<>(favorites);
        // remove unfavorited programs
        for (int i = 0; i < mPrograms.size(); i++) {
            Program program = mPrograms.get(i).mProgram;
            mPrograms.get(i).mIsFavorite = favorites.contains(program);
            if (newPrograms.contains(program)) {
                newPrograms.remove(program);
            }
        }
        // add favorited programs
        for (Program p : newPrograms) {
            mPrograms.add(new FavoritableProgram(p, true));
        }

        if (favorites.size() == 0) {
            mHasFavorites = false;
            mPrograms.clear();
            mPrograms.add(new FavoritableProgram(mActiveProgram, false));
        } else {
            mHasFavorites = true;
        }
        notifyDataSetChanged();
    }

    /**
     * Indicates which radio station is the active one inside the list of programs that are set on
     * this adapter. This will cause that station to be highlighted in the list. If the station
     * passed to this method does not match any of the programs, then none will be highlighted.
     */
    public void setActiveProgram(Program program) {
        mActiveProgram = program;
        if (!mHasFavorites) {
            mPrograms.clear();
            mPrograms.add(new FavoritableProgram(mActiveProgram, false));
        }
        notifyDataSetChanged();
    }

    @Override
    public ProgramViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.radio_browse_item, parent, false);

        return new ProgramViewHolder(
                view, this::handlePresetClicked, this::handlePresetFavoriteChanged);
    }

    @Override
    public void onBindViewHolder(ProgramViewHolder holder, int position) {
        Program station = mPrograms.get(position).mProgram;
        boolean isActiveStation = false;
        if (mActiveProgram != null) {
            isActiveStation = station.getSelector().equals(mActiveProgram.getSelector());
        }
        holder.bindPreset(station, isActiveStation, getItemCount(),
                mPrograms.get(position).mIsFavorite);
    }

    @Override
    public int getItemViewType(int position) {
        return PRESETS_VIEW_TYPE;
    }

    @Override
    public int getItemCount() {
        return mPrograms.size();
    }

    @Override
    public void setMaxItems(int max) {
        // No-op. A PagedListView needs the ItemCap interface to be implemented. However, the
        // list of presets should not be limited.
    }

    private void handlePresetClicked(int position) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, String.format("onPresetClicked(); item count: %d; position: %d",
                    getItemCount(), position));
        }
        if (mItemClickListener != null && getItemCount() > position) {
            if (getItemCount() == 0) {
                mItemClickListener.onItemClicked(mActiveProgram.getSelector());
                return;
            }
            mItemClickListener.onItemClicked(mPrograms.get(position).mProgram.getSelector());
        }
    }

    private void handlePresetFavoriteChanged(int position, boolean saveAsFavorite) {
        if (mItemFavoriteListener != null && getItemCount() > position) {
            if (getItemCount() == 0) {
                mItemFavoriteListener.onItemFavoriteChanged(mActiveProgram, saveAsFavorite);
                return;
            }
            mItemFavoriteListener.onItemFavoriteChanged(
                    mPrograms.get(position).mProgram, saveAsFavorite);
        }
    }

    private class FavoritableProgram {
        private Program mProgram;
        private boolean mIsFavorite;

        FavoritableProgram(Program program, boolean favorite) {
            mProgram = program;
            mIsFavorite = favorite;
        }
    }
}
