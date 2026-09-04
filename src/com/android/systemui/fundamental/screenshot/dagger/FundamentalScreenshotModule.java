/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.screenshot.dagger;

import com.android.systemui.fundamental.screenshot.FundamentalScreenshotSmartActionsModule;
import com.android.systemui.screenshot.DefaultScreenshotActionsProvider;
import com.android.systemui.screenshot.ScreenshotActionsProvider;

import dagger.Binds;
import dagger.Module;

/**
 * FundamentalOS replacement for {@code com.android.systemui.screenshot.ReferenceScreenshotModule}.
 *
 * <p>The base {@code ReferenceScreenshotModule} provides three things: a {@code ThumbnailObserver},
 * a {@code ScreenshotNotificationSmartActionsProvider}, and a {@code @Binds} for
 * {@code ScreenshotActionsProvider.Factory}. FundamentalOS overrides the first two:
 * <ul>
 *   <li>{@link FundamentalScreenshotRevealModule} supplies the glow/ripple
 *       {@code FundamentalThumbnailObserver} (screenshot reveal effects).</li>
 *   <li>{@link FundamentalScreenshotSmartActionsModule} supplies the ASI-backed
 *       {@code ScreenshotNotificationSmartActionsProviderGoogle}.</li>
 * </ul>
 * and re-declares the un-overridden {@code Factory} binding verbatim. This module is swapped in
 * for {@code ReferenceScreenshotModule} inside {@link
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
            DefaultScreenshotActionsProvider.Factory defaultScreenshotActionsProviderFactory);
}
