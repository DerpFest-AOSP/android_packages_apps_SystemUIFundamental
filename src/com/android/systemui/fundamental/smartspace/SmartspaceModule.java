/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.smartspace;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.plugins.BcSmartspaceConfigPlugin;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.smartspace.config.BcSmartspaceConfigProvider;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

/**
 * FundamentalOS BcSmartspace seam.
 *
 * <p>AOSP declares {@code @BindsOptionalOf} for the three lockscreen smartspace plugin keys in
 * {@code com.android.systemui.dagger.SystemUIModule} (the main {@link BcSmartspaceDataPlugin},
 * {@code @Named(DATE_SMARTSPACE_DATA_PLUGIN)} and {@code @Named(WEATHER_SMARTSPACE_DATA_PLUGIN)})
 * plus {@code BcSmartspaceConfigPlugin}, but binds no concrete implementation, so
 * {@code LockscreenSmartspaceController.isEnabled} / {@code isDateWeatherDecoupled} stay false and
 * the whole card is GONE. Providing concrete plugins here flips both flags to true; the card data
 * itself is delivered by the device's ASI smartspace service (wired via the device overlay
 * {@code config_defaultSmartspaceService}).
 *
 * <p>The providers are {@code @SysUISingleton}-scoped to match stock {@code SystemUIGoogleModule}:
 * each plugin key must resolve to a single cached instance so that every consumer (and every view
 * built from it) shares the same target/listener state.
 *
 * <p>The AOSP constants are referenced fully-qualified on purpose: this module is intentionally
 * named {@code SmartspaceModule} too, which would clash with an import of
 * {@code com.android.systemui.smartspace.dagger.SmartspaceModule}.
 *
 * <p>Include this module from {@code FundamentalModule}.
 */
@Module
public abstract class SmartspaceModule {

    /** The main (general "at a glance") lockscreen smartspace plugin. Enables {@code isEnabled}. */
    @Provides
    @SysUISingleton
    static BcSmartspaceDataPlugin provideBcSmartspaceDataPlugin() {
        return new BcSmartspaceDataProvider();
    }

    /** Standalone date (+ alarm + DND) plugin. Half of {@code isDateWeatherDecoupled}. */
    @Provides
    @SysUISingleton
    @Named(com.android.systemui.smartspace.dagger.SmartspaceModule.DATE_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideDateSmartspaceDataPlugin() {
        return new DateSmartspaceDataProvider();
    }

    /** Standalone weather plugin. The other half of {@code isDateWeatherDecoupled}. */
    @Provides
    @SysUISingleton
    @Named(com.android.systemui.smartspace.dagger.SmartspaceModule.WEATHER_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideWeatherSmartspaceDataPlugin() {
        return new WeatherSmartspaceDataProvider();
    }

    /**
     * Config plugin consumed by {@code LockscreenSmartspaceController.optionalConfigPlugin}. Reuses
     * the Lineage-shipped provider (flag-driven; the ViewPager2/carousel toggles are inert for the
     * lean views here, but this matches stock and keeps the graph faithful).
     */
    @Provides
    @SysUISingleton
    static BcSmartspaceConfigPlugin provideBcSmartspaceConfigPlugin(FeatureFlags featureFlags) {
        return new BcSmartspaceConfigProvider(featureFlags);
    }
}
