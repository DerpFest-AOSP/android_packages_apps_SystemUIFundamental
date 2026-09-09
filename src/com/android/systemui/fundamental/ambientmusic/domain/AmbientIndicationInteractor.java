/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.domain;

import android.app.PendingIntent;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.fundamental.ambientmusic.data.AmbientIndicationRepository;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusic;
import com.android.systemui.fundamental.ambientmusic.shared.ExtendedIndication;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;

import javax.inject.Inject;

import kotlinx.coroutines.flow.Flow;

/**
 * Bridges the {@link AmbientIndicationRepository} state and the core keyguard visibility flag.
 *
 * <p>Writing a non-null indication flips {@code KeyguardInteractor.ambientIndicationVisible} on so
 * the keyguard layout reserves room; clearing it flips it back off.
 */
@SysUISingleton
public final class AmbientIndicationInteractor {

    private final AmbientIndicationRepository mRepository;
    private final KeyguardInteractor mKeyguardInteractor;

    @Inject
    public AmbientIndicationInteractor(
            AmbientIndicationRepository repository,
            KeyguardInteractor keyguardInteractor) {
        mRepository = repository;
        mKeyguardInteractor = keyguardInteractor;
    }

    /** {@code Flow<AmbientIndicationMusic>} of the current passive indication. */
    public Flow getAmbientMusicState() {
        return mRepository.ambientMusic;
    }

    public AmbientIndicationRepository getRepository() {
        return mRepository;
    }

    public void hideAmbientMusic() {
        mRepository.setAmbientMusic(null);
        mKeyguardInteractor.setAmbientIndicationVisible(false);
    }

    public void setAmbientMusic(
            CharSequence text,
            PendingIntent openIntent,
            PendingIntent favoritingIntent,
            Integer iconOverride,
            Boolean skipUnlock,
            String iconDescription,
            ExtendedIndication extendedIndication) {
        mRepository.setAmbientMusic(new AmbientIndicationMusic(
                text, openIntent, favoritingIntent, iconOverride, skipUnlock, iconDescription,
                extendedIndication));
        mKeyguardInteractor.setAmbientIndicationVisible(true);
    }
}
