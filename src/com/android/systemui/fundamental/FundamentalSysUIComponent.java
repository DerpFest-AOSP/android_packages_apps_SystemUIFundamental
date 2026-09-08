/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental;

import com.android.systemui.bundle.phone.PodModulePhone;
import com.android.systemui.controls.dagger.StartControlsStartableModule;
import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.fundamental.dagger.FundamentalReferenceSystemUIModule;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUICoreStartableModule;
import com.android.systemui.dagger.SystemUIModule;
import com.android.systemui.keyguard.CustomizationProvider;
import com.android.systemui.notifications.intelligence.rules.ui.NotificationRulesDefaultModule;
import com.android.systemui.settings.MultiUserUtilsModule;
import com.android.systemui.statusbar.NotificationInsetsModule;
import com.android.systemui.statusbar.QsFrameTranslateModule;
import com.android.systemui.unfold.SysUIUnfoldModule;
import com.android.systemui.util.StartBinderLoggerModule;
import com.android.systemui.wallpapers.dagger.WallpaperModule;

import dagger.Subcomponent;

/**
 * Core SysUI subcomponent for FundamentalOS. Mirrors ReferenceSysUIComponent and adds
 * {@link FundamentalModule} as the seam for FundamentalOS SystemUI additions.
 */
@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        MultiUserUtilsModule.class,
        NotificationInsetsModule.class,
        NotificationRulesDefaultModule.class,
        QsFrameTranslateModule.class,
        FundamentalReferenceSystemUIModule.class,
        StartControlsStartableModule.class,
        StartBinderLoggerModule.class,
        SystemUIModule.class,
        PodModulePhone.class,
        SystemUICoreStartableModule.class,
        SysUIUnfoldModule.class,
        WallpaperModule.class,
        FundamentalModule.class})
public interface FundamentalSysUIComponent extends SysUIComponent {

    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        FundamentalSysUIComponent build();
    }

    void inject(CustomizationProvider customizationProvider);
}
