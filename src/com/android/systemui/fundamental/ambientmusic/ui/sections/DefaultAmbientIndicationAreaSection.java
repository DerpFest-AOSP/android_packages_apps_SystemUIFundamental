/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.sections;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.systemui.biometrics.AuthController;
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.fundamental.ambientmusic.ui.binder.KeyguardAmbientIndicationAreaViewBinder;
import com.android.systemui.fundamental.ambientmusic.ui.viewmodel.KeyguardAmbientIndicationViewModel;
import com.android.systemui.graphics.ImageLoader;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.shared.model.KeyguardSection;
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel;
import com.android.systemui.media.NotificationMediaManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.res.R;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.wakelock.DelayedWakeLock;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import kotlinx.coroutines.DisposableHandle;

/**
 * Places the Now Playing container in the keyguard root view. Bound to the AOSP seam
 * {@code @Named(KEYGUARD_AMBIENT_INDICATION_AREA_SECTION) KeyguardSection}.
 */
public final class DefaultAmbientIndicationAreaSection extends KeyguardSection {

    private final KeyguardAmbientIndicationViewModel mViewModel;
    private final KeyguardAmbientIndicationAreaViewBinder.Dependencies mDeps;
    private final AuthController mAuthController;

    private DisposableHandle mHandle;

    @Inject
    public DefaultAmbientIndicationAreaSection(
            KeyguardAmbientIndicationViewModel viewModel,
            PowerInteractor powerInteractor,
            ActivityStarter activityStarter,
            DelayedWakeLock.Factory wakeLockFactory,
            KeyguardInteractor keyguardInteractor,
            @Main ConfigurationInteractor configurationInteractor,
            ImageLoader imageLoader,
            KeyguardRootViewModel keyguardRootViewModel,
            FalsingManager falsingManager,
            @Main DelayableExecutor mainDelayableExecutor,
            @Background Executor backgroundExecutor,
            NotificationMediaManager notificationMediaManager,
            StatusBarStateController statusBarStateController,
            AuthController authController) {
        mViewModel = viewModel;
        mAuthController = authController;
        mDeps = new KeyguardAmbientIndicationAreaViewBinder.Dependencies(
                powerInteractor,
                activityStarter,
                wakeLockFactory,
                keyguardInteractor,
                configurationInteractor,
                imageLoader,
                keyguardRootViewModel,
                falsingManager,
                mainDelayableExecutor,
                backgroundExecutor,
                notificationMediaManager,
                statusBarStateController,
                authController);
    }

    @Override
    public void addViews(ConstraintLayout constraintLayout) {
        View view = LayoutInflater.from(constraintLayout.getContext())
                .inflate(R.layout.ambient_indication, (ViewGroup) constraintLayout, false);
        constraintLayout.addView(view);
    }

    @Override
    public void applyConstraints(ConstraintSet constraintSet) {
        final int container = R.id.ambient_indication_container;
        constraintSet.constrainWidth(container, ViewGroup.LayoutParams.MATCH_PARENT);
        if (!mAuthController.isUdfpsSupported()) {
            constraintSet.constrainHeight(container, ViewGroup.LayoutParams.WRAP_CONTENT);
            constraintSet.connect(container, ConstraintSet.BOTTOM,
                    R.id.device_entry_icon_view, ConstraintSet.TOP);
            constraintSet.connect(container, ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(container, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END);
        } else {
            constraintSet.constrainHeight(container, 0);
            constraintSet.connect(container, ConstraintSet.TOP,
                    R.id.device_entry_icon_view, ConstraintSet.BOTTOM);
            constraintSet.connect(container, ConstraintSet.BOTTOM,
                    R.id.keyguard_indication_area, ConstraintSet.TOP);
            constraintSet.connect(container, ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(container, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END);
        }
    }

    @Override
    public void bindData(ConstraintLayout constraintLayout) {
        mHandle = KeyguardAmbientIndicationAreaViewBinder.bind(constraintLayout, mViewModel, mDeps);
    }

    @Override
    public void removeViews(ConstraintLayout constraintLayout) {
        if (mHandle != null) {
            mHandle.dispose();
            mHandle = null;
        }
        View view = constraintLayout.findViewById(R.id.ambient_indication_container);
        if (view != null) {
            constraintLayout.removeView(view);
        }
    }
}
