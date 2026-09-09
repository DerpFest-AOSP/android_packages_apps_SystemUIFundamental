/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.DEFAULT_EFFECTS_SPEC;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.DEFAULT_SPATIAL_SPEC;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.FAST_EFFECTS_SPEC;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.FAST_SPATIAL_SPEC;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.SLOW_EFFECTS_SPEC;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.SLOW_SPATIAL_SPEC;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.animateAlpha;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.animateDrawableAlpha;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.animateLayeredDrawableAlpha;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.animateScaleX;
import static com.android.systemui.fundamental.ambientmusic.AmbientIndicationAnimationUtils.animateTranslationY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Trace;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.systemui.util.concurrency.DelayableExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * The choreographed pieces of the expanded Now Playing card: action-button fades, album-art
 * background reveal/hide, icon <-> thumbnail cross-fades, colour transitions, and the
 * first-recognition / song-searching / song-change entrances (port of the A17 stock
 * {@code AmbientIndicationAnimationHelper}).
 */
final class AmbientIndicationAnimationHelper {

    private static final long COLOR_ANIMATION_DURATION_MS = 200L;
    private static final long BACKGROUND_REVEAL_DELAY_MS = 33L;
    private static final long SONG_SEARCHING_FADE_DELAY_MS = 50L;
    /** Alpha (0..255) thresholds at which the chained animations kick in. */
    private static final float ICON_SWAP_THRESHOLD = 51f;
    private static final float THUMBNAIL_REVEAL_THRESHOLD = 114.75f;
    private static final float COLLAPSE_ICON_REVEAL_THRESHOLD = 191.25f;
    private static final float COLLAPSE_BACKGROUND_CANCEL_THRESHOLD = 25.5f;
    private static final float SONG_CHANGE_TITLE_THRESHOLD_PX = 44.65f;
    private static final float SONG_CHANGE_ARTIST_THRESHOLD = 0.35f;
    private static final float BACKGROUND_EXPANDED_SCALE_X = 1.05f;

    private AmbientIndicationAnimationHelper() {
    }

    /**
     * Fades {@code buttonView} to {@code alpha}; once the button crosses
     * {@code thresholdToStartIconAnimation} its icon follows. Fading out also hides the button
     * (INVISIBLE at 20%) and finally the whole action container (GONE).
     */
    static void animateActionButtonsAlphaWithSpring(View buttonView, View iconView,
            View actionContainer, float alpha, float thresholdToStartIconAnimation) {
        if (buttonView.getVisibility() == View.GONE) {
            return;
        }
        final boolean fadingIn = alpha == 1f;
        final AtomicBoolean hasTriggeredIconAnim = new AtomicBoolean(false);
        final SpringForce spec = fadingIn ? DEFAULT_EFFECTS_SPEC : FAST_EFFECTS_SPEC;
        final DynamicAnimation.OnAnimationUpdateListener[] self =
                new DynamicAnimation.OnAnimationUpdateListener[1];
        self[0] = (animation, value, velocity) -> {
            if (fadingIn && value >= thresholdToStartIconAnimation) {
                animateAlpha(iconView, alpha, null, null, spec);
                animation.removeUpdateListener(self[0]);
                return;
            }
            if (!fadingIn && value <= thresholdToStartIconAnimation
                    && !hasTriggeredIconAnim.get()) {
                animateAlpha(iconView, alpha, null, null, spec);
                hasTriggeredIconAnim.set(true);
            }
            if (!fadingIn && value <= 0.2f) {
                buttonView.setVisibility(View.INVISIBLE);
                animation.removeUpdateListener(self[0]);
            }
        };
        animateAlpha(buttonView, alpha, self[0], () -> {
            if (!fadingIn) {
                actionContainer.setVisibility(View.GONE);
            }
        }, spec);
    }

    /** Cross-fades the drawable currently in {@code iconView} to {@code toIcon}. */
    static void animateIconTransition(ImageView iconView, Drawable toIcon) {
        Drawable fromIcon = iconView.getDrawable();
        if (fromIcon == null) {
            toIcon.setAlpha(0);
            iconView.setImageDrawable(toIcon);
            animateDrawableAlpha(toIcon, iconView, 255, null, null, DEFAULT_EFFECTS_SPEC);
            return;
        }
        final DynamicAnimation.OnAnimationUpdateListener[] self =
                new DynamicAnimation.OnAnimationUpdateListener[1];
        self[0] = (animation, value, velocity) -> {
            if (value <= ICON_SWAP_THRESHOLD) {
                animation.removeUpdateListener(self[0]);
                toIcon.setAlpha(0);
                iconView.setImageDrawable(toIcon);
                animateDrawableAlpha(toIcon, iconView, 255, null, null, DEFAULT_EFFECTS_SPEC);
                animation.cancel();
            }
        };
        animateDrawableAlpha(fromIcon, iconView, 0, self[0], null, FAST_EFFECTS_SPEC);
    }

