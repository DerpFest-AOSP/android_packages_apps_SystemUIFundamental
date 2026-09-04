/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.keyguard.quickaffordance;

import com.android.systemui.keyguard.data.quickaffordance.KeyguardQuickAffordanceConfig;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

/**
 * FundamentalOS lock-screen quick-affordance extensions.
 *
 * <p>Contributes additional {@link KeyguardQuickAffordanceConfig}s into the same
 * {@code Set<KeyguardQuickAffordanceConfig>} multibinding that AOSP's
 * {@code KeyguardDataQuickAffordanceModule} declares via {@code @ElementsIntoSet} and that
 * {@code KeyguardQuickAffordanceRepository} consumes. {@code @IntoSet} element bindings merge with
 * AOSP's {@code @ElementsIntoSet} set bindings under the identical binding key, so the configs bound
 * here appear in the wallpaper/lock-screen picker alongside the built-in ones with no changes to
 * frameworks/base.
 *
 * <p>Include this module from {@code FundamentalModule} (via {@code @Module(includes = ...)}). To add
 * another self-contained affordance, drop a new {@code KeyguardQuickAffordanceConfig} into this
 * package and add one more {@code @Binds @IntoSet} method here.
 */
@Module
public interface QuickAffordanceModule {

    @Binds
    @IntoSet
    KeyguardQuickAffordanceConfig bindCalculatorQuickAffordanceConfig(
            CalculatorQuickAffordanceConfig impl);

    @Binds
    @IntoSet
    KeyguardQuickAffordanceConfig bindCalendarQuickAffordanceConfig(
            CalendarQuickAffordanceConfig impl);
}
