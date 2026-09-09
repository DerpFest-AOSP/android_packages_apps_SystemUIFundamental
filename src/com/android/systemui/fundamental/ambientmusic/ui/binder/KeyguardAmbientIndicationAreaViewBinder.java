/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.binder;

import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.ViewGroup;

import com.android.systemui.biometrics.AuthController;
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor;
import com.android.systemui.fundamental.ambientmusic.AmbientIndicationContainer;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusic;
import com.android.systemui.fundamental.ambientmusic.ui.viewmodel.KeyguardAmbientIndicationViewModel;
import com.android.systemui.graphics.ImageLoader;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.shared.model.DozeStateModel;
import com.android.systemui.keyguard.shared.model.DozeTransitionModel;
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel;
import com.android.systemui.media.NotificationMediaManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.kotlin.JavaAdapterKt;
import com.android.systemui.util.wakelock.DelayedWakeLock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import kotlinx.coroutines.DisposableHandle;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;

/**
 * Binds the {@link AmbientIndicationContainer} to the Now Playing flows, collecting each on the
 * view's attached lifecycle via {@code JavaAdapter.collectFlow} (the A17 stock binder does the
 * same set with {@code repeatWhenAttached}):
 *
 * <ul>
 *   <li>burn-in translation X / Y for AOD;</li>
 *   <li>the ambient-music state (passive line or extended row / expanded card);</li>
 *   <li>doze transition INITIALIZED -> DOZE_AOD collapses the card;</li>
 *   <li>configuration changes resize the card on foldables;</li>
 *   <li>a tap anywhere outside the card collapses it.</li>
 * </ul>
 *
 * <p>The flows are handled as raw {@link Flow} so the erased {@code Consumer<Object>} collectors
 * type-check against the covariant Kotlin flows.
 */
public final class KeyguardAmbientIndicationAreaViewBinder {

    private KeyguardAmbientIndicationAreaViewBinder() {
    }

    /** Everything the container needs that the inflater cannot inject. */
    public static final class Dependencies {
        public final PowerInteractor powerInteractor;
        public final ActivityStarter activityStarter;
        public final DelayedWakeLock.Factory wakeLockFactory;
        public final KeyguardInteractor keyguardInteractor;
        public final ConfigurationInteractor configurationInteractor;
        public final ImageLoader imageLoader;
        public final KeyguardRootViewModel keyguardRootViewModel;
        public final FalsingManager falsingManager;
        public final DelayableExecutor mainDelayableExecutor;
        public final Executor backgroundExecutor;
        public final NotificationMediaManager notificationMediaManager;
        public final StatusBarStateController statusBarStateController;
        public final AuthController authController;

        public Dependencies(
                PowerInteractor powerInteractor,
                ActivityStarter activityStarter,
                DelayedWakeLock.Factory wakeLockFactory,
                KeyguardInteractor keyguardInteractor,
                ConfigurationInteractor configurationInteractor,
                ImageLoader imageLoader,
                KeyguardRootViewModel keyguardRootViewModel,
                FalsingManager falsingManager,
                DelayableExecutor mainDelayableExecutor,
                Executor backgroundExecutor,
                NotificationMediaManager notificationMediaManager,
                StatusBarStateController statusBarStateController,
                AuthController authController) {
            this.powerInteractor = powerInteractor;
            this.activityStarter = activityStarter;
            this.wakeLockFactory = wakeLockFactory;
            this.keyguardInteractor = keyguardInteractor;
            this.configurationInteractor = configurationInteractor;
            this.imageLoader = imageLoader;
            this.keyguardRootViewModel = keyguardRootViewModel;
            this.falsingManager = falsingManager;
            this.mainDelayableExecutor = mainDelayableExecutor;
            this.backgroundExecutor = backgroundExecutor;
            this.notificationMediaManager = notificationMediaManager;
            this.statusBarStateController = statusBarStateController;
            this.authController = authController;
        }
    }

