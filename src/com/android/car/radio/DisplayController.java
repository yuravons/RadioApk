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
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.audio.PlaybackStateListenerAdapter;
import com.android.car.radio.widget.PlayPauseButton;

import java.util.Objects;

/**
 * Controller that controls the appearance state of various UI elements in the radio.
 */
public class DisplayController {
    private final Context mContext;

    private TextView mChannel;

    private TextView mCurrentSongTitleAndArtist;
    private TextView mCurrentStation;

    private ImageView mBackwardSeekButton;
    private ImageView mForwardSeekButton;

    private PlayPauseButton mPlayButton;

    private boolean mIsFavorite = false;
    private ImageView mFavoriteButton;
    private FavoriteToggleListener mFavoriteToggleListener;

    /**
     * Callback for favorite toggle button.
     */
    public interface FavoriteToggleListener {
        /**
         * Called when favorite toggle button was clicked.
         *
         * @param addFavorite {@code} true, if the callback should add the current program to
         *        favorites, {@code false} otherwise.
         */
        void onFavoriteToggled(boolean addFavorite);
    }

    public DisplayController(@NonNull Context context,
            @NonNull RadioController radioController) {
        mContext = Objects.requireNonNull(context);

        radioController.addRadioServiceConnectionListener(() ->
                radioController.addPlaybackStateListener(new PlaybackStateListenerAdapter(
                        this::onPlaybackStateChanged)));
    }

    /**
     * Initializes this {@link DisplayController}.
     */
    public void initialize(View container) {
        mChannel = container.findViewById(R.id.radio_station_channel);

        mCurrentSongTitleAndArtist = container.findViewById(R.id.radio_station_details);
        mCurrentStation = container.findViewById(R.id.radio_station_name);

        mBackwardSeekButton = container.findViewById(R.id.radio_back_button);
        mForwardSeekButton = container.findViewById(R.id.radio_forward_button);

        mPlayButton = container.findViewById(R.id.radio_play_button);

        mFavoriteButton = container.findViewById(R.id.radio_add_presets_button);
        if (mFavoriteButton != null) {
            mFavoriteButton.setOnClickListener(v -> {
                FavoriteToggleListener listener = mFavoriteToggleListener;
                if (listener != null) listener.onFavoriteToggled(!mIsFavorite);
            });
        }
    }

    /**
     * Set whether or not the buttons controlled by this controller are enabled. If {@code false}
     * is passed to this method, then no {@link View.OnClickListener}s will be
     * triggered when the buttons are pressed. In addition, the look of the button wil be updated
     * to reflect their disabled state.
     */
    public void setEnabled(boolean enabled) {
        // Color the buttons so that they are grey in appearance if they are disabled.
        int tint = enabled
                ? mContext.getColor(R.color.car_radio_control_button)
                : mContext.getColor(R.color.car_radio_control_button_disabled);

        if (mPlayButton != null) {
            // No need to tint the play button because its drawable already contains a disabled
            // state.
            mPlayButton.setEnabled(enabled);
        }

        if (mForwardSeekButton != null) {
            mForwardSeekButton.setEnabled(enabled);
            mForwardSeekButton.setColorFilter(tint);
        }

        if (mBackwardSeekButton != null) {
            mBackwardSeekButton.setEnabled(enabled);
            mBackwardSeekButton.setColorFilter(tint);
        }

        if (mFavoriteButton != null) {
            mFavoriteButton.setEnabled(enabled);
            mFavoriteButton.setColorFilter(tint);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the backwards seek button.
     */
    public void setBackwardSeekButtonListener(View.OnClickListener listener) {
        if (mBackwardSeekButton != null) {
            mBackwardSeekButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the forward seek button.
     */
    public void setForwardSeekButtonListener(View.OnClickListener listener) {
        if (mForwardSeekButton != null) {
            mForwardSeekButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the play button. Clicking on this
     * button should toggle the radio from muted to un-muted.
     */
    public void setPlayButtonListener(View.OnClickListener listener) {
        if (mPlayButton != null) {
            mPlayButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the listener for favorite toggle button.
     *
     * @param listener Listener to set, or {@code null} to remove
     */
    public void setFavoriteToggleListener(@Nullable FavoriteToggleListener listener) {
        mFavoriteToggleListener = listener;
    }

    /**
     * Sets the current radio channel (e.g. 88.5 FM).
     */
    public void setChannel(String channel) {
        if (mChannel == null) return;
        mChannel.setText(channel);
    }

    /**
     * Sets the title of the currently playing song.
     */
    public void setCurrentSongTitleAndArtist(String songTitle, String songArtist) {
        if (mCurrentSongTitleAndArtist != null) {
            boolean isTitleEmpty = TextUtils.isEmpty(songTitle);
            boolean isArtistEmpty = TextUtils.isEmpty(songArtist);
            String titleAndArtist = null;
            if (!isTitleEmpty) {
                titleAndArtist = songTitle.trim();
                if (!isArtistEmpty) {
                    titleAndArtist += '\u2014' + songArtist.trim();
                }
            } else if (!isArtistEmpty) {
                titleAndArtist = songArtist.trim();
            }
            mCurrentSongTitleAndArtist.setText(titleAndArtist);
            mCurrentSongTitleAndArtist.setVisibility(
                    (isTitleEmpty && isArtistEmpty) ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /**
     * Sets the artist(s) of the currently playing song or current radio station information
     * (e.g. KOIT).
     */
    public void setCurrentStation(String stationName) {
        if (mCurrentStation != null) {
            boolean isEmpty = TextUtils.isEmpty(stationName);
            mCurrentStation.setText(isEmpty ? null : stationName.trim());
            mCurrentStation.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void onPlaybackStateChanged(@PlaybackStateCompat.State int state) {
        if (mPlayButton != null) {
            mPlayButton.setPlayState(state);
            mPlayButton.refreshDrawableState();
        }
    }

    /**
     * Sets whether or not the current program is stored as a favorite. If it is, then the
     * icon will be updatd to reflect this state.
     */
    public void setCurrentIsFavorite(boolean isFavorite) {
        mIsFavorite = isFavorite;
        if (mFavoriteButton == null) return;
        mFavoriteButton.setImageResource(
                isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_empty);
    }
}
