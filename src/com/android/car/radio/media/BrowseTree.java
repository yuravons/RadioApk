/**
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

package com.android.car.radio.media;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.BandDescriptor;
import android.hardware.radio.RadioManager;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat.BrowserRoot;
import android.support.v4.media.MediaBrowserServiceCompat.Result;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;

import com.android.car.radio.R;
import com.android.car.radio.platform.ProgramSelectorExt;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BrowseTree {
    private static final String TAG = "BcRadioApp.BrowseTree";

    private static final String NODE_ROOT = "root_id";
    private static final String NODE_PROGRAMS = "programs_id";
    private static final String NODE_FAVORITES = "favorites_id";

    private static final String NODEPREFIX_BAND = "band:";
    private static final String NODEPREFIX_AMFMCHANNEL = "amfm:";

    private static final BrowserRoot mRoot = new BrowserRoot(NODE_ROOT, null);

    private final Object mLock = new Object();
    private final @NonNull MediaBrowserServiceCompat mBrowserService;

    private List<MediaItem> mRootChildren;

    private final AmFmChannelList amChannels = new AmFmChannelList(
            NODEPREFIX_BAND + "am", R.string.radio_am_text);
    private final AmFmChannelList fmChannels = new AmFmChannelList(
            NODEPREFIX_BAND + "fm", R.string.radio_fm_text);

    public BrowseTree(@NonNull MediaBrowserServiceCompat browserService) {
        mBrowserService = Objects.requireNonNull(browserService);
    }

    public BrowserRoot getRoot() {
        return mRoot;
    }

    private static MediaItem createChild(MediaDescriptionCompat.Builder descBuilder,
            String mediaId, String title, int flag) {
        return new MediaItem(descBuilder.setMediaId(mediaId).setTitle(title).build(), flag);
    }

    public void setAmFmRegionConfig(@Nullable List<BandDescriptor> amFmBands) {
        List<BandDescriptor> amBands = new ArrayList<>();
        List<BandDescriptor> fmBands = new ArrayList<>();

        if (amFmBands != null) {
            for (BandDescriptor band : amFmBands) {
                final int freq = band.getLowerLimit();
                if (ProgramSelectorExt.isAmFrequency(freq)) amBands.add(band);
                else if (ProgramSelectorExt.isFmFrequency(freq)) fmBands.add(band);
            }
        }

        synchronized (mLock) {
            amChannels.setBands(amBands);
            fmChannels.setBands(fmBands);
            mRootChildren = null;
            mBrowserService.notifyChildrenChanged(NODE_ROOT);
        }
    }

    private List<MediaItem> getRootChildren() {
        synchronized (mLock) {
            if (mRootChildren != null) return mRootChildren;
            mRootChildren = new ArrayList<>();

            MediaDescriptionCompat.Builder dbld = new MediaDescriptionCompat.Builder();
            int f = MediaItem.FLAG_BROWSABLE;
            if (true) {  // TODO(b/75970985): implement program list and favorites support
                mRootChildren.add(createChild(dbld, NODE_PROGRAMS,
                        mBrowserService.getString(R.string.program_list_text), f));
                mRootChildren.add(createChild(dbld, NODE_FAVORITES,
                        mBrowserService.getString(R.string.favorites_list_text), f));
            }

            MediaItem amRoot = amChannels.getBandRoot();
            if (amRoot != null) mRootChildren.add(amRoot);
            MediaItem fmRoot = fmChannels.getBandRoot();
            if (fmRoot != null) mRootChildren.add(fmRoot);

            return mRootChildren;
        }
    }

    private class AmFmChannelList {
        public final @NonNull String mMediaId;
        private final @StringRes int mBandName;
        private @Nullable List<BandDescriptor> mBands;
        private @Nullable List<MediaItem> mChannels;

        public AmFmChannelList(@NonNull String mediaId, @StringRes int bandName) {
            mMediaId = Objects.requireNonNull(mediaId);
            mBandName = bandName;
        }

        public void setBands(List<BandDescriptor> bands) {
            synchronized (mLock) {
                mBands = bands;
                mChannels = null;
                mBrowserService.notifyChildrenChanged(mMediaId);
            }
        }

        private boolean isEmpty() {
            if (mBands == null) {
                Log.w(TAG, "AM/FM configuration not set");
                return true;
            }
            return mBands.isEmpty();
        }

        public @Nullable MediaItem getBandRoot() {
            if (isEmpty()) return null;
            int flag = MediaItem.FLAG_BROWSABLE | MediaItem.FLAG_PLAYABLE;
            return new MediaItem((new MediaDescriptionCompat.Builder()).
                    setMediaId(mMediaId).
                    setTitle(mBrowserService.getString(mBandName)).build(), flag);
        }

        public List<MediaItem> getChannels() {
            synchronized (mLock) {
                if (mChannels != null) return mChannels;
                if (isEmpty()) return null;
                mChannels = new ArrayList<>();

                MediaDescriptionCompat.Builder dbld = new MediaDescriptionCompat.Builder();

                for (BandDescriptor band : mBands) {
                    final int lowerLimit = band.getLowerLimit();
                    final int upperLimit = band.getUpperLimit();
                    final int spacing = band.getSpacing();
                    for (int ch = lowerLimit; ch <= upperLimit; ch += spacing) {
                        mChannels.add(createChild(dbld, NODEPREFIX_AMFMCHANNEL + ch,
                                ProgramSelectorExt.formatAmFmFrequency(ch, true),
                                MediaItem.FLAG_PLAYABLE));
                    }
                }

                return mChannels;
            }
        }
    }

    public void loadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        if (parentMediaId == null || result == null) return;

        if (NODE_ROOT.equals(parentMediaId)) {
            result.sendResult(getRootChildren());
        } else if (parentMediaId.equals(amChannels.mMediaId)) {
            result.sendResult(amChannels.getChannels());
        } else if (parentMediaId.equals(fmChannels.mMediaId)) {
            result.sendResult(fmChannels.getChannels());
        } else {
            Log.w(TAG, "Invalid parent media ID: " + parentMediaId);
            result.sendResult(null);
        }
    }

    // TODO(b/75970985): parse to ProgramSelector, not RadioStation
    public @Nullable RadioStation parseMediaId(@Nullable String mediaId) {
        if (mediaId == null) return null;
        if (!mediaId.startsWith(NODEPREFIX_AMFMCHANNEL)) return null;

        String freqStr = mediaId.substring(NODEPREFIX_AMFMCHANNEL.length());
        int freqInt;
        try {
            freqInt = Integer.parseInt(freqStr);
        } catch (NumberFormatException ex) {
            Log.e(TAG, "Invalid frequency", ex);
            return null;
        }
        return new RadioStation(freqInt);
    }
}
