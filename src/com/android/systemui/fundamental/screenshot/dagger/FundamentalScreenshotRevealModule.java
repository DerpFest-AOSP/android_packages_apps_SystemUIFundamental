/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Dagger module for the FundamentalOS screenshot reveal effects. It provides a
 * FundamentalThumbnailObserver in place of the stock ThumbnailObserver so the effects are wired
 * into the screenshot preview via the AOSP seam (stock: GoogleScreenshotModule#providesThumbnailObserver).
 *
 * IMPORTANT (integration): AOSP's ReferenceScreenshotModule ALREADY has
 *   @Provides static ThumbnailObserver providesThumbnailObserver()
 * and it is pulled into the graph transitively (ReferenceSystemUIModule -> ReferenceScreenshotModule
 * -> FundamentalSysUIComponent). Dagger forbids two providers for the same type, so this module and
 * that provider cannot both be present; FundamentalReferenceSystemUIModule swaps
 * ReferenceScreenshotModule for FundamentalScreenshotModule (which includes this module).
 */
package com.android.systemui.fundamental.screenshot.dagger;

import com.android.systemui.fundamental.screenshot.FundamentalThumbnailObserver;
import com.android.systemui.screenshot.ThumbnailObserver;

import dagger.Module;
import dagger.Provides;

@Module
public interface FundamentalScreenshotRevealModule {

    /** Provides the FundamentalOS thumbnail observer that plays the screenshot reveal effects. */
    @Provides
    static ThumbnailObserver provideFundamentalThumbnailObserver() {
        return new FundamentalThumbnailObserver();
    }
}
