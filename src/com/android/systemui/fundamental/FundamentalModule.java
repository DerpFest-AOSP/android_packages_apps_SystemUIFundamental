/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental;

import dagger.Module;

/**
 * FundamentalOS SystemUI additions. Each feature ships a self-contained Dagger module that is
 * pulled into the graph here via {@code @Module(includes = { ... })}.
 *
 * <p>Wired here (contributions that merge into existing AOSP multibindings / optional bindings):
 * <ul>
 *   <li>AmbientIndicationModule — Now Playing lockscreen section + quick affordance.</li>
 *   <li>QuickAffordanceModule — Calculator + Calendar lockscreen quick affordances.</li>
 *   <li>RefreshRateModule — pre-auth refresh-rate boost CoreStartable.</li>
 * </ul>
 *
 * <p>Smartspace is intentionally not provided here. Derp's SystemUI-core already binds the
 * reverse-engineered Google BcSmartspace plugins and views under
 * {@code com.google.android.systemui.smartspace} (consumed by
 * {@code LockscreenSmartspaceController}). Replacing those with a lean Fundamental provider
 * would duplicate Dagger bindings and would make Smartspacer's native-mode class probe miss
 * {@code BcSmartspaceCard} / {@code uitemplate.*} / {@code WeatherSmartspaceView}.
 *
 * <p>NOT wired here (they override existing singleton bindings and would duplicate them; wired
 * instead by swapping modules inside {@code FundamentalReferenceSystemUIModule}):
 * <ul>
 *   <li>Screenshot reveal effects + notification smart actions — via FundamentalScreenshotModule.</li>
 *   <li>Back-gesture ML classifier — via FundamentalGestureModule (replaces GestureModule).</li>
 * </ul>
 */
@Module(includes = {
        com.android.systemui.fundamental.ambientmusic.AmbientIndicationModule.class,
        com.android.systemui.fundamental.keyguard.quickaffordance.QuickAffordanceModule.class,
        com.android.systemui.fundamental.keyguard.refreshrate.RefreshRateModule.class,
})
public abstract class FundamentalModule {
}
