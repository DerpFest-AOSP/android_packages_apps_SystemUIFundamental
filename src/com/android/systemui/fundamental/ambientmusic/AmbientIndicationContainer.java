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
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Trace;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LruCache;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.dynamicanimation.animation.DynamicAnimation;

import com.android.app.animation.Interpolators;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.R;
import com.android.systemui.fundamental.ambientmusic.shared.ExpandedIndicationData;
import com.android.systemui.fundamental.ambientmusic.shared.ExtendedIndication;
import com.android.systemui.graphics.ImageLoader;
import com.android.systemui.media.NotificationMediaManager;
import com.android.systemui.monet.ColorScheme;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.wakelock.DelayedWakeLock;
import com.android.systemui.util.wakelock.WakeLock;

import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme;
import com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Now Playing lockscreen view (port of the A17 stock {@code AmbientIndicationContainer}).
 *
 * <p>Two presentations share one layout:
 * <ul>
 *   <li>the passive line: music-note icon + one line of text that taps through to ASI;</li>
 *   <li>the extended row (ASI sends {@code USE_EXTENDED_INTERACTION}): bold title + artist that
 *       can expand into the album-art card with like / play-in-default-music-player buttons
 *       (AMBIENT_INDICATION_EXPAND), plus the "identifying song" and song-change entrances.</li>
 * </ul>
 *
 * <p>Deps are injected field-by-field by {@code KeyguardAmbientIndicationAreaViewBinder} rather
 * than by constructor, because the view is instantiated by the layout inflater. The stock
 * reverse/wireless-charging text and the Dreamliner dock icon are not carried over (Pixel-only
 * hardware features).
 */
