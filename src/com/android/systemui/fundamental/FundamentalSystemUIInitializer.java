/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental;

import android.content.Context;

import com.android.systemui.SystemUIInitializer;
import com.android.systemui.dagger.GlobalRootComponent;

/**
 * Substitutes the default {@link GlobalRootComponent} for
 * {@link FundamentalGlobalRootComponent}.
 */
public final class FundamentalSystemUIInitializer extends SystemUIInitializer {
    public FundamentalSystemUIInitializer(Context context) {
        super(context);
    }

    @Override
    protected GlobalRootComponent.Builder getGlobalRootComponentBuilder() {
        return DaggerFundamentalGlobalRootComponent.builder();
    }
}
