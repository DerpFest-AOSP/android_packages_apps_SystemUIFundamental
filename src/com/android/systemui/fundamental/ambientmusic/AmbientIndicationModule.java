/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import com.android.systemui.CoreStartable;
import com.android.systemui.fundamental.ambientmusic.keyguard.AmbientIndicationCoreStartable;
import com.android.systemui.fundamental.ambientmusic.quickaffordance.NowPlayingQuickAffordanceConfig;
import com.android.systemui.fundamental.ambientmusic.ui.composable.AmbientIndicationElementProvider;
import com.android.systemui.fundamental.ambientmusic.ui.sections.DefaultAmbientIndicationAreaSection;
import com.android.systemui.keyguard.data.quickaffordance.KeyguardQuickAffordanceConfig;
import com.android.systemui.keyguard.shared.model.KeyguardSection;
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardSectionsModule;
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider;

import javax.inject.Named;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;

/**
 * Wires the Now Playing lockscreen feature into the SystemUI graph:
 *
 * <ul>
 *   <li>The passive text line fills the AOSP {@code @BindsOptionalOf}
 *       {@code @Named(KEYGUARD_AMBIENT_INDICATION_AREA_SECTION) KeyguardSection} seam, used when
 *       scene container is off.
 *   <li>The same view is contributed as a {@link LockscreenElementProvider} for
 *       {@code LockscreenElementKeys.AmbientIndicationArea}, which is the slot scene container
 *       actually composes. AOSP ships that slot empty.
 *   <li>The broadcast receiver is started via a {@link CoreStartable}.
 *   <li>The "search a song" button is contributed into the quick-affordance config set.
 * </ul>
 *
 * <p>Included from {@code FundamentalModule} (via integrationSpec).
 */
@Module
public abstract class AmbientIndicationModule {

    @Binds
    @IntoMap
    @ClassKey(AmbientIndicationCoreStartable.class)
    abstract CoreStartable bindAmbientIndicationCoreStartable(
            AmbientIndicationCoreStartable impl);

    @Binds
    @Named(KeyguardSectionsModule.KEYGUARD_AMBIENT_INDICATION_AREA_SECTION)
    abstract KeyguardSection bindAmbientIndicationAreaSection(
            DefaultAmbientIndicationAreaSection impl);

    @Binds
    @IntoSet
    abstract LockscreenElementProvider bindAmbientIndicationElementProvider(
            AmbientIndicationElementProvider impl);

    @Binds
    @IntoSet
    abstract KeyguardQuickAffordanceConfig bindNowPlayingQuickAffordanceConfig(
            NowPlayingQuickAffordanceConfig impl);
}
