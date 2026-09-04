/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Lean stand-in for the Google carousel view
 * com.google.android.systemui.smartspace.BcSmartspaceView. The Google view is a ViewPager2/
 * RecyclerView carousel (BcSmartspaceView + CardPagerAdapter + CardRecyclerViewAdapter +
 * ~15 BcSmartspaceCard* subclasses + uitemplate/* + logging/* + generated proto/statslog) and
 * cannot be assembled from the available refs. This implementation is a single-card
 * {@link BcSmartspaceDataPlugin.SmartspaceView} that renders the primary target's header text and
 * launches its tap action, which is enough to satisfy the keyguard SmartspaceSection general view
 * (and KeyguardUnlockAnimationController, which reads getSelectedPage()/getCurrentCardTopPadding()).
 */
package com.android.systemui.fundamental.smartspace;

import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceTarget;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.plugins.BcSmartspaceConfigPlugin;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.plugins.FalsingManager;

import java.util.List;

public class BcSmartspaceView extends LinearLayout
        implements BcSmartspaceDataPlugin.SmartspaceView,
                BcSmartspaceDataPlugin.SmartspaceTargetListener {

    private final TextView mTitleView;

    private BcSmartspaceDataPlugin mDataProvider;
    private String mUiSurface;
    private float mDozeAmount;
    private int mPrimaryTextColor = Color.WHITE;

    public BcSmartspaceView(Context context) {
        this(context, null);
    }

    public BcSmartspaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        mTitleView = new TextView(context);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        mTitleView.setSingleLine(true);
        mTitleView.setEllipsize(TextUtils.TruncateAt.END);
        mTitleView.setTextColor(mPrimaryTextColor);
        addView(mTitleView,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDataProvider != null) {
            mDataProvider.registerListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDataProvider != null) {
            mDataProvider.unregisterListener(this);
        }
    }

    @Override
    public void onSmartspaceTargetsUpdated(List<? extends Parcelable> targets) {
        SmartspaceTarget target = null;
        for (Parcelable p : targets) {
            if (p instanceof SmartspaceTarget) {
                target = (SmartspaceTarget) p;
                break;
            }
        }
        final SmartspaceAction header = (target != null) ? target.getHeaderAction() : null;
        final CharSequence title = (header != null) ? header.getTitle() : null;
        if (TextUtils.isEmpty(title)) {
            setVisibility(GONE);
            setOnClickListener(null);
            return;
        }
        mTitleView.setText(title);
        mTitleView.setContentDescription(
                header.getContentDescription() != null ? header.getContentDescription() : title);
        setVisibility(VISIBLE);
        setOnClickListener(v -> launch(v, header));
    }

    private void launch(View v, SmartspaceAction action) {
        if (mDataProvider == null) {
            return;
        }
        BcSmartspaceDataPlugin.SmartspaceEventNotifier notifier = mDataProvider.getEventNotifier();
        BcSmartspaceDataPlugin.IntentStarter starter =
                (notifier != null) ? notifier.getIntentStarter() : null;
        if (starter != null) {
            starter.startFromAction(action, v, /* showOnLockscreen= */ false);
        }
    }

    @Override
    public void registerDataProvider(BcSmartspaceDataPlugin plugin) {
        mDataProvider = plugin;
    }

    @Override
    public void registerConfigProvider(BcSmartspaceConfigPlugin configProvider) {
        // Carousel/ViewPager2 toggles do not apply to this single-card view.
    }

    @Override
    public void setPrimaryTextColor(int color) {
        mPrimaryTextColor = color;
        mTitleView.setTextColor(color);
    }

    @Override
    public void setUiSurface(String uiSurface) {
        mUiSurface = uiSurface;
    }

    @Override
    public void setBgHandler(Handler bgHandler) {
        // No binder calls are made off this view.
    }

    @Override
    public void setDozeAmount(float amount) {
        mDozeAmount = amount;
    }

    @Override
    public void setFalsingManager(FalsingManager falsingManager) {
        // Taps are routed through the plugin's IntentStarter, which the controller guards.
    }

    @Override
    public int getSelectedPage() {
        return 0;
    }

    @Override
    public int getCurrentCardTopPadding() {
        return 0;
    }
}
