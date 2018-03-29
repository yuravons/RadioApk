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

package com.android.car.radio.service;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.radio.RadioStorage;
import com.android.car.radio.platform.ProgramSelectorExt;

import java.util.Objects;

/**
 * A representation of a radio station.
 */
public class RadioStation implements Parcelable {
    private final ProgramSelector mSelector;
    private final RadioRds mRds;

    public RadioStation(@NonNull ProgramSelector selector, @Nullable RadioRds rds) {
        mSelector = Objects.requireNonNull(selector);
        mRds = rds;
    }

    /**
     * @param channelNumber Channel number in Hz.
     * @param subChannelNumber The subchannel number.
     * @param band One of {@link android.hardware.radio.RadioManager#BAND_AM},
     *             {@link android.hardware.radio.RadioManager#BAND_FM},
     *             {@link android.hardware.radio.RadioManager#BAND_AM_HD} or
     *             {@link android.hardware.radio.RadioManager#BAND_FM_HD}.
     * @param rds The Radio Data System for a particular channel. This represents the radio
     *            metadata.
     */
    public RadioStation(int channelNumber, int subChannelNumber, int band,
            @Nullable RadioRds rds) {
        if (channelNumber == RadioStorage.INVALID_RADIO_CHANNEL) {
            mSelector = null;
            mRds = null;
        } else {
            mSelector = ProgramSelectorExt.createAmFmSelector(channelNumber);
            mRds = rds;
        }
    }

    private RadioStation(Parcel in) {
        mSelector = in.readTypedObject(ProgramSelector.CREATOR);
        mRds = in.readTypedObject(RadioRds.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(mSelector, 0);
        out.writeTypedObject(mRds, 0);
    }

    public @NonNull ProgramSelector getSelector() {
        return Objects.requireNonNull(mSelector);
    }

    public int getChannelNumber() {
        if (mSelector == null) return RadioStorage.INVALID_RADIO_CHANNEL;
        return (int)mSelector.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
    }

    public int getRadioBand() {
        if (mSelector == null) return RadioStorage.INVALID_RADIO_BAND;
        if (ProgramSelectorExt.isAmFrequency(getChannelNumber())) return RadioManager.BAND_AM;
        else return RadioManager.BAND_FM;
    }

    @Nullable
    public RadioRds getRds() {
        return mRds;
    }

    @Override
    public String toString() {
        return String.format("RadioStation [selector: %s, rds: %s]", mSelector, mRds);
    }

    // stations are considered equal if their selectors are equal
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RadioStation)) return false;
        RadioStation other = (RadioStation) obj;
        return Objects.equals(mSelector, other.mSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSelector);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RadioStation> CREATOR =
            new Parcelable.Creator<RadioStation>() {
                public RadioStation createFromParcel(Parcel in) {
                    return new RadioStation(in);
                }

                public RadioStation[] newArray(int size) {
                    return new RadioStation[size];
                }
            };
}
