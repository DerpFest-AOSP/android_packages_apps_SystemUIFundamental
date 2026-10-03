/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.quickaffordance;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.android.systemui.animation.Expandable;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.common.shared.model.ContentDescription;
import com.android.systemui.common.shared.model.Icon;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.fundamental.ambientmusic.AmbientIndicationFlows;
import com.android.systemui.fundamental.ambientmusic.AmbientIndicationService;
import com.android.systemui.fundamental.ambientmusic.data.AmbientIndicationRepository;
import com.android.systemui.fundamental.ambientmusic.nonpixel.NonPixelAmbientIndicationService;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusicStatus;
import com.android.systemui.keyguard.data.quickaffordance.KeyguardQuickAffordanceConfig;
import com.android.systemui.keyguard.shared.quickaffordance.ActivationState;
import com.android.systemui.R;

import java.util.UUID;

import javax.inject.Inject;

import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

/**
 * Lock-screen "search a song" quick affordance. On a Pixel with Android System Intelligence it
 * fires ASI's on-demand recognition broadcast. Everywhere else it asks the in-tree recognizer.
 * The button stays visible; its active/inactive highlight follows ASI's
 * UPDATE_QUICK_AFFORDANCE_STATE broadcasts when that service is the one running.
 */
public final class NowPlayingQuickAffordanceConfig implements KeyguardQuickAffordanceConfig {

    private static final String KEY = "now_playing";

    private static final String ACTION_AQA_CLICK =
            "com.google.intelligence.sense.ambientmusic.ondemand.AQA_CLICK";
    private static final String ACTION_AQA_GET_STATUS =
            "com.google.intelligence.sense.ambientmusic.ondemand.AQA_GET_STATUS";
    private static final String AS_PACKAGE = "com.google.android.as";
    private static final String AS_RECEIVER =
            "com.google.intelligence.sense.ondemand.SystemUiBroadcastReceiver";
    private static final String EXTRA_ON_DEMAND_TIMESTAMP = "EXTRA_ON_DEMAND_TIMESTAMP";
    private static final String EXTRA_ON_DEMAND_SESSION = "EXTRA_ON_DEMAND_SESSION";

    private final Context mContext;
    private final BroadcastSender mBroadcastSender;
    private final Flow mLockScreenState;

    @Inject
    @SuppressWarnings({"unchecked", "rawtypes"})
    public NowPlayingQuickAffordanceConfig(
            @Application Context context,
            BroadcastSender broadcastSender,
            AmbientIndicationRepository repository) {
        mContext = context;
        mBroadcastSender = broadcastSender;
        mLockScreenState = AmbientIndicationFlows.map(
                AmbientIndicationFlows.onStart(repository.ambientMusicStatus, this::requestStatus),
                status -> new KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                        new Icon.Resource(R.drawable.fundamental_ic_now_playing_lockscreen,
                                new ContentDescription.Resource(R.string.now_playing_label)),
                        ((AmbientIndicationMusicStatus) status).isActive
                                ? ActivationState.Active.INSTANCE
                                : ActivationState.Inactive.INSTANCE));
    }

    /** Asks ASI to (re)broadcast UPDATE_QUICK_AFFORDANCE_STATE. */
    private void requestStatus() {
        Intent intent = new Intent(ACTION_AQA_GET_STATUS);
        intent.setComponent(new ComponentName(AS_PACKAGE, AS_RECEIVER));
        mBroadcastSender.sendBroadcast(intent, AmbientIndicationService.PERMISSION);
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
        if (usesGoogleAmbient()) {
            Intent intent = new Intent(ACTION_AQA_CLICK);
            intent.setComponent(new ComponentName(AS_PACKAGE, AS_RECEIVER));
            intent.putExtra(EXTRA_ON_DEMAND_TIMESTAMP, System.currentTimeMillis());
            intent.putExtra(EXTRA_ON_DEMAND_SESSION, UUID.randomUUID().toString());
            mBroadcastSender.sendBroadcast(intent, AmbientIndicationService.PERMISSION);
        } else {
            Intent local = new Intent(NonPixelAmbientIndicationService.ACTION_LOCAL_ON_DEMAND_SEARCH);
            local.setPackage(mContext.getPackageName());
            mBroadcastSender.sendBroadcast(local);
        }
        return new KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(true);
    }

    /** Pixels with Android System Intelligence use the stock on-demand broadcast. */
    private boolean usesGoogleAmbient() {
        boolean isPixel = "google".equalsIgnoreCase(Build.BRAND)
                || "google".equalsIgnoreCase(Build.MANUFACTURER);
        if (!isPixel) {
            return false;
        }
        try {
            mContext.getPackageManager().getPackageInfo(AS_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
