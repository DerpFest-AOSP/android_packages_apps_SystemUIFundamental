/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.domain;

import android.app.PendingIntent;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Handler;
import android.os.Looper;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.fundamental.ambientmusic.data.AmbientIndicationRepository;
import com.android.systemui.fundamental.ambientmusic.shared.AmbientIndicationMusic;
import com.android.systemui.fundamental.ambientmusic.shared.ExtendedIndication;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;

import java.util.List;

import javax.inject.Inject;

import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

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
    private final AudioManager mAudioManager;
    /** {@code MutableStateFlow<Boolean>} — true while this phone is outputting media. */
    private final MutableStateFlow mDevicePlayingMusic;

    @Inject
    @SuppressWarnings("unchecked")
    public AmbientIndicationInteractor(
            AmbientIndicationRepository repository,
            KeyguardInteractor keyguardInteractor,
            AudioManager audioManager) {
        mRepository = repository;
        mKeyguardInteractor = keyguardInteractor;
        mAudioManager = audioManager;
        mDevicePlayingMusic = StateFlowKt.MutableStateFlow(audioManager.isMusicActive());
        audioManager.registerAudioPlaybackCallback(new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                boolean playing = mAudioManager.isMusicActive();
                mDevicePlayingMusic.setValue(playing);
                // Speakers (or a cast from this phone) are not music near the phone.
                // Drop the indication so the lockscreen island can use the slot.
                if (playing) {
                    hideAmbientMusic();
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }

    /** {@code Flow<Boolean>} true while this phone is outputting media. */
    public Flow getDevicePlayingMusic() {
        return mDevicePlayingMusic;
    }

    private boolean isDevicePlayingMusic() {
        return mAudioManager.isMusicActive();
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
        if (isDevicePlayingMusic()) {
            hideAmbientMusic();
            return;
        }
        mRepository.setAmbientMusic(new AmbientIndicationMusic(
                text, openIntent, favoritingIntent, iconOverride, skipUnlock, iconDescription,
                extendedIndication));
        mKeyguardInteractor.setAmbientIndicationVisible(true);
    }
}
