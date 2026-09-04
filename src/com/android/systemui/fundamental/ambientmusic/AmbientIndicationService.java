/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.fundamental.ambientmusic.domain.AmbientIndicationInteractor;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusicStatus;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;

/**
 * Runtime {@link BroadcastReceiver} for the ASI ambient-indication protocol. Registered for all
 * users by {@link com.android.systemui.fundamental.ambientmusic.keyguard.AmbientIndicationCoreStartable}
 * (there is intentionally no manifest {@code <receiver>}).
 *
 * <p>v1 handles SHOW / HIDE / UPDATE_QUICK_AFFORDANCE_STATE only; the expanded-card action
 * (AMBIENT_INDICATION_EXPAND) is intentionally dropped.
 */
public final class AmbientIndicationService extends BroadcastReceiver {

    private static final String TAG = "AmbientIndication";

    public static final String PERMISSION =
            "com.google.android.ambientindication.permission.AMBIENT_INDICATION";

    public static final String ACTION_SHOW =
            "com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW";
    public static final String ACTION_HIDE =
            "com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE";
    public static final String ACTION_UPDATE_QUICK_AFFORDANCE_STATE =
            "com.google.android.ambientindication.action.UPDATE_QUICK_AFFORDANCE_STATE";

    private static final String EXTRA_VERSION =
            "com.google.android.ambientindication.extra.VERSION";
    private static final String EXTRA_TEXT =
            "com.google.android.ambientindication.extra.TEXT";
    private static final String EXTRA_OPEN_INTENT =
            "com.google.android.ambientindication.extra.OPEN_INTENT";
    private static final String EXTRA_FAVORITING_INTENT =
            "com.google.android.ambientindication.extra.FAVORITING_INTENT";
    private static final String EXTRA_TTL_MILLIS =
            "com.google.android.ambientindication.extra.TTL_MILLIS";
    private static final String EXTRA_SKIP_UNLOCK =
            "com.google.android.ambientindication.extra.SKIP_UNLOCK";
    private static final String EXTRA_ICON_OVERRIDE =
            "com.google.android.ambientindication.extra.ICON_OVERRIDE";
    private static final String EXTRA_ICON_DESCRIPTION =
            "com.google.android.ambientindication.extra.ICON_DESCRIPTION";
    private static final String EXTRA_IS_ENABLED =
            "com.google.android.ambientindication.extra.IS_ENABLED";
    private static final String EXTRA_IS_ACTIVE =
            "com.google.android.ambientindication.extra.IS_ACTIVE";

    private static final long MAX_TTL_MILLIS = 180000L;

    private final AmbientIndicationInteractor mInteractor;
    private final AlarmManager mAlarmManager;
    private final SelectedUserInteractor mSelectedUserInteractor;

    private final AlarmManager.OnAlarmListener mHideIndicationListener =
            new AlarmManager.OnAlarmListener() {
                @Override
                public void onAlarm() {
                    mInteractor.hideAmbientMusic();
                }
            };

    private final KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onUserSwitchComplete(int userId) {
            onUserSwitched();
        }
    };

    public AmbientIndicationService(
            AmbientIndicationInteractor interactor,
            AlarmManager alarmManager,
            SelectedUserInteractor selectedUserInteractor) {
        mInteractor = interactor;
        mAlarmManager = alarmManager;
        mSelectedUserInteractor = selectedUserInteractor;
    }

    public KeyguardUpdateMonitorCallback getCallback() {
        return mCallback;
    }

    private int getCurrentUser() {
        return mSelectedUserInteractor.getSelectedUserId();
    }

    private boolean isForCurrentUser() {
        return getSendingUserId() == getCurrentUser() || getSendingUserId() == -1;
    }

    private void onUserSwitched() {
        mInteractor.hideAmbientMusic();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isForCurrentUser()) {
            Log.i(TAG, "Suppressing ambient, not for this user.");
            return;
        }
        int version = intent.getIntExtra(EXTRA_VERSION, 0);
        if (version != 1) {
            Log.e(TAG, "Expected EXTRA_VERSION 1 but received " + version + ", dropping intent.");
            return;
        }
        final String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case ACTION_HIDE:
                mAlarmManager.cancel(mHideIndicationListener);
                mInteractor.hideAmbientMusic();
                Log.i(TAG, "Hiding ambient indication.");
                break;
            case ACTION_SHOW: {
                long ttl = Math.min(
                        Math.max(intent.getLongExtra(EXTRA_TTL_MILLIS, MAX_TTL_MILLIS), 0L),
                        MAX_TTL_MILLIS);
                boolean skipUnlock = intent.getBooleanExtra(EXTRA_SKIP_UNLOCK, false);
                int iconOverride = intent.getIntExtra(EXTRA_ICON_OVERRIDE, 0);
                String iconDescription = intent.getStringExtra(EXTRA_ICON_DESCRIPTION);
                CharSequence text = intent.getCharSequenceExtra(EXTRA_TEXT);
                PendingIntent openIntent =
                        intent.getParcelableExtra(EXTRA_OPEN_INTENT, PendingIntent.class);
                PendingIntent favoritingIntent =
                        intent.getParcelableExtra(EXTRA_FAVORITING_INTENT, PendingIntent.class);
                mInteractor.setAmbientMusic(text, openIntent, favoritingIntent,
                        Integer.valueOf(iconOverride), Boolean.valueOf(skipUnlock),
                        iconDescription);
                mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + ttl, TAG, mHideIndicationListener, null);
                Log.i(TAG, "Showing ambient indication.");
                break;
            }
            case ACTION_UPDATE_QUICK_AFFORDANCE_STATE: {
                boolean isEnabled = intent.getBooleanExtra(EXTRA_IS_ENABLED, false);
                boolean isActive = intent.getBooleanExtra(EXTRA_IS_ACTIVE, false);
                Log.d(TAG, "UPDATE_QUICK_AFFORDANCE_STATE: isEnabled=" + isEnabled
                        + ", isActive=" + isActive);
                mInteractor.getRepository().setAmbientMusicStatus(
                        new AmbientIndicationMusicStatus(isEnabled, isActive));
                break;
            }
            default:
                break;
        }
    }
}
