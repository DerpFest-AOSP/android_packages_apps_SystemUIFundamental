/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Ported from Google SystemUI com.google.android.systemui.smartspace.BcSmartspaceDataProvider.
 * The plugin plumbing (listener/target fan-out, event notifier, attach-listener tracking) is a
 * faithful port; getView() returns a lean {@link BcSmartspaceView} instead of inflating the
 * Google carousel layout (smartspace_enhanced -> BcSmartspaceView + CardPagerAdapter + card
 * subsystem), which depends on generated code that cannot be assembled from the refs. See the
 * feature blockers for the exact missing pieces.
 */
package com.android.systemui.fundamental.smartspace;

import android.app.smartspace.SmartspaceTarget;
import android.content.Context;
import android.view.View;

import com.android.systemui.plugins.BcSmartspaceConfigPlugin;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Main ("general") lockscreen smartspace data plugin. */
public final class BcSmartspaceDataProvider implements BcSmartspaceDataPlugin {

    private final Set<SmartspaceTargetListener> mSmartspaceTargetListeners =
            new CopyOnWriteArraySet<>();
    private final Set<View> mViews = new HashSet<>();
    private final Set<View.OnAttachStateChangeListener> mAttachListeners = new HashSet<>();
    private final EventNotifierProxy mEventNotifier = new EventNotifierProxy();

    private List<SmartspaceTarget> mSmartspaceTargets = Collections.emptyList();
    private BcSmartspaceConfigPlugin mConfigProvider;

    private final View.OnAttachStateChangeListener mStateChangeListener =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    mViews.add(v);
                    for (View.OnAttachStateChangeListener l : mAttachListeners) {
                        l.onViewAttachedToWindow(v);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    mViews.remove(v);
                    for (View.OnAttachStateChangeListener l : mAttachListeners) {
                        l.onViewDetachedFromWindow(v);
                    }
                }
            };

    @Override
    public void addOnAttachStateChangeListener(View.OnAttachStateChangeListener listener) {
        mAttachListeners.add(listener);
        for (View v : mViews) {
            listener.onViewAttachedToWindow(v);
        }
    }

    @Override
    public SmartspaceEventNotifier getEventNotifier() {
        return mEventNotifier;
    }

    @Override
    public SmartspaceView getView(Context context) {
        BcSmartspaceView view = new BcSmartspaceView(context);
        // The keyguard SmartspaceSection positions the general card by this shared id.
        view.setId(com.android.systemui.shared.R.id.bc_smartspace_view);
        view.addOnAttachStateChangeListener(mStateChangeListener);
        return view;
    }

    @Override
    public void onTargetsAvailable(List<SmartspaceTarget> targets) {
        List<SmartspaceTarget> filtered = new ArrayList<>();
        for (SmartspaceTarget target : targets) {
            if (target != null && target.getFeatureType() != SmartspaceTarget.FEATURE_WEATHER) {
                filtered.add(target);
            }
        }
        mSmartspaceTargets = filtered;
        for (SmartspaceTargetListener listener : mSmartspaceTargetListeners) {
            listener.onSmartspaceTargetsUpdated(mSmartspaceTargets);
        }
    }

    @Override
    public void registerConfigProvider(BcSmartspaceConfigPlugin configProvider) {
        mConfigProvider = configProvider;
    }

    @Override
    public void registerListener(SmartspaceTargetListener listener) {
        mSmartspaceTargetListeners.add(listener);
        listener.onSmartspaceTargetsUpdated(mSmartspaceTargets);
    }

    @Override
    public void unregisterListener(SmartspaceTargetListener listener) {
        mSmartspaceTargetListeners.remove(listener);
    }

    @Override
    public void setEventDispatcher(SmartspaceEventDispatcher eventDispatcher) {
        mEventNotifier.setEventDispatcher(eventDispatcher);
    }

    @Override
    public void setIntentStarter(IntentStarter intentStarter) {
        mEventNotifier.setIntentStarter(intentStarter);
    }
}