    /**
     * @return a {@link DisposableHandle} that tears down every collector, or {@code null} if the
     *     container view was not present.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DisposableHandle bind(
            ViewGroup rootView,
            KeyguardAmbientIndicationViewModel viewModel,
            Dependencies deps) {
        final AmbientIndicationContainer container =
                rootView.findViewById(
                        com.android.systemui.res.R.id.ambient_indication_container);
        if (container == null) {
            return null;
        }
        container.mPowerInteractor = deps.powerInteractor;
        container.mActivityStarter = deps.activityStarter;
        container.mDelayedWakeLockFactory = deps.wakeLockFactory;
        container.mFalsingManager = deps.falsingManager;
        container.mMainDelayableExecutor = deps.mainDelayableExecutor;
        container.mBackgroundExecutor = deps.backgroundExecutor;
        container.mImageLoader = deps.imageLoader;
        container.mNotificationMediaManager = deps.notificationMediaManager;
        container.mStatusBarStateController = deps.statusBarStateController;
        container.mUdfpsSupported = deps.authController.isUdfpsSupported();
        container.mWakeLock = container.createWakeLock();
        container.initializeView();
        container.registerListeners();

        final List<DisposableHandle> handles = new ArrayList<>();

        final Flow translationXFlow = viewModel.getIndicationAreaTranslationX();
        handles.add(JavaAdapterKt.collectFlow(container, translationXFlow,
                (Consumer<Object>) value -> container.setTranslationX(((Number) value).floatValue())));

        final Flow translationYFlow = viewModel.getIndicationAreaTranslationY();
        handles.add(JavaAdapterKt.collectFlow(container, translationYFlow,
                (Consumer<Object>) value -> container.setTranslationY(((Number) value).floatValue())));

        final Flow musicFlow = viewModel.getAmbientMusicState();
        handles.add(JavaAdapterKt.collectFlow(container, musicFlow, (Consumer<Object>) value -> {
            AmbientIndicationMusic music = (AmbientIndicationMusic) value;
            if (music == null) {
                container.setAmbientMusic(null, null, null, 0, false, null, null);
            } else {
                container.setAmbientMusic(
                        music.text,
                        music.openIntent,
                        music.favoritingIntent,
                        music.iconOverride != null ? music.iconOverride : 0,
                        Boolean.TRUE.equals(music.skipUnlock),
                        music.iconDescription,
                        music.extendedIndication);
            }
        }));

        final Flow dozeTransitionFlow = deps.keyguardInteractor.getDozeTransitionModel();
        handles.add(JavaAdapterKt.collectFlow(container, dozeTransitionFlow,
                (Consumer<Object>) value -> {
                    DozeTransitionModel model = (DozeTransitionModel) value;
                    if (model.getFrom() == DozeStateModel.INITIALIZED
                            && model.getTo() == DozeStateModel.DOZE_AOD) {
                        container.restoreToCollapsedState();
                    }
                }));

        final Flow configurationFlow = deps.configurationInteractor.getConfigurationValues();
        handles.add(JavaAdapterKt.collectFlow(container, configurationFlow,
                (Consumer<Object>) value -> {
                    Configuration configuration = (Configuration) value;
                    container.updateContainerWidthOnFoldableDevice(configuration.screenWidthDp,
                            configuration.smallestScreenWidthDp);
                }));

        final Flow tapFlow =
                FlowKt.filterNotNull(deps.keyguardRootViewModel.getLastRootViewTapPosition());
        handles.add(JavaAdapterKt.collectFlow(container, tapFlow, (Consumer<Object>) value -> {
            Point point = (Point) value;
            Rect hitRect = new Rect();
            container.getHitRect(hitRect);
            if (!hitRect.contains(point.x, point.y)) {
                container.restoreToCollapsedState();
            }
        }));

        return new DisposableHandle() {
            @Override
            public void dispose() {
                for (DisposableHandle handle : handles) {
                    handle.dispose();
                }
            }
        };
    }
}
