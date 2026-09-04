/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental;

import com.android.systemui.dagger.GlobalModule;
import com.android.systemui.dagger.GlobalRootComponent;

import javax.inject.Singleton;

import dagger.Component;

/** Root Dagger component for FundamentalOS SystemUI. Mirrors ReferenceGlobalRootComponent. */
@Singleton
@Component(modules = {GlobalModule.class})
public interface FundamentalGlobalRootComponent extends GlobalRootComponent {

    @Component.Builder
    interface Builder extends GlobalRootComponent.Builder {
        FundamentalGlobalRootComponent build();
    }

    @Override
    FundamentalSysUIComponent.Builder getSysUIComponent();
}
