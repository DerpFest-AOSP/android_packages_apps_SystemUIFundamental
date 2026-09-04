/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.shared;

/**
 * State of the on-demand "search a song" quick affordance, driven by ASI's
 * ACTION_UPDATE_QUICK_AFFORDANCE_STATE broadcast.
 */
public final class AmbientIndicationMusicStatus {
    public final boolean isEnabled;
    public final boolean isActive;

    public AmbientIndicationMusicStatus(boolean isEnabled, boolean isActive) {
        this.isEnabled = isEnabled;
        this.isActive = isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AmbientIndicationMusicStatus)) {
            return false;
        }
        AmbientIndicationMusicStatus other = (AmbientIndicationMusicStatus) o;
        return isEnabled == other.isEnabled && isActive == other.isActive;
    }

    @Override
    public int hashCode() {
        return (Boolean.hashCode(isEnabled) * 31) + Boolean.hashCode(isActive);
    }

    @Override
    public String toString() {
        return "AmbientIndicationMusicStatus{isEnabled=" + isEnabled + ", isActive=" + isActive
                + "}";
    }
}
