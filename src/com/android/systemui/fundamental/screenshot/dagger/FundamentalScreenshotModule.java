/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.screenshot.dagger;

import com.android.systemui.fundamental.screenshot.FundamentalScreenshotActionsProvider;
import com.android.systemui.fundamental.screenshot.FundamentalScreenshotSmartActionsModule;
import com.android.systemui.screenshot.ScreenshotActionsProvider;

import dagger.Binds;
import dagger.Module;

/**
 * FundamentalOS replacement for {@code com.android.systemui.screenshot.ReferenceScreenshotModule}
 * (the counterpart of the stock Pixel GoogleScreenshotModule).
 *
 * <p>The base {@code ReferenceScreenshotModule} provides three things: a {@code ThumbnailObserver},
 * a {@code ScreenshotNotificationSmartActionsProvider}, and a {@code @Binds} for
 * {@code ScreenshotActionsProvider.Factory}. FundamentalOS overrides all three:
 * <ul>
 *   <li>{@link FundamentalScreenshotRevealModule} supplies the glow/ripple
 *       {@code FundamentalThumbnailObserver} (screenshot reveal effects).</li>
 *   <li>{@link FundamentalScreenshotSmartActionsModule} supplies the ASI-backed
 *       {@code ScreenshotNotificationSmartActionsProviderGoogle}.</li>
 *   <li>The {@link ScreenshotActionsProvider.Factory} points at
 *       {@link FundamentalScreenshotActionsProvider}, which adds the ASI quick-share / smart
 *       action chips on top of the AOSP default shelf actions. Nothing in AOSP requests smart
 *       actions any more, so without this override the smart actions provider is never
 *       called.</li>
 * </ul>
 * This module is swapped in for {@code ReferenceScreenshotModule} inside {@link
 * com.android.systemui.fundamental.dagger.FundamentalReferenceSystemUIModule} so the net graph
 * contains exactly one binding for each of the three keys.
 */
@Module(includes = {
        FundamentalScreenshotSmartActionsModule.class,
        FundamentalScreenshotRevealModule.class,
})
public interface FundamentalScreenshotModule {

    /** */
    @Binds
    ScreenshotActionsProvider.Factory bindScreenshotActionsProviderFactory(
            FundamentalScreenshotActionsProvider.Factory fundamentalScreenshotActionsProviderFactory);
}
