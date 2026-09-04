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
 * <p>Includes are fully qualified (no {@code import}) so a feature module named identically to an
 * AOSP one (e.g. {@code smartspace.SmartspaceModule} vs
 * {@code com.android.systemui.smartspace.dagger.SmartspaceModule}) cannot clash.
 *
 * <p>Wired here (contributions that merge into existing AOSP multibindings / optional bindings):
 * <ul>
 *   <li>AmbientIndicationModule — Now Playing lockscreen section + quick affordance.</li>
 *   <li>smartspace.SmartspaceModule — lockscreen date+weather BcSmartspace plugin bindings.</li>
 *   <li>QuickAffordanceModule — Calculator + Calendar lockscreen quick affordances.</li>
 *   <li>RefreshRateModule — pre-auth refresh-rate boost CoreStartable.</li>
 * </ul>
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
        com.android.systemui.fundamental.smartspace.SmartspaceModule.class,
        com.android.systemui.fundamental.keyguard.quickaffordance.QuickAffordanceModule.class,
        com.android.systemui.fundamental.keyguard.refreshrate.RefreshRateModule.class,
})
public abstract class FundamentalModule {
}
