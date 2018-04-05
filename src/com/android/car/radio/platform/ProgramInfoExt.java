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

package com.android.car.radio.platform;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Bitmap;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.media.MediaMetadata;
import android.media.Rating;
import android.util.Log;

/**
 * Proposed extensions to android.hardware.radio.RadioManager.ProgramInfo.
 *
 * They might eventually get pushed to the framework.
 */
public class ProgramInfoExt {
    private static final String TAG = "BcRadioApp.pinfoext";

    private static final char EN_DASH = '\u2013';
    private static final String TITLE_SEPARATOR = " " + EN_DASH + " ";

    private static final String[] programNameOrder = new String[] {
        RadioMetadata.METADATA_KEY_PROGRAM_NAME,
        RadioMetadata.METADATA_KEY_DAB_COMPONENT_NAME,
        RadioMetadata.METADATA_KEY_DAB_SERVICE_NAME,
        RadioMetadata.METADATA_KEY_DAB_ENSEMBLE_NAME,
        RadioMetadata.METADATA_KEY_RDS_PS,
    };

    public static @NonNull String getProgramName(@NonNull ProgramInfo info) {
        RadioMetadata meta = info.getMetadata();
        if (meta != null) {
            for (String key : programNameOrder) {
                String value = meta.getString(key);
                if (value != null) return value;
            }
        }

        String sel = ProgramSelectorExt.getDisplayName(info.getSelector());
        if (sel != null) return sel;
        Log.w(TAG, "ProgramInfo without a name");
        return "";
    }

    public static @NonNull MediaMetadata toMediaMetadata(@NonNull ProgramInfo info,
            boolean isFavorite, @Nullable ImageResolver imageResolver) {
        MediaMetadata.Builder bld = new MediaMetadata.Builder();

        bld.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, getProgramName(info));

        RadioMetadata meta = info.getMetadata();
        if (meta != null) {
            String title = meta.getString(RadioMetadata.METADATA_KEY_TITLE);
            if (title != null) {
                bld.putString(MediaMetadata.METADATA_KEY_TITLE, title);
            }
            String artist = meta.getString(RadioMetadata.METADATA_KEY_ARTIST);
            if (artist != null) {
                bld.putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
            }
            String album = meta.getString(RadioMetadata.METADATA_KEY_ALBUM);
            if (album != null) {
                bld.putString(MediaMetadata.METADATA_KEY_ALBUM, album);
            }
            if (title != null || artist != null) {
                String subtitle;
                if (title == null) subtitle = artist;
                else if (artist == null) subtitle = title;
                else subtitle = title + TITLE_SEPARATOR + artist;
                bld.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle);
            }
            long albumArtId = RadioMetadataExt.getGlobalBitmapId(meta,
                    RadioMetadata.METADATA_KEY_ART);
            if (albumArtId != 0 && imageResolver != null) {
                Bitmap bm = imageResolver.resolve(albumArtId);
                if (bm != null) bld.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bm);
            }
        }

        bld.putRating(MediaMetadata.METADATA_KEY_USER_RATING, Rating.newHeartRating(isFavorite));

        return bld.build();
    }
}
