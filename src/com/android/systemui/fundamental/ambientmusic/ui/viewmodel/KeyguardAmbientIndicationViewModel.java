/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.viewmodel;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.fundamental.ambientmusic.domain.AmbientIndicationInteractor;

import javax.inject.Inject;

import kotlinx.coroutines.flow.Flow;

/**
 * View-model for the passive Now Playing area.
 *
 * <p>v1 exposes only the ambient-music state. Burn-in / AOD translation offsets present in the
 * stock Pixel view-model are intentionally omitted (see feature blockers).
 */
@SysUISingleton
public final class KeyguardAmbientIndicationViewModel {

    private final AmbientIndicationInteractor mInteractor;

    @Inject
    public KeyguardAmbientIndicationViewModel(AmbientIndicationInteractor interactor) {
        mInteractor = interactor;
    }

    /** {@code Flow<AmbientIndicationMusic>}. */
    public Flow getAmbientMusicState() {
        return mInteractor.getAmbientMusicState();
    }
}
