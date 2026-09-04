/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * FundamentalOS screenshot-preview reveal effects. Extends the AOSP ThumbnailObserver seam
 * (com.android.systemui.screenshot.ThumbnailObserver), which ScreenshotShelfViewProxy drives via
 * setViews()/onEntranceStarted()/onEntranceComplete(). Mirrors the wiring done by the stock Pixel
 * ThumbnailObserverGoogle, but self-contained: the effects live entirely in this module.
 *
 * Additive by design: if the views are missing/unmeasured it silently no-ops, leaving the stock
 * screenshot flow untouched.
 */
package com.android.systemui.fundamental.screenshot;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.fundamental.screenshot.surfaceeffects.gloweffect.GlowPieEffect;
import com.android.systemui.fundamental.screenshot.surfaceeffects.gloweffect.GlowPieEffectConfig;
import com.android.systemui.fundamental.screenshot.surfaceeffects.revealeffect.RippleRevealEffect;
import com.android.systemui.fundamental.screenshot.surfaceeffects.revealeffect.RippleRevealEffectConfig;
import com.android.systemui.screenshot.ThumbnailObserver;

public final class FundamentalThumbnailObserver extends ThumbnailObserver {

    /** Corner radius (dp) of the glowing border box. */
    private static final float BORDER_CORNER_RADIUS_DP = 32f;
    /** Blur radius applied to the preview image, in dp. */
    private static final float PREVIEW_BLUR_DP = 20f;

    private ImageView mImage;
    private View mBorder;

    private final GlowPieEffect mGlowEffect = new GlowPieEffect();
    private final RippleRevealEffect mRevealEffect = new RippleRevealEffect();

    public FundamentalThumbnailObserver() {}

    @Override
    public void setViews(ImageView image, View border) {
        mImage = image;
        mBorder = border;
    }

    @Override
    public void onEntranceStarted() {
        final ImageView image = mImage;
        final View border = mBorder;
        if (image == null || border == null) {
            return;
        }
        // Views must be measured before we can build the geometry-dependent configs.
        if (border.getWidth() == 0 || border.getHeight() == 0
                || image.getWidth() == 0 || image.getHeight() == 0) {
            return;
        }

        // Glowing animated border around the preview.
        mGlowEffect.play(createGlowBorderConfig(border),
                renderEffect -> border.setRenderEffect(renderEffect));

        // Ripple reveal over a blurred copy of the preview; when it finishes, hide the blur layer
        // so the sharp preview underneath shows through.
        final DisplayMetrics dm = image.getResources().getDisplayMetrics();
        final float blur = dm.density * PREVIEW_BLUR_DP;
        final RenderEffect blurEffect =
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP);
        mRevealEffect.play(
                createRippleRevealConfig(image),
                renderEffect -> image.setRenderEffect(
                        RenderEffect.createChainEffect(renderEffect, blurEffect)),
                () -> image.setVisibility(View.INVISIBLE));
    }

    @Override
    public void onEntranceComplete() {
        // No-op: the effects run on their own timelines (reveal ~1s, glow ~3.5s) and release when
        // they complete. The entrance-complete bracket is intentionally decoupled from the effect
        // lifecycle, matching the stock behaviour where the resting glow persists on the preview.
    }

    private static GlowPieEffectConfig createGlowBorderConfig(View view) {
        final Context ctx = view.getContext();
        final float width = view.getWidth();
        final float height = view.getHeight();
        final int innerColor = ctx.getColor(android.R.color.white);
        final int accentColor = ctx.getColor(android.R.color.system_accent1_500);
        final float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, BORDER_CORNER_RADIUS_DP,
                ctx.getResources().getDisplayMetrics());

        GlowPieEffectConfig config = new GlowPieEffectConfig();
        config.centerX = width * 0.5f;
        config.centerY = height * 0.5f;
        config.width = width;
        config.height = height;
        config.cornerRadius = cornerRadius;
        // {base, first, second}
        config.colors = new int[] {innerColor, accentColor, innerColor};
        return config;
    }

    private static RippleRevealEffectConfig createRippleRevealConfig(View view) {
        final Context ctx = view.getContext();
        final float max = Math.max(view.getWidth(), view.getHeight()) * 3.0f;
        final float radiusStart = 0.05f * max;
        final int innerColor = ctx.getColor(android.R.color.white);
        final int accentColor = ctx.getColor(android.R.color.system_accent1_500);

        RippleRevealEffectConfig config = new RippleRevealEffectConfig();
        config.centerX = view.getWidth() * 0.5f;
        config.centerY = view.getHeight();
        config.innerRadiusStart = radiusStart;
        config.innerRadiusEnd = max;
        config.outerRadiusStart = radiusStart * 1.25f;
        config.outerRadiusEnd = max * 1.25f;
        config.pixelDensity = ctx.getResources().getDisplayMetrics().density;
        config.innerColor = innerColor;
        config.outerColor = accentColor;
        return config;
    }
}
