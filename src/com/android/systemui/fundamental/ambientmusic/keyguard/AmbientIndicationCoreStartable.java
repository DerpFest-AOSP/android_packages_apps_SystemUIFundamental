/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.keyguard;

import android.app.AlarmManager;
import android.content.Context;
import android.content.IntentFilter;
import android.os.UserHandle;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.CoreStartable;
import com.android.systemui.fundamental.ambientmusic.AmbientIndicationService;
import com.android.systemui.fundamental.ambientmusic.domain.AmbientIndicationInteractor;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;

import javax.inject.Inject;

/**
 * Registers the {@link AmbientIndicationService} broadcast receiver for all users at SysUI start
 * (there is no manifest {@code <receiver>}; ASI sends user-scoped broadcasts).
 */
public final class AmbientIndicationCoreStartable implements CoreStartable {

    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private final SelectedUserInteractor mSelectedUserInteractor;
    private final AmbientIndicationInteractor mInteractor;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    @Inject
    public AmbientIndicationCoreStartable(
            Context context,
            AlarmManager alarmManager,
            SelectedUserInteractor selectedUserInteractor,
            AmbientIndicationInteractor interactor,
            KeyguardUpdateMonitor keyguardUpdateMonitor) {
        mContext = context;
        mAlarmManager = alarmManager;
        mSelectedUserInteractor = selectedUserInteractor;
        mInteractor = interactor;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
    }

    @Override
    public void start() {
        AmbientIndicationService service = new AmbientIndicationService(
                mInteractor, mAlarmManager, mSelectedUserInteractor);
        IntentFilter filter = new IntentFilter();
        filter.addAction(AmbientIndicationService.ACTION_SHOW);
        filter.addAction(AmbientIndicationService.ACTION_EXPAND);
        filter.addAction(AmbientIndicationService.ACTION_HIDE);
        filter.addAction(AmbientIndicationService.ACTION_UPDATE_QUICK_AFFORDANCE_STATE);
        mContext.registerReceiverAsUser(service, UserHandle.ALL, filter,
                AmbientIndicationService.PERMISSION, null, Context.RECEIVER_EXPORTED);
        mKeyguardUpdateMonitor.registerCallback(service.getCallback());
    }
}
