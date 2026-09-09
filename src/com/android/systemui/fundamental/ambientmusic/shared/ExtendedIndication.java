/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.shared;

import android.app.PendingIntent;

import java.util.Objects;

/**
 * Extra payload ASI sends with {@code USE_EXTENDED_INTERACTION} (A17 stock model): the
 * title/artist split, the intent fired when the collapsed row is tapped, and whether the row is
 * a recognition result or a "still identifying" placeholder. {@link #expandedIndicationData} is
 * only set by the AMBIENT_INDICATION_EXPAND broadcast.
 */
public final class ExtendedIndication {
    public final CharSequence songTitle;
    public final CharSequence artistName;
    public final PendingIntent expandIntent;
    public final Boolean isRecognitionResult;
    public final Boolean isSongSearching;
    public final ExpandedIndicationData expandedIndicationData;

    public ExtendedIndication(
            CharSequence songTitle,
            CharSequence artistName,
            PendingIntent expandIntent,
            Boolean isRecognitionResult,
            Boolean isSongSearching,
            ExpandedIndicationData expandedIndicationData) {
        this.songTitle = songTitle;
        this.artistName = artistName;
        this.expandIntent = expandIntent;
        this.isRecognitionResult = isRecognitionResult;
        this.isSongSearching = isSongSearching;
        this.expandedIndicationData = expandedIndicationData;
    }

    public boolean isRecognitionResult() {
        return Boolean.TRUE.equals(isRecognitionResult);
    }

    public boolean isSongSearching() {
        return Boolean.TRUE.equals(isSongSearching);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtendedIndication)) {
            return false;
        }
        ExtendedIndication other = (ExtendedIndication) o;
        return Objects.equals(songTitle, other.songTitle)
                && Objects.equals(artistName, other.artistName)
                && Objects.equals(expandIntent, other.expandIntent)
                && Objects.equals(isRecognitionResult, other.isRecognitionResult)
                && Objects.equals(isSongSearching, other.isSongSearching)
                && Objects.equals(expandedIndicationData, other.expandedIndicationData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(songTitle, artistName, expandIntent, isRecognitionResult,
                isSongSearching, expandedIndicationData);
    }

    @Override
    public String toString() {
        return "ExtendedIndication{songTitle=" + songTitle
                + ", artistName=" + artistName
                + ", expandIntent=" + expandIntent
                + ", isRecognitionResult=" + isRecognitionResult
                + ", isSongSearching=" + isSongSearching
                + ", expandedIndicationData=" + expandedIndicationData + "}";
    }
}
