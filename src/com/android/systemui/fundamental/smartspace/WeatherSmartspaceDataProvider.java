/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Ported from Google SystemUI
 * com.google.android.systemui.smartspace.WeatherSmartspaceDataProvider. Plugin plumbing is a
 * faithful port (takes unfiltered targets, keeps only FEATURE_WEATHER, fans out to listeners);
 * getView() / getLargeClockView() return a lean {@link WeatherSmartspaceView} instead of inflating
 * weather(_large).xml.
 */
package com.android.systemui.fundamental.smartspace;

import android.app.smartspace.SmartspaceTarget;
import android.content.Context;

import com.android.systemui.plugins.BcSmartspaceDataPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Standalone weather lockscreen smartspace plugin. */
public final class WeatherSmartspaceDataProvider implements BcSmartspaceDataPlugin {

    private final Set<SmartspaceTargetListener> mSmartspaceTargetListeners = new HashSet<>();
    private final List<SmartspaceTarget> mSmartspaceTargets = new ArrayList<>();
    private final EventNotifierProxy mEventNotifier = new EventNotifierProxy();

    @Override
    public SmartspaceEventNotifier getEventNotifier() {
        return mEventNotifier;
    }

    @Override
    public SmartspaceView getView(Context context) {
        WeatherSmartspaceView view = new WeatherSmartspaceView(context);
        view.setId(com.android.systemui.shared.R.id.weather_smartspace_view);
        return view;
    }

    @Override
    public SmartspaceView getLargeClockView(Context context) {
        WeatherSmartspaceView view = new WeatherSmartspaceView(context);
        view.setId(com.android.systemui.shared.R.id.weather_smartspace_view_large);
        return view;
    }

    @Override
    public void onTargetsAvailable(List<SmartspaceTarget> targets) {
        mSmartspaceTargets.clear();
        for (SmartspaceTarget target : targets) {
            if (target != null && target.getFeatureType() == SmartspaceTarget.FEATURE_WEATHER) {
                mSmartspaceTargets.add(target);
            }
        }
        for (SmartspaceTargetListener listener : mSmartspaceTargetListeners) {
            listener.onSmartspaceTargetsUpdated(mSmartspaceTargets);
        }
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
