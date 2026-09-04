/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.binder;

import android.view.ViewGroup;

import com.android.systemui.fundamental.ambientmusic.AmbientIndicationContainer;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusic;
import com.android.systemui.fundamental.ambientmusic.ui.viewmodel.KeyguardAmbientIndicationViewModel;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.util.kotlin.JavaAdapterKt;
import com.android.systemui.util.wakelock.DelayedWakeLock;

import java.util.function.Consumer;

import kotlinx.coroutines.DisposableHandle;
import kotlinx.coroutines.flow.Flow;

/**
 * Binds the {@link AmbientIndicationContainer} to the passive-music flow and the keyguard doze
 * flow, collecting each on the view's attached lifecycle via {@code JavaAdapter.collectFlow}.
 *
 * <p>The flows are handled as raw {@link Flow} so the erased {@code Consumer<Object>} collectors
 * type-check against the covariant Kotlin flows.
 */
public final class KeyguardAmbientIndicationAreaViewBinder {

    private KeyguardAmbientIndicationAreaViewBinder() {
    }

    /**
     * @return a {@link DisposableHandle} that tears down both collectors, or {@code null} if the
     *     container view was not present.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DisposableHandle bind(
            ViewGroup rootView,
            KeyguardAmbientIndicationViewModel viewModel,
            PowerInteractor powerInteractor,
            ActivityStarter activityStarter,
            DelayedWakeLock.Factory wakeLockFactory,
            KeyguardInteractor keyguardInteractor) {
        final AmbientIndicationContainer container =
                rootView.findViewById(
                        com.android.systemui.res.R.id.ambient_indication_container);
        if (container == null) {
            return null;
        }
        container.mPowerInteractor = powerInteractor;
        container.mActivityStarter = activityStarter;
        container.mDelayedWakeLockFactory = wakeLockFactory;
        container.mWakeLock = container.createWakeLock();
        container.initializeView();

        final Flow musicFlow = viewModel.getAmbientMusicState();
        final DisposableHandle musicHandle = JavaAdapterKt.collectFlow(
                container,
                musicFlow,
                (Consumer<Object>) value -> {
                    AmbientIndicationMusic music = (AmbientIndicationMusic) value;
                    if (music == null) {
                        container.setAmbientMusic(null, null, null, 0, false, null);
                    } else {
                        container.setAmbientMusic(
                                music.text,
                                music.openIntent,
                                music.favoritingIntent,
                                music.iconOverride != null ? music.iconOverride : 0,
                                Boolean.TRUE.equals(music.skipUnlock),
                                music.iconDescription);
                    }
                });

        final Flow dozeFlow = keyguardInteractor.isDozing();
        final DisposableHandle dozeHandle = JavaAdapterKt.collectFlow(
                container,
                dozeFlow,
                (Consumer<Object>) dozing -> container.setDozing(Boolean.TRUE.equals(dozing)));

        return new DisposableHandle() {
            @Override
            public void dispose() {
                musicHandle.dispose();
                dozeHandle.dispose();
            }
        };
    }
}
