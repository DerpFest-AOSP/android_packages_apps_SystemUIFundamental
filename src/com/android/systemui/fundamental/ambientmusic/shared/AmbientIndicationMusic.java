/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.shared;

import android.app.PendingIntent;

import java.util.Objects;

/**
 * Immutable snapshot of the "Now Playing" indication pushed by ASI. {@link #extendedIndication}
 * is null for the plain passive line and set when ASI asks for the extended interaction
 * (title/artist row that can expand into the album-art card).
 */
public final class AmbientIndicationMusic {
    public final CharSequence text;
    public final PendingIntent openIntent;
    public final PendingIntent favoritingIntent;
    public final Integer iconOverride;
    public final Boolean skipUnlock;
    public final String iconDescription;
    public final ExtendedIndication extendedIndication;

    public AmbientIndicationMusic(
            CharSequence text,
            PendingIntent openIntent,
            PendingIntent favoritingIntent,
            Integer iconOverride,
            Boolean skipUnlock,
            String iconDescription,
            ExtendedIndication extendedIndication) {
        this.text = text;
        this.openIntent = openIntent;
        this.favoritingIntent = favoritingIntent;
        this.iconOverride = iconOverride;
        this.skipUnlock = skipUnlock;
        this.iconDescription = iconDescription;
        this.extendedIndication = extendedIndication;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AmbientIndicationMusic)) {
            return false;
        }
        AmbientIndicationMusic other = (AmbientIndicationMusic) o;
        return Objects.equals(text, other.text)
                && Objects.equals(openIntent, other.openIntent)
                && Objects.equals(favoritingIntent, other.favoritingIntent)
                && Objects.equals(iconOverride, other.iconOverride)
                && Objects.equals(skipUnlock, other.skipUnlock)
                && Objects.equals(iconDescription, other.iconDescription)
                && Objects.equals(extendedIndication, other.extendedIndication);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, openIntent, favoritingIntent, iconOverride, skipUnlock,
                iconDescription, extendedIndication);
    }

    @Override
    public String toString() {
        return "AmbientIndicationMusic{text=" + text
                + ", openIntent=" + openIntent
                + ", favoritingIntent=" + favoritingIntent
                + ", iconOverride=" + iconOverride
                + ", skipUnlock=" + skipUnlock
                + ", iconDescription=" + iconDescription
                + ", extendedIndication=" + extendedIndication + "}";
    }
}
