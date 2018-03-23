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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BrowseTree {
    private static final String TAG = "BcRadioApp.BrowseTree";

    private static final String NODE_ROOT = "root_id";
    private static final String NODE_CHANNELS = "channels_id";
    private static final String NODE_PROGRAMS = "programs_id";
    private static final String NODE_FAVORITES = "favorites_id";

    static final String NODEPREFIX_AMFMCHANNEL = "amfm:";

    private final @NonNull MediaBrowserServiceCompat mBrowserService;

    private final List<MediaItem> mRootChildren = new ArrayList<>();

    private final Object mChannelsLock = new Object();
    private @Nullable List<MediaItem> mChannels;
    private @Nullable List<BandDescriptor> mAmFmBands;

    public BrowseTree(@NonNull MediaBrowserServiceCompat browserService) {
        mBrowserService = Objects.requireNonNull(browserService);

        /* TODO(b/75970985): split channels to bands, so the top-level nodes
         * would be "AM" and "FM", not "Channels". Probably below "Favorites".
         */
        MediaDescriptionCompat.Builder dbld = new MediaDescriptionCompat.Builder();
        int f = MediaItem.FLAG_BROWSABLE;
        /* Channels are going to be split between AM/FM bands, so the following line will
         * go away soon (we don't translate that string). */
        mRootChildren.add(createChild(dbld, NODE_CHANNELS, "Channels", f));
        if (false) {  // TODO(b/75970985): implement program list and favorites support
            mRootChildren.add(createChild(dbld, NODE_PROGRAMS,
                    browserService.getString(R.string.program_list_text), f));
            mRootChildren.add(createChild(dbld, NODE_FAVORITES,
                    browserService.getString(R.string.favorites_list_text), f));
        }
    }

    public BrowserRoot getRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // TODO(b/75970985): check permissions, if necessary

        return new BrowserRoot(NODE_ROOT, null);
    }

    private static MediaItem createChild(MediaDescriptionCompat.Builder descBuilder,
            String mediaId, String title, int flag) {
        return new MediaItem(descBuilder.setMediaId(mediaId).setTitle(title).build(), flag);
    }

    public void setAmFmRegionConfig(@Nullable List<BandDescriptor> amFmBands) {
        synchronized (mChannelsLock) {
            mAmFmBands = amFmBands;
            mChannels = null;
            // TODO(b/75970985): if amFmBands == null, then remove channels top-level directory
            mBrowserService.notifyChildrenChanged(NODE_CHANNELS);
        }
    }

    private List<MediaItem> getChannels() {
        synchronized (mChannelsLock) {
            if (mChannels != null) return mChannels;
            if (mAmFmBands == null) {
                Log.w(TAG, "AM/FM configuration not set");
                return null;
            }
            mChannels = new ArrayList<>();

            MediaDescriptionCompat.Builder dbld = new MediaDescriptionCompat.Builder();

            for (BandDescriptor band : mAmFmBands) {
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

    public void loadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        if (NODE_ROOT.equals(parentMediaId)) {
            result.sendResult(mRootChildren);
        } else if (NODE_CHANNELS.equals(parentMediaId)) {
            result.sendResult(getChannels());
        } else {
            Log.w(TAG, "Invalid parent media ID: " + parentMediaId);
            result.sendResult(null);
        }
    }
}
