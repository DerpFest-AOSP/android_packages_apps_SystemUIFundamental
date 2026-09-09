/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import android.app.WallpaperColors;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Handler;

import com.android.systemui.graphics.ImageLoader;
import com.android.systemui.monet.ColorScheme;
import com.android.systemui.util.ColorUtilKt;

import java.util.Objects;

/**
 * Loads the album art behind the expanded Now Playing card off the main thread and derives a
 * Monet {@link ColorScheme} from it (port of the A17 stock
 * {@code AmbientIndicationArtworkHelper}). The last result is cached so re-expanding the same
 * track does not hit the content provider again.
 */
final class AmbientIndicationArtworkHelper {

    /** Small thumbnail rendered behind the icon glyph while expanded. */
    private static final int SMALL_ICON_SIZE_DP = 48;
    /** Scrim gradient alphas (centre -> edge) laid over the scaled artwork. */
    private static final float SCRIM_CENTER_ALPHA = 0.65f;
    private static final float SCRIM_EDGE_ALPHA = 0.75f;
    /** Tone of the secondary palette used for the backdrop colour and the scrim. */
    static final int BACKDROP_TONE = 20;
    /** {@code android.content.theming.ThemeStyle.CONTENT}: seed the scheme from the artwork. */
    private static final int THEME_STYLE_CONTENT = 6;

    /** Receives the processed artwork on the main thread. */
    interface Callback {
        void onArtworkProcessed(Drawable artwork, ColorScheme colorScheme, Uri albumArtUri,
                Drawable smallIcon);
    }

    static final class ArtworkResult {
        final Uri albumArtUri;
        final Drawable artwork;
        final ColorScheme colorScheme;
        final Drawable smallIcon;

        ArtworkResult(Uri albumArtUri, Drawable artwork, ColorScheme colorScheme,
                Drawable smallIcon) {
            this.albumArtUri = albumArtUri;
            this.artwork = artwork;
            this.colorScheme = colorScheme;
            this.smallIcon = smallIcon;
        }
    }

    private static volatile ArtworkResult sLastArtworkResult;

    private AmbientIndicationArtworkHelper() {
    }

    /** Returns the cached result if it was produced for exactly {@code albumArtUri}. */
    static ArtworkResult getCachedResult(Uri albumArtUri) {
        ArtworkResult last = sLastArtworkResult;
        if (last != null && Objects.equals(albumArtUri, last.albumArtUri)) {
            return last;
        }
        return null;
    }

    /**
     * Decodes, scales and scrims the artwork. Must run on a background executor; the callback is
     * always posted to {@code mainHandler}.
     */
    static void processArtwork(Context context, ImageLoader imageLoader, Uri albumArtUri,
            int targetWidth, int targetHeight, Handler mainHandler, Callback callback) {
        if (albumArtUri == null) {
            mainHandler.post(() -> callback.onArtworkProcessed(null, null, null, null));
            return;
        }
        Bitmap bitmap = imageLoader.loadBitmapSync(new ImageLoader.Uri(albumArtUri, null),
                targetWidth, targetWidth, ImageDecoder.ALLOCATOR_SOFTWARE);
        if (bitmap == null) {
            mainHandler.post(() -> callback.onArtworkProcessed(null, null, albumArtUri, null));
            return;
        }
        WallpaperColors wallpaperColors = WallpaperColors.fromBitmap(bitmap);
        if (wallpaperColors == null) {
            mainHandler.post(() -> callback.onArtworkProcessed(null, null, albumArtUri, null));
            return;
        }

        int smallIconSize = (int) (SMALL_ICON_SIZE_DP * context.getResources().getDisplayMetrics()
                .density);
        BitmapDrawable smallIcon = new BitmapDrawable(context.getResources(),
                Bitmap.createScaledBitmap(bitmap, smallIconSize, smallIconSize, true));
        ColorScheme colorScheme = new ColorScheme(wallpaperColors, false, THEME_STYLE_CONTENT);

        float scale = (float) Math.max(targetWidth, targetHeight)
                / Math.max(bitmap.getWidth(), bitmap.getHeight());
        int scaledWidth = (int) (bitmap.getWidth() * scale);
        int scaledHeight = (int) (bitmap.getHeight() * scale);
        Bitmap scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaled);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate((targetWidth - scaledWidth) / 2f, (targetHeight - scaledHeight) / 2f);
        canvas.drawBitmap(bitmap, matrix, null);
        BitmapDrawable artworkDrawable = new BitmapDrawable(context.getResources(), scaled);

        Drawable scrimDrawable = context.getDrawable(
                com.android.systemui.res.R.drawable.qs_media_scrim);
        GradientDrawable scrim = (GradientDrawable) (scrimDrawable != null
                ? scrimDrawable.mutate() : null);
        int backdrop = colorScheme.getMaterialScheme().secondaryPalette.tone(BACKDROP_TONE);
        Drawable[] layers;
        if (scrim != null) {
            scrim.setColors(new int[]{
                    ColorUtilKt.getColorWithAlpha(backdrop, SCRIM_CENTER_ALPHA),
                    ColorUtilKt.getColorWithAlpha(backdrop, SCRIM_EDGE_ALPHA)});
            layers = new Drawable[]{artworkDrawable, scrim};
        } else {
            layers = new Drawable[]{artworkDrawable};
        }
        LayerDrawable artwork = new LayerDrawable(layers);

        sLastArtworkResult = new ArtworkResult(albumArtUri, artwork, colorScheme, smallIcon);
        bitmap.recycle();
        mainHandler.post(
                () -> callback.onArtworkProcessed(artwork, colorScheme, albumArtUri, smallIcon));
    }
}
