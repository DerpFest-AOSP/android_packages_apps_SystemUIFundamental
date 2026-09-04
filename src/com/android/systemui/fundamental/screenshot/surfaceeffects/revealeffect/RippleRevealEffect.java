/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Ported from com.google.android.systemui.screenshot.surfaceeffects.revealeffect.RippleRevealEffect.
 *
 * Expands two sparkling, fading circles to reveal content. The per-frame uniform math is
 * reproduced faithfully from the stock animator update listener; the animator wrapper (0..1 over
 * the configured duration) was reconstructed to be functionally equivalent (the stock wrapper was
 * inlined by R8 into the screenshot executor). On end, {@code onEnd} runs (the stock code hid the
 * blurred preview so the sharp preview underneath shows through).
 */
package com.android.systemui.fundamental.screenshot.surfaceeffects.revealeffect;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;

import com.android.systemui.fundamental.screenshot.surfaceeffects.RevealDrawCallback;
import com.android.systemui.fundamental.screenshot.surfaceeffects.utils.MathUtils;

public final class RippleRevealEffect {

    /** Scales absolute play time (ms) into the shader's sparkle time domain. */
    private static final float TIME_SCALE = 0.00175f;

    private RippleRevealEffectConfig mConfig;
    private RippleRevealShader mShader;
    private ValueAnimator mAnimator;

    /**
     * Builds the shader, applies {@code config}, and starts the reveal.
     *
     * @param drawCallback receives the shader {@link RenderEffect} each frame; the caller may
     *                     chain it (e.g. with a blur) before applying it to a view.
     * @param onEnd        run when the reveal completes (may be {@code null}).
     */
    public void play(RippleRevealEffectConfig config, RevealDrawCallback drawCallback,
            Runnable onEnd) {
        mConfig = config;
        mShader = new RippleRevealShader();
        mShader.applyConfig(config);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration((long) config.duration);
        animator.addUpdateListener(anim -> {
            float time = anim.getCurrentPlayTime();
            mShader.setFloatUniform("in_time", TIME_SCALE * time);

            float fraction = (float) anim.getAnimatedValue();
            float innerRadius =
                    MathUtils.lerp(mConfig.innerRadiusStart, mConfig.innerRadiusEnd, fraction);
            float outerRadius =
                    MathUtils.lerp(mConfig.outerRadiusStart, mConfig.outerRadiusEnd, fraction);
            mShader.setFloatUniform("in_innerRadius", innerRadius);
            mShader.setFloatUniform("in_outerRadius", outerRadius);

            float innerFade = MathUtils.constrainedMap(
                    1f, 0f, mConfig.innerFadeOutStart, mConfig.duration, time);
            float outerFade = MathUtils.constrainedMap(
                    1f, 0f, mConfig.outerFadeOutStart, mConfig.duration, time);
            mShader.setColorUniform(
                    "in_innerColor", withAlpha(mConfig.innerColor, (int) (255f * innerFade)));
            mShader.setColorUniform(
                    "in_outerColor", withAlpha(mConfig.outerColor, (int) (255f * outerFade)));
            mShader.setFloatUniform("in_dstAlpha", Math.max(innerFade, outerFade));
            mShader.setFloatUniform("in_sparkleAlpha", Math.min(innerFade, outerFade));

            drawCallback.onDraw(RenderEffect.createRuntimeShaderEffect(mShader, "in_dst"));
        });
        if (onEnd != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        mAnimator = animator;
        animator.start();
    }

    /** Reapplies a (typically resized) config to the live shader. */
    public void updateConfig(RippleRevealEffectConfig config) {
        mConfig = config;
        if (mShader != null) {
            mShader.applyConfig(config);
        }
    }

    public boolean isPlaying() {
        return mAnimator != null && mAnimator.isRunning();
    }

    /** Cancels the animation if running. */
    public void finish() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
    }

    /** Replaces the alpha channel of {@code color} with {@code alpha} (0..255). Self-contained. */
    private static int withAlpha(int color, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
