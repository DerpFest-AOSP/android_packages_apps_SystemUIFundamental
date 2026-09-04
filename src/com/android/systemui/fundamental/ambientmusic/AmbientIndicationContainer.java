/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.app.animation.Interpolators;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.R;
import com.android.systemui.util.wakelock.DelayedWakeLock;
import com.android.systemui.util.wakelock.WakeLock;

import java.util.Objects;

/**
 * Passive Now Playing view: a music-note icon + a single track line that taps through to ASI.
 *
 * <p>Deps are injected field-by-field by {@code KeyguardAmbientIndicationAreaViewBinder} rather
 * than by constructor, because the view is instantiated by the layout inflater. v1 drops the
 * expanded album-art card, reverse/wireless charging text and the notification-panel bottom-spacing
 * bookkeeping that the stock Pixel container carried.
 */
public final class AmbientIndicationContainer extends AutoReinflateContainer {

    private static final String TAG = "AmbientIndication";
    private static final String WAKE_TAG = "AmbientIndication";

    // Injected by the view binder before initializeView().
    public PowerInteractor mPowerInteractor;
    public ActivityStarter mActivityStarter;
    public DelayedWakeLock.Factory mDelayedWakeLockFactory;
    public WakeLock mWakeLock;

    private final Rect mIconBounds = new Rect();

    private CharSequence mAmbientMusicText;
    private PendingIntent mOpenIntent;
    private PendingIntent mFavoritingIntent;
    private boolean mAmbientSkipUnlock;
    private int mIconOverride = -1;
    private String mIconDescription;
    private boolean mDozing;

    private Drawable mAmbientIconOverride;
    private Drawable mAmbientMusicNoteIcon;
    private int mAmbientIndicationIconSize;
    private int mAmbientMusicNoteIconSize;
    private int mTextColor;
    private ValueAnimator mTextColorAnimator;

    private TextView mTextView;
    private ImageView mIconView;
    private ConstraintLayout mAmbientIndication;
    private boolean mInflated;

    public AmbientIndicationContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @VisibleForTesting
    public WakeLock createWakeLock() {
        return mDelayedWakeLockFactory.create(WAKE_TAG);
    }

    /** Wires the inflate listener; safe to call after the binder has populated the deps. */
    public void initializeView() {
        addInflateListener(new AutoReinflateContainer.InflateListener() {
            @Override
            public void onInflated(View view) {
                mTextView = findViewById(R.id.ambient_indication_text);
                mIconView = findViewById(R.id.ambient_indication_icon);
                mAmbientIndication = findViewById(R.id.ambient_indication);
                if (mTextView == null || mIconView == null || mAmbientIndication == null) {
                    return;
                }
                ConstraintSet constraintSet = new ConstraintSet();
                int[] udfpsProps = getContext().getResources().getIntArray(
                        com.android.internal.R.array.config_udfps_sensor_props);
                if (!ArrayUtils.isEmpty(udfpsProps)) {
                    constraintSet.load(getContext(), R.xml.ambient_indication_inner_downwards);
                } else {
                    constraintSet.load(getContext(), R.xml.ambient_indication_inner_upwards);
                }
                constraintSet.applyTo(mAmbientIndication);
                mAmbientMusicNoteIcon = null;
                mTextColor = mTextView.getCurrentTextColor();
                mAmbientIndicationIconSize =
                        getResources().getDimensionPixelSize(R.dimen.ambient_indication_icon_size);
                mAmbientMusicNoteIconSize = getResources().getDimensionPixelSize(
                        R.dimen.ambient_indication_note_icon_size);
                mTextView.setEnabled(!mDozing);
                updateColors();
                updatePill();
                mTextView.setOnClickListener(v -> onTextClick());
                mIconView.setOnClickListener(v -> onIconClick());
                mInflated = true;
            }
        });
    }

    /** Called by the binder when the keyguard doze state changes. */
    public void setDozing(boolean dozing) {
        mDozing = dozing;
        if (mTextView != null) {
            mTextView.setEnabled(!dozing);
            updateColors();
        }
    }

