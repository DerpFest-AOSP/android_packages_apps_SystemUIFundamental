/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental;

import android.content.Context;

import com.android.systemui.SystemUIAppComponentFactoryBase;
import com.android.systemui.SystemUIInitializer;

/** Boots SystemUI via {@link FundamentalSystemUIInitializer}. */
public class FundamentalSystemUIAppComponentFactory extends SystemUIAppComponentFactoryBase {
    @Override
    protected SystemUIInitializer createSystemUIInitializer(Context context) {
        return new FundamentalSystemUIInitializer(context);
    }
}
