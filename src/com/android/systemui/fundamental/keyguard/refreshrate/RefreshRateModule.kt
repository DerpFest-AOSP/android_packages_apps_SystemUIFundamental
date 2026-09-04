/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.keyguard.refreshrate

import com.android.systemui.CoreStartable
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/**
 * Dagger wiring for the pre-auth refresh-rate boost. Binds [RefreshRateRequesterBinder] into the
 * SystemUI [CoreStartable] map so it starts with SysUI. Included from
 * com.android.systemui.fundamental.FundamentalModule.
 */
@Module
abstract class RefreshRateModule {
    @Binds
    @IntoMap
    @ClassKey(RefreshRateRequesterBinder::class)
    abstract fun bindRefreshRateRequesterBinder(
        impl: RefreshRateRequesterBinder
    ): CoreStartable
}
