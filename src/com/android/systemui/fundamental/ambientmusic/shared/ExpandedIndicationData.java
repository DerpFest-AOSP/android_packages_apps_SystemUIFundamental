/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.shared;

import android.app.PendingIntent;
import android.net.Uri;

import java.util.Objects;

/**
 * Payload of the AMBIENT_INDICATION_EXPAND broadcast (A17 stock model): album art, favourite
 * state and the default-music-player ("DMP") deep link shown as the play button.
 */
public final class ExpandedIndicationData {
    public final PendingIntent dmpIntent;
    public final String dmpPackageName;
    public final Uri albumArtUri;
    public final Boolean isFavorite;

    public ExpandedIndicationData(
            PendingIntent dmpIntent,
            String dmpPackageName,
            Uri albumArtUri,
            Boolean isFavorite) {
        this.dmpIntent = dmpIntent;
        this.dmpPackageName = dmpPackageName;
        this.albumArtUri = albumArtUri;
        this.isFavorite = isFavorite;
    }

    public boolean isFavorite() {
        return Boolean.TRUE.equals(isFavorite);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExpandedIndicationData)) {
            return false;
        }
        ExpandedIndicationData other = (ExpandedIndicationData) o;
        return Objects.equals(dmpIntent, other.dmpIntent)
                && Objects.equals(dmpPackageName, other.dmpPackageName)
                && Objects.equals(albumArtUri, other.albumArtUri)
                && Objects.equals(isFavorite, other.isFavorite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dmpIntent, dmpPackageName, albumArtUri, isFavorite);
    }

    @Override
    public String toString() {
        return "ExpandedIndicationData{dmpIntent=" + dmpIntent
                + ", dmpPackageName=" + dmpPackageName
                + ", albumArtUri=" + albumArtUri
                + ", isFavorite=" + isFavorite + "}";
    }
}