    /** Animates a drawable colour with an ARGB evaluator; jumps straight to the target if equal. */
    static <T> void animateDrawableColor(T drawable, int fromColor, int toColor, Runnable onEnd,
            BiConsumer<T, Integer> setColor) {
        if (fromColor == toColor) {
            setColor.accept(drawable, toColor);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        animator.setDuration(COLOR_ANIMATION_DURATION_MS);
        animator.addUpdateListener(
                anim -> setColor.accept(drawable, (Integer) anim.getAnimatedValue()));
        if (onEnd != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        animator.start();
    }

    static int getBackgroundColor(View view) {
        Drawable background = view.getBackground();
        if (background instanceof GradientDrawable) {
            ColorStateList color = ((GradientDrawable) background).getColor();
            return color != null ? color.getDefaultColor() : 0;
        }
        if (background instanceof ColorDrawable) {
            return ((ColorDrawable) background).getColor();
        }
        return 0;
    }

    static void animateImageTint(ImageView view, int toColor) {
        ColorStateList tint = view.getImageTintList();
        int fromColor = tint != null ? tint.getDefaultColor() : 0;
        ValueAnimator animator = ValueAnimator.ofArgb(fromColor, toColor);
        animator.setDuration(COLOR_ANIMATION_DURATION_MS);
        animator.addUpdateListener(anim ->
                view.setImageTintList(ColorStateList.valueOf((Integer) anim.getAnimatedValue())));
        animator.start();
    }

    static void animateTextColors(List<TextView> textViews, int toColor) {
        int fromColor = textViews.get(0).getTextColors().getDefaultColor();
        ValueAnimator animator = ValueAnimator.ofArgb(fromColor, toColor);
        animator.setDuration(COLOR_ANIMATION_DURATION_MS);
        animator.addUpdateListener(anim -> {
            int color = (Integer) anim.getAnimatedValue();
            for (TextView textView : textViews) {
                textView.setTextColor(color);
            }
        });
        animator.start();
    }

    /**
     * Reveals the album-art background: tints the (new or existing) colour backdrop to
     * {@code toBgColor}, then fades/scales in {@code toSrc} (the scrimmed artwork).
     */
    static void animateBackgroundArtworkInExpand(ImageView view, int toBgColor, Drawable toSrc,
            Runnable bgAnimationEnd, DelayableExecutor mainDelayableExecutor) {
        Drawable background = view.getBackground();
        if (background == null) {
            mainDelayableExecutor.executeDelayed(() -> {
                ColorDrawable colorDrawable = new ColorDrawable(0);
                view.setBackground(colorDrawable);
                animateDrawableColor(colorDrawable, 0, toBgColor, bgAnimationEnd,
                        ColorDrawable::setColor);
                startToSrcAnimation(toSrc, view);
            }, BACKGROUND_REVEAL_DELAY_MS);
            return;
        }
        if (background instanceof ColorDrawable) {
            animateDrawableColor((ColorDrawable) background, getBackgroundColor(view), toBgColor,
                    bgAnimationEnd, ColorDrawable::setColor);
        }
        startToSrcAnimation(toSrc, view);
    }

    private static void startToSrcAnimation(Drawable toSrc, ImageView view) {
        if (toSrc == null) {
            return;
        }
        toSrc.setAlpha(0);
        view.setImageDrawable(toSrc);
        Runnable onEnd = () -> Trace.endAsyncSection(AmbientIndicationContainer.TRACE_BIND_ARTWORK,
                AmbientIndicationContainer.TRACE_COOKIE_BIND_ARTWORK);
        if (toSrc instanceof LayerDrawable) {
            animateLayeredDrawableAlpha((LayerDrawable) toSrc, view, 255, onEnd,
                    SLOW_EFFECTS_SPEC);
        } else {
            animateDrawableAlpha(toSrc, view, 255, null, onEnd, SLOW_EFFECTS_SPEC);
        }
        animateScaleX(view, BACKGROUND_EXPANDED_SCALE_X, DEFAULT_SPATIAL_SPEC);
    }

    /** Fades out and clears the album-art background, scaling it back to 1.0. */
    static void animateBackgroundArtworkInCollapse(ImageView view) {
        animateAlpha(view, 0f, (animation, value, velocity) -> {
            if (value <= 0.1f) {
                animation.cancel();
            }
        }, () -> {
            view.setBackground(null);
            view.setImageDrawable(null);
            view.setAlpha(1f);
            view.setVisibility(View.GONE);
        }, DEFAULT_EFFECTS_SPEC);
        animateScaleX(view, 1f, DEFAULT_SPATIAL_SPEC);
    }

    /** Expanded without artwork: neutral thumbnail backdrop + tinted music-note glyph. */
    static void animateIconAndThumbnailOnExpandNoAlbumArt(ImageView iconView, int backdropColor,
            int iconTintColor, Drawable noteIcon) {
        animateImageTint(iconView, iconTintColor);
        ColorDrawable backdrop = new ColorDrawable(backdropColor);
        backdrop.setAlpha(0);
        iconView.setBackground(backdrop);
        animateDrawableAlpha(backdrop, iconView, 255, null, null, DEFAULT_EFFECTS_SPEC);
        if (noteIcon != null) {
            animateIconTransition(iconView, noteIcon);
        }
    }

    /** Expanded with artwork: fade the glyph out, reveal the small album thumbnail behind it. */
    static void animateIconAndThumbnailOnExpandWithAlbumArt(ImageView iconView,
            Drawable toBgDrawable) {
        Drawable currentIcon = iconView.getDrawable();
        if (currentIcon == null) {
            toBgDrawable.setAlpha(0);
            iconView.setBackground(toBgDrawable);
            animateDrawableAlpha(toBgDrawable, iconView, 255, null, null, DEFAULT_EFFECTS_SPEC);
            iconView.setImageTintList(null);
            return;
        }
        final AtomicBoolean hasTriggeredBgDrawableAnim = new AtomicBoolean(false);
        animateDrawableAlpha(currentIcon, iconView, 0, (animation, value, velocity) -> {
            if (!hasTriggeredBgDrawableAnim.get() && value <= THUMBNAIL_REVEAL_THRESHOLD) {
                toBgDrawable.setAlpha(0);
                iconView.setBackground(toBgDrawable);
                animateDrawableAlpha(toBgDrawable, iconView, 255, null, null,
                        DEFAULT_EFFECTS_SPEC);
                hasTriggeredBgDrawableAnim.set(true);
            }
        }, () -> iconView.setImageTintList(null), FAST_EFFECTS_SPEC);
    }

    /** Collapse: drop the thumbnail / backdrop and bring the lockscreen glyph back. */
    static void animateIconAndThumbnailOnCollapse(ImageView iconView, Drawable toIcon,
            boolean hadAlbumArt) {
        Drawable background = iconView.getBackground();
        if (hadAlbumArt) {
            final AtomicBoolean hasTriggeredIconAnim = new AtomicBoolean(false);
            toIcon.setAlpha(0);
            iconView.setImageDrawable(toIcon);
            if (background == null) {
                animateDrawableAlpha(toIcon, iconView, 255, null, null, DEFAULT_EFFECTS_SPEC);
                return;
            }
            animateDrawableAlpha(background, iconView, 0, (animation, value, velocity) -> {
                if (value <= COLLAPSE_BACKGROUND_CANCEL_THRESHOLD) {
                    animation.cancel();
                }
                if (!hasTriggeredIconAnim.get() && value <= COLLAPSE_ICON_REVEAL_THRESHOLD) {
                    animateDrawableAlpha(iconView.getDrawable(), iconView, 255, null, null,
                            DEFAULT_EFFECTS_SPEC);
                    hasTriggeredIconAnim.set(true);
                }
            }, () -> iconView.setBackground(null), DEFAULT_EFFECTS_SPEC);
            return;
        }
        if (background != null) {
            animateDrawableAlpha(background, iconView, 0, null,
                    () -> iconView.setBackground(null), DEFAULT_EFFECTS_SPEC);
        }
        animateIconTransition(iconView, toIcon);
    }

    /** Re-tints the like/play pill backgrounds and their icons. */
    static void updateActionContainerColors(List<View> containers, List<ImageView> icons,
            int containerColor, int iconColor) {
        int fromContainerColor = getBackgroundColor(containers.get(0));
        for (View container : containers) {
            Drawable background = container.getBackground();
            if (background instanceof GradientDrawable) {
                animateDrawableColor((GradientDrawable) background, fromContainerColor,
                        containerColor, null, GradientDrawable::setColor);
            }
        }
        ColorStateList fromTint = icons.get(0).getImageTintList();
        int fromIconColor = fromTint != null ? fromTint.getDefaultColor() : 0;
        for (ImageView icon : icons) {
            ValueAnimator animator = ValueAnimator.ofArgb(fromIconColor, iconColor);
            animator.setDuration(COLOR_ANIMATION_DURATION_MS);
            animator.addUpdateListener(anim -> icon.setImageTintList(
                    ColorStateList.valueOf((Integer) anim.getAnimatedValue())));
            animator.start();
        }
    }

    /** Slides the extended container up and fades it in the first time a song is recognised. */
    static void performFirstRecognitionAnimation(View extendedContainer, float translationY) {
        Trace.beginAsyncSection(AmbientIndicationContainer.TRACE_FIRST_RECOGNITION_ANIMATION,
                AmbientIndicationContainer.TRACE_COOKIE_FIRST_RECOGNITION);
        extendedContainer.setTranslationY(translationY);
        extendedContainer.setAlpha(0f);
        animateTranslationY(extendedContainer, 0f, null, null, SLOW_SPATIAL_SPEC);
        animateAlpha(extendedContainer, 1f, null, () -> Trace.endAsyncSection(
                AmbientIndicationContainer.TRACE_FIRST_RECOGNITION_ANIMATION,
                AmbientIndicationContainer.TRACE_COOKIE_FIRST_RECOGNITION), SLOW_EFFECTS_SPEC);
    }

    /** Nudges the collapsed row in from below while a song search is in progress. */
    static void performSongSearchingAnimation(View collapsedContainer, float translationY,
            DelayableExecutor mainDelayableExecutor) {
        collapsedContainer.setTranslationY(translationY);
        collapsedContainer.setAlpha(0f);
        animateTranslationY(collapsedContainer, 0f, null, null, FAST_SPATIAL_SPEC);
        mainDelayableExecutor.executeDelayed(
                () -> animateAlpha(collapsedContainer, 1f, null, null, DEFAULT_EFFECTS_SPEC),
                SONG_SEARCHING_FADE_DELAY_MS);
    }

    /**
     * Song changed while collapsed: the old title/artist set slides up and out, the temp set
     * slides in from below (title first, then artist), then the real views take over.
     */
    static void runSongChangeContentSlide(View container, View realSet, TextView realSong,
            TextView realArtist, View tempSet, TextView tempSong, TextView tempArtist,
            CharSequence newSong, CharSequence newArtist, float translationY) {
        tempSong.setText(newSong);
        tempArtist.setText(newArtist);
        tempSet.setTranslationY(translationY);
        tempSet.setAlpha(1f);
        tempSong.setAlpha(0f);
        tempArtist.setAlpha(0f);
        animateTranslationY(realSet, -translationY, null, null, SLOW_SPATIAL_SPEC);
        animateAlpha(realSet, 0f, null, null, FAST_EFFECTS_SPEC);

        final AtomicInteger animationEndCounter = new AtomicInteger(3);
        final Runnable finalSwapAction = () -> {
            realSong.setText(newSong);
            realArtist.setText(newArtist);
            realSet.setTranslationY(0f);
            realSet.setAlpha(1f);
            tempSet.setAlpha(0f);
            tempSong.setText(null);
            tempArtist.setText(null);
            if (realSet.getLayoutParams().width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                realSet.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            container.setTranslationX(0f);
        };
        final Runnable sharedOnEndListener = () -> {
            if (animationEndCounter.decrementAndGet() == 0) {
                finalSwapAction.run();
            }
        };
        final DynamicAnimation.OnAnimationUpdateListener[] artistTrigger =
                new DynamicAnimation.OnAnimationUpdateListener[1];
        artistTrigger[0] = (animation, value, velocity) -> {
            if (value >= SONG_CHANGE_ARTIST_THRESHOLD) {
                animateAlpha(tempArtist, 1f, null, sharedOnEndListener, SLOW_EFFECTS_SPEC);
                animation.removeUpdateListener(artistTrigger[0]);
            }
        };
        final DynamicAnimation.OnAnimationUpdateListener[] titleTrigger =
                new DynamicAnimation.OnAnimationUpdateListener[1];
        titleTrigger[0] = (animation, value, velocity) -> {
            if (value <= SONG_CHANGE_TITLE_THRESHOLD_PX) {
                animateAlpha(tempSong, 1f, artistTrigger[0], sharedOnEndListener,
                        DEFAULT_EFFECTS_SPEC);
                animation.removeUpdateListener(titleTrigger[0]);
            }
        };
        animateTranslationY(tempSet, 0f, titleTrigger[0], sharedOnEndListener,
                SLOW_SPATIAL_SPEC);
    }
}