public final class AmbientIndicationContainer extends AutoReinflateContainer
        implements StatusBarStateController.StateListener, NotificationMediaManager.MediaListener {

    private static final String TAG = "AmbientIndication";
    private static final String WAKE_TAG = "AmbientIndication";
    private static final String WAKE_REASON = "AMBIENT_MUSIC_CLICK";

    // Async trace sections, named like stock so systrace comparisons line up.
    static final String TRACE_FIRST_RECOGNITION_ANIMATION = "first_recognition_animation";
    static final int TRACE_COOKIE_FIRST_RECOGNITION = 1;
    static final String TRACE_EXPAND_ANIMATION = "expand_animation";
    static final int TRACE_COOKIE_EXPAND = 2;
    static final String TRACE_BIND_ARTWORK = "bind_artwork";
    static final int TRACE_COOKIE_BIND_ARTWORK = 3;
    static final String TRACE_COLLAPSE_ANIMATION = "collapse_animation";
    static final int TRACE_COOKIE_COLLAPSE = 4;

    /** Stock bakes this in at construction time; the extended row/card is always on. */
    private static final boolean ENABLE_EXTENDED_INTERACTION = true;

    private static final int INDICATION_TEXT_MODE_NONE = 0;
    private static final int INDICATION_TEXT_MODE_MUSIC = 1;

    private static final int ANIMATION_STATE_IDLE = 0;
    private static final int ANIMATION_STATE_EXPANDING = 1;
    private static final int ANIMATION_STATE_COLLAPSING = 2;

    private static final int MUSIC_APP_ICON_CACHE_SIZE = 5;
    private static final int MUSIC_APP_ICON_SIZE_DP = 36;
    private static final int WRAPPER_CORNER_RADIUS_DP = 32;
    private static final int ICON_CORNER_RADIUS_DP = 12;
    private static final int TEXT_END_PADDING_DP = 24;
    private static final int COLLAPSED_TEXT_SHIFT_DP = 14;
    private static final int LARGE_SCREEN_SMALLEST_WIDTH_DP = 600;
    private static final int LARGE_SCREEN_SIDE_MARGIN_DP = 24;
    private static final float ACTION_BUTTONS_FADE_IN_THRESHOLD = 0.6f;
    private static final float ACTION_BUTTONS_FADE_OUT_THRESHOLD = 0.75f;
    private static final float ACTION_CONTAINER_TRIGGER_FRACTION = 0.37f;
    private static final long ALBUM_ART_RELOAD_DELAY_MS = 800L;
    private static final long TEXT_COLOR_ANIMATION_DURATION_MS = 500L;

    // Injected by the view binder before initializeView().
    public PowerInteractor mPowerInteractor;
    public ActivityStarter mActivityStarter;
    public DelayedWakeLock.Factory mDelayedWakeLockFactory;
    public WakeLock mWakeLock;
    public FalsingManager mFalsingManager;
    public DelayableExecutor mMainDelayableExecutor;
    public Executor mBackgroundExecutor;
    public ImageLoader mImageLoader;
    public NotificationMediaManager mNotificationMediaManager;
    public StatusBarStateController mStatusBarStateController;
    /** Whether the device has an under-display fingerprint sensor (picks the constraint set). */
    public boolean mUdfpsSupported;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Rect mIconBounds = new Rect();
    private final LruCache<String, Drawable> mMusicAppIconCache =
            new LruCache<>(MUSIC_APP_ICON_CACHE_SIZE);

    private CharSequence mAmbientMusicText;
    private PendingIntent mOpenIntent;
    private PendingIntent mFavoritingIntent;
    private boolean mAmbientSkipUnlock;
    private int mIconOverride = -1;
    private String mIconDescription;
    private ExtendedIndication mExtendedIndication;
    private boolean mUsingExtendedIndication;
    private boolean mIsCurrentlyInExpandedState;
    private int mAnimationState = ANIMATION_STATE_IDLE;
    private Uri mCurrentLoadedAlbumArtUri;
    private int mIndicationTextMode = INDICATION_TEXT_MODE_NONE;
    private boolean mDozing;
    private int mStatusBarState;
    private int mMediaPlaybackState;
    private boolean mListenersRegistered;

    private Drawable mAmbientIconOverride;
    private Drawable mAmbientMusicNoteIcon;
    private int mAmbientIndicationIconSize;
    private int mAmbientMusicNoteIconSize;
    private int mTextColor;
    private ValueAnimator mTextColorAnimator;

    private ConstraintLayout mAmbientIndication;
    private LinearLayout mInfoContainer;
    private FrameLayout mWrapperContainer;
    private ImageView mContainerBackground;
    private FrameLayout mExtendedContainer;
    private LinearLayout mCollapsedContainer;
    private ImageView mIconView;
    private FrameLayout mTextContainer;
    private LinearLayout mRealTextSet;
    private LinearLayout mTempTextSet;
    private TextView mTextView;
    private TextView mTextViewExtended;
    private TextView mTempTextView;
    private TextView mTempTextViewExtended;
    private LinearLayout mActionContainer;
    private FrameLayout mLikeContainer;
    private ImageView mLikeIcon;
    private FrameLayout mPlayContainer;
    private ImageView mPlayIcon;
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
                AmbientIndicationContainer.this.onInflated();
            }
        });
    }

    private void onInflated() {
        mTextView = findViewById(R.id.ambient_indication_text);
        mIconView = findViewById(R.id.ambient_indication_icon);
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mInfoContainer = findViewById(R.id.ambient_indication_info_container);
        mContainerBackground = findViewById(R.id.ambient_indication_container_background);
        mWrapperContainer = findViewById(R.id.ambient_indication_wrapper_container);
        mExtendedContainer = findViewById(R.id.ambient_indication_extended_container);
        mCollapsedContainer = findViewById(R.id.ambient_indication_collapsed_container);
        mTextContainer = findViewById(R.id.ambient_indication_text_container);
        mActionContainer = findViewById(R.id.ambient_indication_action_container);
        mTextViewExtended = findViewById(R.id.ambient_indication_text_extended);
        mLikeContainer = findViewById(R.id.ambient_indication_like_container);
        mPlayContainer = findViewById(R.id.ambient_indication_play_container);
        mLikeIcon = findViewById(R.id.ambient_indication_like_icon);
        mPlayIcon = findViewById(R.id.ambient_indication_play_icon);
        mRealTextSet = findViewById(R.id.text_set_real);
        mTempTextSet = findViewById(R.id.text_set_temp);
        mTempTextView = findViewById(R.id.ambient_indication_text_temp);
        mTempTextViewExtended = findViewById(R.id.ambient_indication_text_extended_temp);
        if (mTextView == null || mIconView == null || mAmbientIndication == null
                || mWrapperContainer == null || mExtendedContainer == null
                || mCollapsedContainer == null || mTextContainer == null
                || mActionContainer == null || mTextViewExtended == null
                || mLikeContainer == null || mPlayContainer == null || mLikeIcon == null
                || mPlayIcon == null || mRealTextSet == null || mTempTextSet == null
                || mTempTextView == null || mTempTextViewExtended == null
                || mContainerBackground == null || mInfoContainer == null) {
            Log.w(TAG, "ambient_indication_inner is missing views; not binding");
            // Keep every later entry point (updatePill etc.) on its null-guard.
            mTextView = null;
            mIconView = null;
            mExtendedContainer = null;
            return;
        }
        if (ENABLE_EXTENDED_INTERACTION) {
            mWrapperContainer.post(
                    () -> applyRoundedOutline(mWrapperContainer, WRAPPER_CORNER_RADIUS_DP));
            mIconView.post(() -> applyRoundedOutline(mIconView, ICON_CORNER_RADIUS_DP));
        }

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.load(getContext(), mUdfpsSupported
                ? R.xml.ambient_indication_inner_downwards
                : R.xml.ambient_indication_inner_upwards);
        if (ENABLE_EXTENDED_INTERACTION) {
            // Stock only recentres when the Dreamliner dock icon is gone; we never show one.
            constraintSet.clear(R.id.ambient_indication_info_container, ConstraintSet.TOP);
            constraintSet.clear(R.id.ambient_indication_info_container, ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.ambient_indication_info_container, ConstraintSet.TOP,
                    R.id.ambient_indication, ConstraintSet.TOP);
            constraintSet.connect(R.id.ambient_indication_info_container, ConstraintSet.BOTTOM,
                    R.id.ambient_indication, ConstraintSet.BOTTOM);
        }
        constraintSet.applyTo(mAmbientIndication);

        mAmbientMusicNoteIcon = null;
        mTextColor = mTextView.getCurrentTextColor();
        mAmbientIndicationIconSize =
                getResources().getDimensionPixelSize(R.dimen.ambient_indication_icon_size);
        mAmbientMusicNoteIconSize =
                getResources().getDimensionPixelSize(R.dimen.ambient_indication_note_icon_size);
        if (ENABLE_EXTENDED_INTERACTION) {
            Configuration configuration = getResources().getConfiguration();
            updateContainerWidthOnFoldableDevice(configuration.screenWidthDp,
                    configuration.smallestScreenWidthDp);
        }
        mTextView.setEnabled(!mDozing);
        mIsCurrentlyInExpandedState = false;
        updateColors();
        updatePill();

        mTextView.setOnClickListener(v -> onTextClick());
        mTempTextView.setOnClickListener(v -> onTextClick());
        mIconView.setOnClickListener(v -> onIconClick());
        if (ENABLE_EXTENDED_INTERACTION) {
            mCollapsedContainer.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLICK,
                            getResources().getString(
                                    R.string.fundamental_accessibility_action_expand)));
                }
            });
            mCollapsedContainer.setOnClickListener(v -> onCollapsedContainerClick());
            mPlayContainer.setOnClickListener(v -> onPlayClick());
            mLikeContainer.setOnClickListener(v -> onIconClick());
            mExtendedContainer.setOnClickListener(v -> {
                if (mIsCurrentlyInExpandedState) {
                    onTextClick();
                }
            });
        }
        mInflated = true;
    }

    private void applyRoundedOutline(View view, int radiusDp) {
        final float radius = getPixelsFromDp(radiusDp);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle: statusbar state / dozing / media playback (stock pulls these via Dependency).
    // ---------------------------------------------------------------------------------------

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerListeners();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterListeners();
        mMediaPlaybackState = 0;
    }

    /** Idempotent; called on attach and again by the binder once the deps are injected. */
    public void registerListeners() {
        if (mListenersRegistered || !isAttachedToWindow()
                || mStatusBarStateController == null || mNotificationMediaManager == null) {
            return;
        }
        mListenersRegistered = true;
        mStatusBarStateController.addCallback(this);
        mNotificationMediaManager.addCallback(this);
        onStateChanged(mStatusBarStateController.getState());
        onDozingChanged(mStatusBarStateController.isDozing());
    }

    private void unregisterListeners() {
        if (!mListenersRegistered) {
            return;
        }
        mListenersRegistered = false;
        mStatusBarStateController.removeCallback(this);
        mNotificationMediaManager.removeCallback(this);
    }

    @Override
    public void onStateChanged(int newState) {
        mStatusBarState = newState;
        setVisibility(newState == StatusBarState.KEYGUARD ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onDozingChanged(boolean isDozing) {
        mDozing = isDozing;
        setVisibility(mStatusBarState == StatusBarState.KEYGUARD ? View.VISIBLE : View.INVISIBLE);
        if (mUsingExtendedIndication && mExtendedContainer != null) {
            mExtendedContainer.setEnabled(!isDozing);
            updateColors();
        } else if (mTextView != null) {
            mTextView.setEnabled(!isDozing);
            updateColors();
        }
    }

    @Override
    public void onPrimaryMetadataOrStateChanged(MediaMetadata metadata, int state) {
        if (mMediaPlaybackState == state) {
            return;
        }
        mMediaPlaybackState = state;
        if (NotificationMediaManager.isPlayingState(state)) {
            setAmbientMusic(null, null, null, 0, false, null, null);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Clicks
    // ---------------------------------------------------------------------------------------

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

    private boolean isFalseTap() {
        return mFalsingManager != null && mFalsingManager.isFalseTap(FalsingManager.LOW_PENALTY);
    }

    private void wakeUpIfDozing() {
        mPowerInteractor.wakeUpIfDozing(WAKE_REASON, PowerManager.WAKE_REASON_GESTURE);
    }

    private void onTextClick() {
        if (isFalseTap() || mOpenIntent == null) {
            return;
        }
        wakeUpIfDozing();
        if (mAmbientSkipUnlock) {
            sendBroadcastWithoutDismissingKeyguard(mOpenIntent);
        } else {
            mActivityStarter.startPendingIntentDismissingKeyguard(mOpenIntent);
        }
    }

    /** Icon (and, when expanded, the like button): favourite if possible, else open. */
    private void onIconClick() {
        if (isFalseTap()) {
            return;
        }
        if (mUsingExtendedIndication && !mIsCurrentlyInExpandedState
                && isExtendedIndicationRecognitionResult()) {
            return;
        }
        if (mFavoritingIntent == null) {
            onTextClick();
            return;
        }
        wakeUpIfDozing();
        sendBroadcastWithoutDismissingKeyguard(mFavoritingIntent);
    }

    /** Collapsed extended row: expand (if ASI already sent the card data) and notify ASI. */
    private void onCollapsedContainerClick() {
        if (isFalseTap() || !mUsingExtendedIndication || mExtendedIndication == null) {
            return;
        }
        if (mIsCurrentlyInExpandedState) {
            onTextClick();
            return;
        }
        if (mExtendedIndication.expandedIndicationData != null) {
            performExpandAnimation();
        }
        PendingIntent expandIntent = mExtendedIndication.expandIntent;
        if (expandIntent != null) {
            wakeUpIfDozing();
            sendBroadcastWithoutDismissingKeyguard(expandIntent);
        }
    }

    /** "Play on default music player" button. */
    private void onPlayClick() {
        if (isFalseTap() || !mIsCurrentlyInExpandedState || mExtendedIndication == null
                || mExtendedIndication.expandedIndicationData == null) {
            return;
        }
        PendingIntent dmpIntent = mExtendedIndication.expandedIndicationData.dmpIntent;
        if (dmpIntent != null) {
            wakeUpIfDozing();
            mActivityStarter.startPendingIntentDismissingKeyguard(dmpIntent);
        }
    }

    // ---------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------

    public void hideAmbientMusic() {
        setAmbientMusic(null, null, null, 0, false, null, null);
    }

    public void setAmbientMusic(
            CharSequence text,
            PendingIntent openIntent,
            PendingIntent favoritingIntent,
            int iconOverride,
            boolean skipUnlock,
            String iconDescription,
            ExtendedIndication extendedIndication) {
        if (Objects.equals(mAmbientMusicText, text)
                && Objects.equals(mOpenIntent, openIntent)
                && Objects.equals(mFavoritingIntent, favoritingIntent)
                && mIconOverride == iconOverride
                && Objects.equals(mIconDescription, iconDescription)
                && mAmbientSkipUnlock == skipUnlock
                && Objects.equals(mExtendedIndication, extendedIndication)) {
            return;
        }
        boolean toBeExpanded = extendedIndication != null
                && extendedIndication.expandedIndicationData != null;
        if (mIsCurrentlyInExpandedState && TextUtils.equals(mAmbientMusicText, text)
                && !toBeExpanded) {
            return;
        }
        mAmbientMusicText = text;
        mOpenIntent = openIntent;
        mFavoritingIntent = favoritingIntent;
        mAmbientSkipUnlock = skipUnlock;
        mIconOverride = iconOverride;
        mIconDescription = iconDescription;
        mExtendedIndication = extendedIndication;
        mUsingExtendedIndication = extendedIndication != null && ENABLE_EXTENDED_INTERACTION;

        Drawable override = null;
        if (!isExtendedIndicationRecognitionResult()) {
            switch (iconOverride) {
                case 1:
                    override = getContext().getDrawable(R.drawable.fundamental_ic_music_search);
                    break;
                case 3:
                    override = getContext().getDrawable(mUsingExtendedIndication
                            ? R.drawable.fundamental_ic_now_playing_music_off
                            : R.drawable.fundamental_ic_music_not_found);
                    break;
                case 4:
                    override = getContext().getDrawable(R.drawable.fundamental_ic_cloud_off);
                    break;
                case 5:
                    override = getContext().getDrawable(R.drawable.fundamental_ic_favorite);
                    break;
                case 6:
                    override = getContext().getDrawable(
                            R.drawable.fundamental_ic_favorite_border);
                    break;
                case 7:
                    override = getContext().getDrawable(R.drawable.fundamental_ic_error);
                    break;
                case 8:
                    override = getContext().getDrawable(R.drawable.fundamental_ic_favorite_note);
                    break;
                default:
                    break;
            }
        }
        mAmbientIconOverride = override;
        updatePill();
    }

    private boolean isExtendedIndicationRecognitionResult() {
        return mUsingExtendedIndication && mExtendedIndication != null
                && mExtendedIndication.isRecognitionResult();
    }

    private boolean isExtendedIndicationToBeExpanded() {
        return mUsingExtendedIndication && mExtendedIndication != null
                && mExtendedIndication.expandedIndicationData != null;
    }

    private Drawable getAmbientMusicNoteIcon() {
        if (mAmbientMusicNoteIcon == null) {
            mAmbientMusicNoteIcon = mUsingExtendedIndication
                    ? getContext().getDrawable(R.drawable.fundamental_ic_now_playing_lockscreen)
                    : getContext().getDrawable(com.android.systemui.res.R.drawable.ic_music_note);
            if (mAmbientMusicNoteIcon != null) {
                mAmbientMusicNoteIcon.mutate();
            }
        }
        return mAmbientMusicNoteIcon;
    }

    private int getPixelsFromDp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    private void updateColors() {
        if (mTextView == null || mIconView == null) {
            return;
        }
        if (mTextColorAnimator != null && mTextColorAnimator.isRunning()) {
            mTextColorAnimator.cancel();
        }
        int current = mTextView.getTextColors().getDefaultColor();
        int target = mDozing ? -1 : mTextColor;
        if (current == target) {
            mTextView.setTextColor(target);
            if (mUsingExtendedIndication) {
                mTextViewExtended.setTextColor(target);
            }
            if (!mIsCurrentlyInExpandedState) {
                mIconView.setImageTintList(ColorStateList.valueOf(target));
            }
            return;
        }
        mTextColorAnimator = ValueAnimator.ofArgb(current, target);
        mTextColorAnimator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        mTextColorAnimator.setDuration(TEXT_COLOR_ANIMATION_DURATION_MS);
        mTextColorAnimator.addUpdateListener(animator -> {
            int value = (Integer) animator.getAnimatedValue();
            mTextView.setTextColor(value);
            if (mUsingExtendedIndication) {
                mTextViewExtended.setTextColor(value);
            }
            if (!mIsCurrentlyInExpandedState) {
                mIconView.setImageTintList(ColorStateList.valueOf(value));
            }
        });
        mTextColorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTextColorAnimator = null;
            }
        });
        mTextColorAnimator.start();
    }

    /** On foldables the card takes half the inner screen; elsewhere the full width. */
    public void updateContainerWidthOnFoldableDevice(int screenWidthDp, int smallestScreenWidthDp) {
        if (!ENABLE_EXTENDED_INTERACTION || mExtendedContainer == null || mTextView == null) {
            return;
        }
        int width = smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP
                ? getPixelsFromDp((screenWidthDp / 2) - LARGE_SCREEN_SIDE_MARGIN_DP)
                : getPixelsFromDp(screenWidthDp);
        if (mInfoContainer == null) {
            mInfoContainer = findViewById(R.id.ambient_indication_info_container);
        }
        ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) mInfoContainer.getLayoutParams();
        if (width != lp.width) {
            lp.width = width;
            mInfoContainer.setLayoutParams(lp);
            mExtendedContainer.post(this::updateTextMaxWidth);
        }
    }

    /** Caps the four text views so the card keeps room for the icon + action buttons. */
    private void updateTextMaxWidth() {
        boolean constrain = mIndicationTextMode == INDICATION_TEXT_MODE_MUSIC
                && mUsingExtendedIndication;
        int maxWidth = constrain
                ? mExtendedContainer.getWidth() - getResources().getDimensionPixelSize(
                        R.dimen.ambient_indication_extended_container_non_text_width)
                : Integer.MAX_VALUE;
        TextUtils.TruncateAt ellipsize = constrain ? TextUtils.TruncateAt.END : null;
        for (TextView textView : Arrays.asList(mTextView, mTextViewExtended, mTempTextView,
                mTempTextViewExtended)) {
            textView.setMaxWidth(maxWidth);
            textView.setEllipsize(ellipsize);
        }
    }

    private void adjustTextContainerPadding() {
        int startPadding = mUsingExtendedIndication
                ? getResources().getDimensionPixelSize(mIsCurrentlyInExpandedState
                        ? R.dimen.ambient_indication_extended_text_container_start_padding_expanded
                        : R.dimen.ambient_indication_extended_text_container_start_padding_collapsed)
                : 0;
        int endPadding = (!mUsingExtendedIndication || TextUtils.isEmpty(mAmbientMusicText))
                ? 0
                : getResources().getDimensionPixelSize(
                        R.dimen.ambient_indication_extended_text_container_end_padding);
        mTextContainer.setPaddingRelative(startPadding, mTextContainer.getPaddingTop(),
                endPadding, mTextContainer.getPaddingBottom());
    }

    private static void updateContainerAccessibility(View view, boolean important,
            CharSequence contentDescription) {
        view.setImportantForAccessibility(important
                ? View.IMPORTANT_FOR_ACCESSIBILITY_YES : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setContentDescription(important ? contentDescription : null);
    }

    /** Exactly one of collapsed row / expanded card / text line announces the content. */
    private void setContentDescriptionForOuterContainer() {
        View target;
        CharSequence description;
        if (!mUsingExtendedIndication || mExtendedIndication == null) {
            target = null;
            description = null;
        } else if (mExtendedIndication.isRecognitionResult()) {
            target = mIsCurrentlyInExpandedState ? mExtendedContainer : mCollapsedContainer;
            description = getContext().getString(
                    R.string.fundamental_accessibility_now_playing_container_recognition_result_content_description,
                    mExtendedIndication.songTitle, mExtendedIndication.artistName);
        } else {
            target = mTextView;
            description = mExtendedIndication.isSongSearching()
                    ? getContext().getString(
                            R.string.fundamental_accessibility_now_playing_container_song_searching_content_description)
                    : mAmbientMusicText;
        }
        updateContainerAccessibility(mCollapsedContainer, target == mCollapsedContainer,
                description);
        updateContainerAccessibility(mExtendedContainer, target == mExtendedContainer,
                description);
        updateContainerAccessibility(mTextView, target == mTextView, description);
    }

    // ---------------------------------------------------------------------------------------
    // Expand / collapse
    // ---------------------------------------------------------------------------------------

    /** Back to the collapsed row; used when AOD starts or the user taps outside the card. */
    public void restoreToCollapsedState() {
        if (!mInflated) {
            return;
        }
        if (mIsCurrentlyInExpandedState) {
            performCollapseAnimation();
            return;
        }
        mActionContainer.setVisibility(View.GONE);
        mExtendedContainer.setBackground(null);
        mIsCurrentlyInExpandedState = false;
        adjustTextContainerPadding();
        mCurrentLoadedAlbumArtUri = null;
    }

    private void performExpandAnimation() {
        if (mAnimationState != ANIMATION_STATE_IDLE) {
            return;
        }
        mAnimationState = ANIMATION_STATE_EXPANDING;
        Trace.beginAsyncSection(TRACE_EXPAND_ANIMATION, TRACE_COOKIE_EXPAND);
        mPlayContainer.setAlpha(0f);
        mLikeContainer.setAlpha(0f);
        mPlayIcon.setAlpha(0f);
        mLikeIcon.setAlpha(0f);
        updateIcons();
        mContainerBackground.setVisibility(View.VISIBLE);
        mIsCurrentlyInExpandedState = true;
        setContentDescriptionForOuterContainer();
        Trace.beginAsyncSection(TRACE_BIND_ARTWORK, TRACE_COOKIE_BIND_ARTWORK);
        mExtendedContainer.post(this::loadArtwork);
        mActionContainer.setVisibility(View.VISIBLE);
        mExtendedContainer.post(() -> {
            if (mIsCurrentlyInExpandedState) {
                animateCollapsedContainerTranslationX();
                animateActionContainerTranslationX();
            }
        });
    }

    private void performCollapseAnimation() {
        if (mAnimationState != ANIMATION_STATE_IDLE) {
            return;
        }
        mAnimationState = ANIMATION_STATE_COLLAPSING;
        Trace.beginAsyncSection(TRACE_COLLAPSE_ANIMATION, TRACE_COOKIE_COLLAPSE);
        mIsCurrentlyInExpandedState = false;
        setContentDescriptionForOuterContainer();
        final boolean hadAlbumArt = mCurrentLoadedAlbumArtUri != null;
        mCurrentLoadedAlbumArtUri = null;
        updateColors();
        mExtendedContainer.post(() -> runCollapseAnimation(hadAlbumArt));
    }

    private void runCollapseAnimation(boolean hadAlbumArt) {
        if (mIsCurrentlyInExpandedState) {
            return;
        }
        animateCollapsedContainerTranslationX();
        animateActionContainerTranslationX();
        AmbientIndicationAnimationHelper.animateActionButtonsAlphaWithSpring(mLikeContainer,
                mLikeIcon, mActionContainer, 0f, ACTION_BUTTONS_FADE_OUT_THRESHOLD);
        AmbientIndicationAnimationHelper.animateActionButtonsAlphaWithSpring(mPlayContainer,
                mPlayIcon, mActionContainer, 0f, ACTION_BUTTONS_FADE_OUT_THRESHOLD);
        Drawable toIcon = getContext().getDrawable(
                R.drawable.fundamental_ic_now_playing_lockscreen);
        if (toIcon != null) {
            toIcon.mutate();
            AmbientIndicationAnimationHelper.animateIconAndThumbnailOnCollapse(mIconView, toIcon,
                    hadAlbumArt);
        }
        AmbientIndicationAnimationHelper.animateBackgroundArtworkInCollapse(mContainerBackground);
    }

    /** Slides the like/play pills between centre (collapsed) and end (expanded). */
    private void animateActionContainerTranslationX() {
        float translationX = ((mExtendedContainer.getWidth()
                - mExtendedContainer.getPaddingStart() - mExtendedContainer.getPaddingEnd())
                - mActionContainer.getWidth()) / 2f;
        final boolean expanded = mIsCurrentlyInExpandedState;
        final int gravity = expanded ? Gravity.END : Gravity.CENTER;
        Runnable onEnd = () -> {
            mActionContainer.setTranslationX(0f);
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) mActionContainer.getLayoutParams();
            lp.gravity = gravity | Gravity.CENTER_VERTICAL;
            mActionContainer.setLayoutParams(lp);
            if (mIsCurrentlyInExpandedState) {
                Trace.endAsyncSection(TRACE_EXPAND_ANIMATION, TRACE_COOKIE_EXPAND);
            } else {
                Trace.endAsyncSection(TRACE_COLLAPSE_ANIMATION, TRACE_COOKIE_COLLAPSE);
            }
            mAnimationState = ANIMATION_STATE_IDLE;
        };
        AmbientIndicationAnimationUtils.animateTranslationX(mActionContainer,
                (expanded ? 1 : -1) * translationX, null, onEnd,
                AmbientIndicationAnimationUtils.DEFAULT_SPATIAL_SPEC);
    }

    /** Slides icon + text between centre (collapsed) and start (expanded). */
    private void animateCollapsedContainerTranslationX() {
        int textWidth = mTextContainer.getVisibility() == View.GONE
                ? 0
                : getResources().getDimensionPixelSize(
                        R.dimen.ambient_indication_extended_text_container_start_padding_collapsed)
                        + (mTextContainer.getWidth() - mTextContainer.getPaddingStart());
        final float translationX = ((mExtendedContainer.getWidth()
                - mExtendedContainer.getPaddingStart() - mExtendedContainer.getPaddingEnd())
                - (mIconView.getWidth() + textWidth)) / 2f;
        final AtomicInteger endCounter = new AtomicInteger(0);
        final boolean expanded = mIsCurrentlyInExpandedState;
        final int gravity = expanded ? Gravity.START : Gravity.CENTER;
        final AtomicBoolean hasTriggeredActionContainerAlphaAnim = new AtomicBoolean(false);
        Runnable onEnd = () -> {
            if (endCounter.incrementAndGet() == 2) {
                mIconView.setTranslationX(0f);
                mTextContainer.setTranslationX(0f);
                mCollapsedContainer.setGravity(gravity | Gravity.CENTER_VERTICAL);
                adjustTextContainerPadding();
            }
        };
        DynamicAnimation.OnAnimationUpdateListener onUpdate =
                !expanded ? null : (animation, value, velocity) -> {
                    if (!hasTriggeredActionContainerAlphaAnim.get()
                            && Math.abs(value) / translationX >= ACTION_CONTAINER_TRIGGER_FRACTION) {
                        AmbientIndicationAnimationHelper.animateActionButtonsAlphaWithSpring(
                                mLikeContainer, mLikeIcon, mActionContainer, 1f,
                                ACTION_BUTTONS_FADE_IN_THRESHOLD);
                        AmbientIndicationAnimationHelper.animateActionButtonsAlphaWithSpring(
                                mPlayContainer, mPlayIcon, mActionContainer, 1f,
                                ACTION_BUTTONS_FADE_IN_THRESHOLD);
                        hasTriggeredActionContainerAlphaAnim.set(true);
                    }
                };
        float direction = expanded ? -1f : 1f;
        float textTranslationX = translationX - getPixelsFromDp(COLLAPSED_TEXT_SHIFT_DP);
        AmbientIndicationAnimationUtils.animateTranslationX(mIconView, direction * translationX,
                onUpdate, onEnd, AmbientIndicationAnimationUtils.DEFAULT_SPATIAL_SPEC);
        AmbientIndicationAnimationUtils.animateTranslationX(mTextContainer,
                direction * textTranslationX, null, onEnd,
                AmbientIndicationAnimationUtils.DEFAULT_SPATIAL_SPEC);
    }

    /** Like button state + the default-music-player app icon on the play button. */
    private void updateIcons() {
        ExpandedIndicationData data = mExtendedIndication.expandedIndicationData;
        mLikeContainer.setVisibility(View.VISIBLE);
        boolean isFavorite = data.isFavorite();
        mLikeIcon.setImageResource(isFavorite
                ? R.drawable.fundamental_ic_now_playing_heart_minus
                : R.drawable.fundamental_ic_now_playing_heart_plus);
        mLikeIcon.setContentDescription(getContext().getString(isFavorite
                ? R.string.fundamental_accessibility_now_playing_like_icon
                : R.string.fundamental_accessibility_now_playing_unlike_icon));
        String dmpPackageName = data.dmpPackageName;
        if (TextUtils.isEmpty(dmpPackageName) || data.dmpIntent == null) {
            mPlayContainer.setVisibility(View.GONE);
            return;
        }
        Drawable appIcon = mMusicAppIconCache.get(dmpPackageName);
        if (appIcon == null) {
            try {
                appIcon = getContext().getPackageManager().getApplicationIcon(dmpPackageName);
                if (appIcon instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) appIcon;
                    Drawable layer = adaptive.getMonochrome();
                    if (layer == null) {
                        layer = adaptive.getForeground();
                    }
                    int size = getPixelsFromDp(MUSIC_APP_ICON_SIZE_DP);
                    layer.setBounds(0, 0, size, size);
                    appIcon = layer;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Failed to get icon for music app with package name: "
                        + dmpPackageName, e);
                appIcon = null;
            }
            if (appIcon != null) {
                mMusicAppIconCache.put(dmpPackageName, appIcon);
            }
        }
        mPlayIcon.setImageDrawable(appIcon);
        mPlayContainer.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------------------------------
    // Album art
    // ---------------------------------------------------------------------------------------

    private void loadArtwork() {
        if (!mIsCurrentlyInExpandedState || mExtendedIndication == null
                || mExtendedIndication.expandedIndicationData == null) {
            return;
        }
        final int width = mExtendedContainer.getWidth();
        final int height = mExtendedContainer.getHeight();
        final Uri albumArtUri = mExtendedIndication.expandedIndicationData.albumArtUri;
        AmbientIndicationArtworkHelper.ArtworkResult cached =
                AmbientIndicationArtworkHelper.getCachedResult(albumArtUri);
        if (cached != null) {
            updateColorScheme(cached.artwork, cached.colorScheme, cached.albumArtUri,
                    cached.smallIcon);
            return;
        }
        final Context context = getContext();
        mBackgroundExecutor.execute(() -> AmbientIndicationArtworkHelper.processArtwork(context,
                mImageLoader, albumArtUri, width, height, mHandler, this::updateColorScheme));
    }

    /** Applies the artwork + derived Monet colours to the expanded card (main thread). */
    private void updateColorScheme(Drawable artwork, ColorScheme colorScheme, Uri albumArtUri,
            Drawable smallIcon) {
        if (!mInflated) {
            return;
        }
        Context context = getContext();
        DynamicScheme scheme = colorScheme != null ? colorScheme.getMaterialScheme() : null;

        int backdropColor = scheme != null
                ? scheme.secondaryPalette.tone(AmbientIndicationArtworkHelper.BACKDROP_TONE)
                : context.getColor(R.color.fundamental_now_playing_artwork_fallback_color);
        Runnable bgAnimationEnd = () -> {
            if (artwork == null) {
                Trace.endAsyncSection(TRACE_BIND_ARTWORK, TRACE_COOKIE_BIND_ARTWORK);
            }
        };
        AmbientIndicationAnimationHelper.animateBackgroundArtworkInExpand(mContainerBackground,
                backdropColor, artwork, bgAnimationEnd, mMainDelayableExecutor);

        if (smallIcon == null) {
            AmbientIndicationAnimationHelper.animateIconAndThumbnailOnExpandNoAlbumArt(mIconView,
                    context.getColor(R.color.fundamental_now_playing_icon_thumbnail_background),
                    context.getColor(com.android.internal.R.color.materialColorShadow),
                    context.getDrawable(R.drawable.fundamental_ic_now_playing_music_note));
        } else {
            AmbientIndicationAnimationHelper.animateIconAndThumbnailOnExpandWithAlbumArt(mIconView,
                    smallIcon);
        }

        MaterialDynamicColors colors = new MaterialDynamicColors();
        int containerColor = scheme != null
                ? colors.primaryFixed().getArgb(scheme)
                : context.getColor(com.android.internal.R.color.materialColorTertiaryFixedDim);
        int iconColor = scheme != null
                ? colors.onPrimaryFixed().getArgb(scheme)
                : context.getColor(com.android.internal.R.color.materialColorPrimaryContainer);
        List<View> containers = Arrays.asList(mPlayContainer, mLikeContainer);
        List<ImageView> icons = Arrays.asList(mPlayIcon, mLikeIcon);
        AmbientIndicationAnimationHelper.updateActionContainerColors(containers, icons,
                containerColor, iconColor);

        int textColor = artwork != null
                ? mTextColor
                : context.getColor(com.android.internal.R.color.materialColorSecondaryFixedDim);
        AmbientIndicationAnimationHelper.animateTextColors(
                Arrays.asList(mTextView, mTextViewExtended), textColor);

        mCurrentLoadedAlbumArtUri = albumArtUri;
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    /** Recomputes icon + text + visibility for the current state and runs the entrance. */
    public void updatePill() {
        if (mTextView == null || mIconView == null || mExtendedContainer == null) {
            return;
        }
        final int previousMode = mIndicationTextMode;
        mIndicationTextMode = INDICATION_TEXT_MODE_MUSIC;
        CharSequence text = mAmbientMusicText;
        final boolean wasVisible = mTextView.getVisibility() == View.VISIBLE;
        // ASI's icon-only prompts send a non-null but empty TEXT.
        final boolean showEmptyText = mAmbientMusicText != null && mAmbientMusicText.length() == 0;

        mTextView.setClickable(mOpenIntent != null && !isExtendedIndicationRecognitionResult());
        mIconView.setClickable((mFavoritingIntent != null || mOpenIntent != null)
                && !isExtendedIndicationRecognitionResult());

        Drawable icon = null;
        if (!TextUtils.isEmpty(text) || showEmptyText) {
            icon = mAmbientIconOverride;
            if (icon == null) {
                icon = getAmbientMusicNoteIcon();
            }
        }

        if (ENABLE_EXTENDED_INTERACTION) {
            mExtendedContainer.post(this::updateTextMaxWidth);
        }
        if (isExtendedIndicationRecognitionResult()) {
            text = mExtendedIndication.songTitle;
        }
        CharSequence artist = isExtendedIndicationRecognitionResult()
                ? mExtendedIndication.artistName : null;
        mTextView.setTypeface(mTextView.getTypeface(),
                mUsingExtendedIndication ? Typeface.BOLD : Typeface.NORMAL);
        mTempTextView.setTypeface(mTextView.getTypeface(), Typeface.BOLD);

        Drawable displayIcon = null;
        if (icon != null) {
            mIconBounds.set(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            int fit = (icon == mAmbientMusicNoteIcon)
                    ? mAmbientMusicNoteIconSize : mAmbientIndicationIconSize;
            if (mUsingExtendedIndication) {
                fit = mAmbientIndicationIconSize;
            }
            MathUtils.fitRect(mIconBounds, fit);
            displayIcon = new DrawableWrapper(icon) {
                @Override
                public int getIntrinsicHeight() {
                    return mIconBounds.height();
                }

                @Override
                public int getIntrinsicWidth() {
                    return mIconBounds.width();
                }
            };
            int endPadding = !TextUtils.isEmpty(text) ? getPixelsFromDp(TEXT_END_PADDING_DP) : 0;
            if (!mUsingExtendedIndication) {
                mTextView.setPaddingRelative(mTextView.getPaddingStart(),
                        mTextView.getPaddingTop(), endPadding, mTextView.getPaddingBottom());
            }
        } else if (!mUsingExtendedIndication) {
            mTextView.setPaddingRelative(mTextView.getPaddingStart(), mTextView.getPaddingTop(),
                    0, mTextView.getPaddingBottom());
        }
        if (!mIsCurrentlyInExpandedState) {
            mIconView.setImageDrawable(displayIcon);
        }
        if (mUsingExtendedIndication && displayIcon != null
                && mContainerBackground.getDrawable() == null) {
            displayIcon.setAlpha(255);
        }

        final boolean hasContent = !TextUtils.isEmpty(text) || showEmptyText;
        int visibility = hasContent ? View.VISIBLE : View.GONE;
        mTextView.setVisibility(visibility);
        mIconView.setVisibility(icon == null ? View.GONE : visibility);
        mWrapperContainer.setVisibility(visibility);
        mTextViewExtended.setVisibility(hasContent && isExtendedIndicationRecognitionResult()
                && !TextUtils.isEmpty(mExtendedIndication.artistName) ? View.VISIBLE : View.GONE);
        mTextContainer.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);

        // Song changed while the collapsed row is showing: slide the new title/artist in.
        if (!mIsCurrentlyInExpandedState && isExtendedIndicationRecognitionResult()
                && !isExtendedIndicationToBeExpanded() && wasVisible
                && !TextUtils.equals(mTextView.getText(), mExtendedIndication.songTitle)) {
            final CharSequence newSong = mExtendedIndication.songTitle;
            final CharSequence newArtist = mExtendedIndication.artistName;
            setContentDescriptionForOuterContainer();
            final int oldWidth = mRealTextSet.getWidth();
            mTempTextView.setText(newSong);
            mTempTextViewExtended.setText(newArtist);
            mTempTextSet.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            final int newWidth = mTempTextSet.getMeasuredWidth();
            mTempTextView.setText(null);
            mTempTextViewExtended.setText(null);
            AmbientIndicationAnimationUtils.animateTranslationX(mCollapsedContainer,
                    (oldWidth - newWidth) / 2f, null,
                    () -> mHandler.post(mWakeLock.wrap(
                            () -> runSongChangeContentSlide(newWidth, oldWidth, newSong,
                                    newArtist))),
                    AmbientIndicationAnimationUtils.DEFAULT_SPATIAL_SPEC);
            return;
        }

        // "Identifying song": looping search glyph, row nudges in from below.
        if (mUsingExtendedIndication && mExtendedIndication.isSongSearching()) {
            mIconView.setImageDrawable(
                    getContext().getDrawable(R.drawable.fundamental_avd_nowplaying_searching));
            Drawable searching = mIconView.getDrawable();
            setContentDescriptionForOuterContainer();
            if (searching instanceof AnimatedVectorDrawable) {
                final AnimatedVectorDrawable avd = (AnimatedVectorDrawable) searching;
                avd.registerAnimationCallback(new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        if (mIconView.getDrawable() == drawable) {
                            avd.start();
                        }
                    }
                });
                avd.start();
            }
            if (!mIsCurrentlyInExpandedState) {
                mTextView.setText(text);
                AmbientIndicationAnimationHelper.performSongSearchingAnimation(
                        mCollapsedContainer,
                        getResources().getDimensionPixelSize(
                                R.dimen.ambient_indication_song_searching_animation_translation_y),
                        mMainDelayableExecutor);
                return;
            }
        }

        mTextView.setText(text);
        mTextViewExtended.setText(artist);
        if (mUsingExtendedIndication && hasContent && isExtendedIndicationRecognitionResult()) {
            ExpandedIndicationData data = mExtendedIndication.expandedIndicationData;
            if (!mIsCurrentlyInExpandedState && isExtendedIndicationToBeExpanded()) {
                performExpandAnimation();
            } else if (mIsCurrentlyInExpandedState && isExtendedIndicationToBeExpanded()) {
                updateIcons();
                if (data.albumArtUri != null && (mCurrentLoadedAlbumArtUri == null
                        || !mCurrentLoadedAlbumArtUri.toString()
                                .equals(data.albumArtUri.toString()))) {
                    mMainDelayableExecutor.executeDelayed(() -> {
                        Trace.beginAsyncSection(TRACE_BIND_ARTWORK, TRACE_COOKIE_BIND_ARTWORK);
                        mExtendedContainer.post(this::loadArtwork);
                    }, ALBUM_ART_RELOAD_DELAY_MS);
                }
            } else if (!mIsCurrentlyInExpandedState || isExtendedIndicationToBeExpanded()) {
                adjustTextContainerPadding();
            } else {
                performCollapseAnimation();
            }
        } else {
            restoreToCollapsedState();
        }
        setContentDescriptionForOuterContainer();
        if (mIsCurrentlyInExpandedState) {
            return;
        }

        if (!hasContent) {
            mTextView.animate().cancel();
            if (icon instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) icon).reset();
            }
            mHandler.post(mWakeLock.wrap(() -> { }));
            return;
        }
        if (wasVisible) {
            if (previousMode == mIndicationTextMode) {
                mHandler.post(mWakeLock.wrap(() -> { }));
                return;
            }
            if (icon instanceof AnimatedVectorDrawable) {
                mWakeLock.acquire(WAKE_TAG);
                ((AnimatedVectorDrawable) icon).start();
                mWakeLock.release(WAKE_TAG);
            }
            return;
        }
        if (mUsingExtendedIndication && isExtendedIndicationRecognitionResult()) {
            AmbientIndicationAnimationHelper.performFirstRecognitionAnimation(mExtendedContainer,
                    getResources().getDimension(
                            R.dimen.ambient_indication_first_recognition_animation_translation_y));
            return;
        }
        mWakeLock.acquire(WAKE_TAG);
        if (icon instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) icon).start();
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
                        mWakeLock.release(WAKE_TAG);
                        mTextView.animate().setListener(null);
                    }
                })
                .setInterpolator(Interpolators.DECELERATE_QUINT)
                .start();
    }

    private void runSongChangeContentSlide(int newWidth, int oldWidth, CharSequence newSong,
            CharSequence newArtist) {
        if (newWidth > oldWidth) {
            mRealTextSet.getLayoutParams().width = newWidth;
            mCollapsedContainer.setTranslationX(0f);
        }
        AmbientIndicationAnimationHelper.runSongChangeContentSlide(mCollapsedContainer,
                mRealTextSet, mTextView, mTextViewExtended, mTempTextSet, mTempTextView,
                mTempTextViewExtended, newSong, newArtist,
                getResources().getDimension(
                        R.dimen.ambient_indication_song_change_animation_translation_y));
    }
}
