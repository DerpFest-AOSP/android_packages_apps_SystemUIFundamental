/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Spring specs and one-shot spring helpers used by the expanded Now Playing card
 * (port of the A17 stock {@code AmbientIndicationAnimationUtils}).
 */
final class AmbientIndicationAnimationUtils {

    /** Spatial (translation / scale) springs. */
    static final SpringForce DEFAULT_SPATIAL_SPEC = spec(0.8f, 380f);
    static final SpringForce SLOW_SPATIAL_SPEC = spec(0.8f, 200f);
    static final SpringForce FAST_SPATIAL_SPEC = spec(0.6f, 800f);
    /** Effects (alpha / color) springs. */
    static final SpringForce DEFAULT_EFFECTS_SPEC = spec(1f, 1600f);
    static final SpringForce FAST_EFFECTS_SPEC = spec(1f, 3800f);
    static final SpringForce SLOW_EFFECTS_SPEC = spec(1f, 800f);

    private AmbientIndicationAnimationUtils() {
    }

    private static SpringForce spec(float dampingRatio, float stiffness) {
        SpringForce force = new SpringForce();
        force.setDampingRatio(dampingRatio);
        force.setStiffness(stiffness);
        return force;
    }

    /** Copies damping/stiffness of {@code spec} into a new force that rests at {@code finalPosition}. */
    static SpringForce copySpringForce(SpringForce spec, float finalPosition) {
        SpringForce force = new SpringForce(finalPosition);
        force.setDampingRatio(spec.getDampingRatio());
        force.setStiffness(spec.getStiffness());
        return force;
    }

    static void animateAlpha(View view, float target,
            DynamicAnimation.OnAnimationUpdateListener onUpdate, Runnable onEnd,
            SpringForce spec) {
        start(new SpringAnimation(view, DynamicAnimation.ALPHA, target), target, onUpdate, onEnd,
                spec);
    }

    static void animateTranslationX(View view, float target,
            DynamicAnimation.OnAnimationUpdateListener onUpdate, Runnable onEnd,
            SpringForce spec) {
        start(new SpringAnimation(view, DynamicAnimation.TRANSLATION_X, target), target, onUpdate,
                onEnd, spec);
    }

    static void animateTranslationY(View view, float target,
            DynamicAnimation.OnAnimationUpdateListener onUpdate, Runnable onEnd,
            SpringForce spec) {
        start(new SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, target), target, onUpdate,
                onEnd, spec);
    }

    static void animateScaleX(View view, float target, SpringForce spec) {
        start(new SpringAnimation(view, DynamicAnimation.SCALE_X, target), target, null, null,
                spec);
    }

    /** Springs {@code drawable}'s alpha (0..255), invalidating {@code host} on every frame. */
    static void animateDrawableAlpha(Drawable drawable, View host, int target,
            DynamicAnimation.OnAnimationUpdateListener onUpdate, Runnable onEnd,
            SpringForce spec) {
        FloatPropertyCompat<Drawable> alphaProperty =
                new FloatPropertyCompat<Drawable>("drawableAlpha") {
                    @Override
                    public float getValue(Drawable d) {
                        return d.getAlpha();
                    }

                    @Override
                    public void setValue(Drawable d, float value) {
                        d.setAlpha((int) value);
                        host.invalidate();
                    }
                };
        start(new SpringAnimation(drawable, alphaProperty, target), target, onUpdate, onEnd, spec);
    }

    /** Like {@link #animateDrawableAlpha} but applies the alpha to every layer. */
    static void animateLayeredDrawableAlpha(LayerDrawable drawable, View host, int target,
            Runnable onEnd, SpringForce spec) {
        FloatPropertyCompat<LayerDrawable> alphaProperty =
                new FloatPropertyCompat<LayerDrawable>("layeredDrawableAlpha") {
                    @Override
                    public float getValue(LayerDrawable d) {
                        return d.getDrawable(0).getAlpha();
                    }

                    @Override
                    public void setValue(LayerDrawable d, float value) {
                        int layers = d.getNumberOfLayers();
                        for (int i = 0; i < layers; i++) {
                            d.getDrawable(i).setAlpha((int) value);
                        }
                        host.invalidate();
                    }
                };
        start(new SpringAnimation(drawable, alphaProperty, target), target, null, onEnd, spec);
    }

    private static void start(SpringAnimation animation, float target,
            DynamicAnimation.OnAnimationUpdateListener onUpdate, Runnable onEnd,
            SpringForce spec) {
        animation.setSpring(copySpringForce(spec, target));
        if (onUpdate != null) {
            animation.addUpdateListener(onUpdate);
        }
        if (onEnd != null) {
            animation.addEndListener((anim, canceled, value, velocity) -> onEnd.run());
        }
        animation.start();
    }
}
