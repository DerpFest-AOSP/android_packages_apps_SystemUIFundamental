/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.smartspace;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.plugins.BcSmartspaceConfigPlugin;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.smartspace.SmartspaceTargetFilter;
import com.android.systemui.smartspace.config.BcSmartspaceConfigProvider;
import com.android.systemui.smartspace.filters.LockscreenTargetFilter;

import javax.inject.Named;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

/**
 * FundamentalOS BcSmartspace seam. Mirrors the bindings of stock SystemUIGoogle's
 * {@code SmartspaceGoogleModule} (Android 17, CP2A) on top of our lean providers/views.
 *
 * <p>AOSP only declares {@code @BindsOptionalOf} for every smartspace key and binds no concrete
 * implementation, so all consumers see {@code Optional.empty()} and stay dormant:
 * <ul>
 *   <li>{@code com.android.systemui.dagger.SystemUIModule}: the unqualified
 *       {@link BcSmartspaceDataPlugin}, {@code @Named(DATE_SMARTSPACE_DATA_PLUGIN)},
 *       {@code @Named(WEATHER_SMARTSPACE_DATA_PLUGIN)} and {@link BcSmartspaceConfigPlugin} —
 *       consumed by {@code LockscreenSmartspaceController} ({@code isEnabled} /
 *       {@code isDateWeatherDecoupled}) for the keyguard {@code SmartspaceSection}.</li>
 *   <li>{@code com.android.systemui.smartspace.dagger.SmartspaceModule}:
 *       {@code @Named(DREAM_SMARTSPACE_DATA_PLUGIN)} +
 *       {@code @Named(DREAM_WEATHER_SMARTSPACE_DATA_PLUGIN)} — consumed by
 *       {@code DreamSmartspaceController} (dream overlay {@code SmartSpaceComplication});
 *       {@code @Named(GLANCEABLE_HUB_SMARTSPACE_DATA_PLUGIN)} —
 *       consumed by {@code CommunalSmartspaceController} ({@code CommunalSmartspaceRepository});
 *       and {@code @Named(LOCKSCREEN_SMARTSPACE_TARGET_FILTER)} — consumed by both of those
 *       controllers to drop sensitive targets.</li>
 * </ul>
 * Providing concrete plugins here lights those surfaces up; the card data itself is delivered by
 * the device's ASI smartspace service (wired via the device overlay
 * {@code config_defaultSmartspaceService}).
 *
 * <p>Scoping matches stock: every plugin key is {@code @SysUISingleton} (each surface gets its own
 * cached provider so every consumer and every view built from it shares the same target/listener
 * state), while the target filter is unscoped (stock hands each controller a fresh
 * {@link LockscreenTargetFilter}).
 *
 * <p>The AOSP constants are referenced fully-qualified on purpose: this module is intentionally
 * named {@code SmartspaceModule} too, which would clash with an import of
 * {@code com.android.systemui.smartspace.dagger.SmartspaceModule}.
 *
 * <p>Include this module from {@code FundamentalModule}.
 */
@Module
public abstract class SmartspaceModule {

    // ---- Lockscreen (keys declared in com.android.systemui.dagger.SystemUIModule) ----

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
     * Config plugin that {@code LockscreenSmartspaceController} hands to the general view via
     * {@code SmartspaceView.registerConfigProvider}. Reuses AOSP's (now empty)
     * {@link BcSmartspaceConfigProvider}, same as stock.
     */
    @Provides
    @SysUISingleton
    static BcSmartspaceConfigPlugin provideBcSmartspaceConfigPlugin(FeatureFlags featureFlags) {
        return new BcSmartspaceConfigProvider(featureFlags);
    }

    // ---- Dream + glanceable hub (keys declared in smartspace.dagger.SmartspaceModule) ----

    /** General smartspace plugin for the dream overlay ({@code SmartSpaceComplication}). */
    @Provides
    @SysUISingleton
    @Named(com.android.systemui.smartspace.dagger.SmartspaceModule.DREAM_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideDreamBcSmartspaceDataPlugin() {
        return new BcSmartspaceDataProvider();
    }

    /** Standalone weather plugin for the dream overlay (receives unfiltered targets). */
    @Provides
    @SysUISingleton
    @Named(com.android.systemui.smartspace.dagger.SmartspaceModule
            .DREAM_WEATHER_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideDreamWeatherSmartspaceDataPlugin() {
        return new WeatherSmartspaceDataProvider();
    }

    /** General smartspace plugin for the glanceable hub ({@code CommunalSmartspaceRepository}). */
    @Provides
    @SysUISingleton
    @Named(com.android.systemui.smartspace.dagger.SmartspaceModule
            .GLANCEABLE_HUB_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideGlanceableHubBcSmartspaceDataPlugin() {
        return new BcSmartspaceDataProvider();
    }

    /**
     * Target filter for the dream / hub controllers (hides sensitive targets per the lockscreen
     * privacy settings). AOSP's {@link LockscreenTargetFilter}, unscoped like stock.
     */
    @Binds
    @Named(com.android.systemui.smartspace.dagger.SmartspaceModule
            .LOCKSCREEN_SMARTSPACE_TARGET_FILTER)
    abstract SmartspaceTargetFilter bindLockscreenSmartspaceTargetFilter(
            LockscreenTargetFilter impl);
}
