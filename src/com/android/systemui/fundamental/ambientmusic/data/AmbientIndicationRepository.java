/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.data;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusic;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusicStatus;

import javax.inject.Inject;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/**
 * Holds the observable state for the Now Playing lockscreen surfaces.
 *
 * <ul>
 *   <li>{@link #ambientMusic}: the current passive indication (or {@code null} when hidden).
 *   <li>{@link #ambientMusicStatus}: enable/active state of the on-demand quick affordance.
 * </ul>
 */
@SysUISingleton
public final class AmbientIndicationRepository {

    /** {@code MutableStateFlow<AmbientIndicationMusic>} — null means "no passive indication". */
    @SuppressWarnings("unchecked")
    public final MutableStateFlow ambientMusic = StateFlowKt.MutableStateFlow(null);

    /** {@code MutableStateFlow<AmbientIndicationMusicStatus>}. */
    @SuppressWarnings("unchecked")
    public final MutableStateFlow ambientMusicStatus =
            StateFlowKt.MutableStateFlow(new AmbientIndicationMusicStatus(false, false));

    @Inject
    public AmbientIndicationRepository() {
    }

    public void setAmbientMusic(AmbientIndicationMusic music) {
        ambientMusic.setValue(music);
    }

    public void setAmbientMusicStatus(AmbientIndicationMusicStatus status) {
        ambientMusicStatus.setValue(status);
    }
}