    static void sendBroadcastWithoutDismissingKeyguard(PendingIntent pendingIntent) {
        if (pendingIntent.isActivity()) {
            return;
        }
        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "Sending intent failed: " + e);
        }
    }

    private void onTextClick() {
        if (mOpenIntent == null) {
            return;
        }
        mPowerInteractor.wakeUpIfDozing(WAKE_TAG, PowerManager.WAKE_REASON_GESTURE);
        if (mAmbientSkipUnlock) {
            sendBroadcastWithoutDismissingKeyguard(mOpenIntent);
        } else {
            mActivityStarter.startPendingIntentDismissingKeyguard(mOpenIntent);
        }
    }

    private void onIconClick() {
        if (mFavoritingIntent != null) {
            mPowerInteractor.wakeUpIfDozing(WAKE_TAG, PowerManager.WAKE_REASON_GESTURE);
            sendBroadcastWithoutDismissingKeyguard(mFavoritingIntent);
            return;
        }
        onTextClick();
    }

    public void hideAmbientMusic() {
        setAmbientMusic(null, null, null, 0, false, null);
    }

    public void setAmbientMusic(
            CharSequence text,
            PendingIntent openIntent,
            PendingIntent favoritingIntent,
            int iconOverride,
            boolean skipUnlock,
            String iconDescription) {
        if (Objects.equals(mAmbientMusicText, text)
                && Objects.equals(mOpenIntent, openIntent)
                && Objects.equals(mFavoritingIntent, favoritingIntent)
                && mIconOverride == iconOverride
                && Objects.equals(mIconDescription, iconDescription)
                && mAmbientSkipUnlock == skipUnlock) {
            return;
        }
        mAmbientMusicText = text;
        mOpenIntent = openIntent;
        mFavoritingIntent = favoritingIntent;
        mAmbientSkipUnlock = skipUnlock;
        mIconOverride = iconOverride;
        mIconDescription = iconDescription;
        Drawable override;
        switch (iconOverride) {
            case 1:
                override = getContext().getDrawable(R.drawable.fundamental_ic_music_search);
                break;
            case 3:
                override = getContext().getDrawable(R.drawable.fundamental_ic_music_not_found);
                break;
            case 4:
                override = getContext().getDrawable(R.drawable.fundamental_ic_cloud_off);
                break;
            case 5:
                override = getContext().getDrawable(R.drawable.fundamental_ic_favorite);
                break;
            case 6:
                override = getContext().getDrawable(R.drawable.fundamental_ic_favorite_border);
                break;
            case 7:
                override = getContext().getDrawable(R.drawable.fundamental_ic_error);
                break;
            case 8:
                override = getContext().getDrawable(R.drawable.fundamental_ic_favorite_note);
                break;
            default:
                override = null;
                break;
        }
        mAmbientIconOverride = override;
        updatePill();
    }

    private void updateColors() {
        if (mTextColorAnimator != null && mTextColorAnimator.isRunning()) {
            mTextColorAnimator.cancel();
        }
        int current = mTextView.getTextColors().getDefaultColor();
        int target = mDozing ? -1 : mTextColor;
        if (current == target) {
            mTextView.setTextColor(target);
            mIconView.setImageTintList(ColorStateList.valueOf(target));
            return;
        }
        mTextColorAnimator = ValueAnimator.ofArgb(current, target);
        mTextColorAnimator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        mTextColorAnimator.setDuration(500L);
        mTextColorAnimator.addUpdateListener(animator -> {
            int value = (Integer) animator.getAnimatedValue();
            mTextView.setTextColor(value);
            mIconView.setImageTintList(ColorStateList.valueOf(value));
        });
        mTextColorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTextColorAnimator = null;
            }
        });
        mTextColorAnimator.start();
    }

    /** Recomputes the icon + text + visibility for the current state. */
    public void updatePill() {
        if (mTextView == null || mIconView == null) {
            return;
        }
        final CharSequence text = mAmbientMusicText;
        final boolean hasText = !TextUtils.isEmpty(text);
        final boolean wasVisible = mTextView.getVisibility() == View.VISIBLE;

        mTextView.setClickable(mOpenIntent != null);
        mIconView.setClickable(mOpenIntent != null || mFavoritingIntent != null);

        CharSequence iconDescription =
                TextUtils.isEmpty(mIconDescription) ? text : mIconDescription;

        Drawable baseIcon = mAmbientIconOverride;
        if (baseIcon == null && hasText) {
            if (mAmbientMusicNoteIcon == null) {
                mAmbientMusicNoteIcon = getContext().getDrawable(com.android.systemui.res.R.drawable.ic_music_note);
            }
            baseIcon = mAmbientMusicNoteIcon;
        }

        mTextView.setText(text);
        mTextView.setContentDescription(text);
        mIconView.setContentDescription(iconDescription);

        Drawable displayIcon = null;
        if (baseIcon != null) {
            mIconBounds.set(0, 0, baseIcon.getIntrinsicWidth(), baseIcon.getIntrinsicHeight());
            int fit = (baseIcon == mAmbientMusicNoteIcon)
                    ? mAmbientMusicNoteIconSize : mAmbientIndicationIconSize;
            MathUtils.fitRect(mIconBounds, fit);
            displayIcon = new DrawableWrapper(baseIcon) {
                @Override
                public int getIntrinsicHeight() {
                    return mIconBounds.height();
                }

                @Override
                public int getIntrinsicWidth() {
                    return mIconBounds.width();
                }
            };
            int endPadding = hasText
                    ? (int) (getResources().getDisplayMetrics().density * 24.0f) : 0;
            mTextView.setPaddingRelative(mTextView.getPaddingStart(), mTextView.getPaddingTop(),
                    endPadding, mTextView.getPaddingBottom());
        } else {
            mTextView.setPaddingRelative(mTextView.getPaddingStart(), mTextView.getPaddingTop(),
                    0, mTextView.getPaddingBottom());
        }
        mIconView.setImageDrawable(displayIcon);

        int visibility = hasText ? View.VISIBLE : View.GONE;
        mTextView.setVisibility(visibility);
        mIconView.setVisibility(displayIcon == null ? View.GONE : visibility);

        if (hasText && !wasVisible) {
            if (mWakeLock != null) {
                mWakeLock.acquire(WAKE_TAG);
            }
            if (baseIcon instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) baseIcon).start();
            }
            mTextView.setTranslationY(mTextView.getHeight() / 2f);
            mTextView.setAlpha(0f);
            mTextView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(150L)
                    .setDuration(100L)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mWakeLock != null) {
                                mWakeLock.release(WAKE_TAG);
                            }
                            mTextView.animate().setListener(null);
                        }
                    })
                    .setInterpolator(Interpolators.DECELERATE_QUINT)
                    .start();
        } else if (!hasText) {
            mTextView.animate().cancel();
            if (baseIcon instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) baseIcon).reset();
            }
        }
    }
}
