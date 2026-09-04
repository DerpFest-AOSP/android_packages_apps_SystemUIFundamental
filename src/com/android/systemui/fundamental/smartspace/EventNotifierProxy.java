/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Ported from Google SystemUI com.google.android.systemui.smartspace.EventNotifierProxy.
 */
package com.android.systemui.fundamental.smartspace;

import android.app.smartspace.SmartspaceTargetEvent;

import com.android.systemui.plugins.BcSmartspaceDataPlugin;

/**
 * Routes {@link SmartspaceTargetEvent}s to whatever dispatcher the controller has installed, and
 * holds the {@link BcSmartspaceDataPlugin.IntentStarter} the views use to launch taps on the
 * lockscreen. Mirrors the Google implementation 1:1 (fields cleaned up from decompiled form).
 */
public final class EventNotifierProxy implements BcSmartspaceDataPlugin.SmartspaceEventNotifier {

    private volatile BcSmartspaceDataPlugin.SmartspaceEventDispatcher mEventDispatcher;
    private volatile BcSmartspaceDataPlugin.IntentStarter mIntentStarter;

    void setEventDispatcher(BcSmartspaceDataPlugin.SmartspaceEventDispatcher eventDispatcher) {
        mEventDispatcher = eventDispatcher;
    }

    void setIntentStarter(BcSmartspaceDataPlugin.IntentStarter intentStarter) {
        mIntentStarter = intentStarter;
    }

    @Override
    public BcSmartspaceDataPlugin.IntentStarter getIntentStarter() {
        return mIntentStarter;
    }

    @Override
    public void notifySmartspaceEvent(SmartspaceTargetEvent event) {
        BcSmartspaceDataPlugin.SmartspaceEventDispatcher dispatcher = mEventDispatcher;
        if (dispatcher != null) {
            dispatcher.notifySmartspaceEvent(event);
        }
    }
}
