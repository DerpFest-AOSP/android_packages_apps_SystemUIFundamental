/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.quickaffordance;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.systemui.animation.Expandable;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.common.shared.model.Icon;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.fundamental.ambientmusic.AmbientIndicationService;
import com.android.systemui.keyguard.data.quickaffordance.KeyguardQuickAffordanceConfig;
import com.android.systemui.keyguard.shared.quickaffordance.ActivationState;
import com.android.systemui.R;

import java.util.UUID;

import javax.inject.Inject;

import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;

/**
 * Lock-screen "search a song" quick affordance. When triggered it fires ASI's on-demand
 * recognition broadcast. The lock-screen button is always available (v1 does not gate on the
 * ASI-reported enable state; see feature blockers).
 */
public final class NowPlayingQuickAffordanceConfig implements KeyguardQuickAffordanceConfig {

    private static final String KEY = "now_playing";

    private static final String ACTION_AQA_CLICK =
            "com.google.intelligence.sense.ambientmusic.ondemand.AQA_CLICK";
    private static final String AS_PACKAGE = "com.google.android.as";
    private static final String AS_RECEIVER =
            "com.google.intelligence.sense.ondemand.SystemUiBroadcastReceiver";
    private static final String EXTRA_ON_DEMAND_TIMESTAMP = "EXTRA_ON_DEMAND_TIMESTAMP";
    private static final String EXTRA_ON_DEMAND_SESSION = "EXTRA_ON_DEMAND_SESSION";

    private final Context mContext;
    private final BroadcastSender mBroadcastSender;
    private final Flow mLockScreenState;

    @Inject
    public NowPlayingQuickAffordanceConfig(
            @Application Context context,
            BroadcastSender broadcastSender) {
        mContext = context;
        mBroadcastSender = broadcastSender;
        mLockScreenState = FlowKt.flowOf(new KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                new Icon.Resource(R.drawable.fundamental_ic_now_playing_lockscreen, null),
                ActivationState.NotSupported.INSTANCE));
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public int getPickerIconResourceId() {
        return R.drawable.fundamental_ic_now_playing_lockscreen;
    }

    @Override
    public Flow getLockScreenState() {
        return mLockScreenState;
    }

    @Override
    public String pickerName() {
        return mContext.getString(R.string.now_playing_label);
    }

    @Override
    public Object getPickerScreenState(Continuation continuation) {
        return new KeyguardQuickAffordanceConfig.PickerScreenState.Default(null);
    }

    @Override
    public KeyguardQuickAffordanceConfig.OnTriggeredResult onTriggered(Expandable expandable) {
        Intent intent = new Intent(ACTION_AQA_CLICK);
        intent.setComponent(new ComponentName(AS_PACKAGE, AS_RECEIVER));
        intent.putExtra(EXTRA_ON_DEMAND_TIMESTAMP, System.currentTimeMillis());
        intent.putExtra(EXTRA_ON_DEMAND_SESSION, UUID.randomUUID().toString());
        mBroadcastSender.sendBroadcast(intent, AmbientIndicationService.PERMISSION);
        return new KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(true);
    }
}
