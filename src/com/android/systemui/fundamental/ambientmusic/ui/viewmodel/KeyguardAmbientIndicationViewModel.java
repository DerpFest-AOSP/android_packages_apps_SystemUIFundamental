/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.ui.viewmodel;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.fundamental.ambientmusic.AmbientIndicationFlows;
import com.android.systemui.fundamental.ambientmusic.domain.AmbientIndicationInteractor;
import com.android.systemui.keyguard.domain.interactor.BurnInInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.shared.model.BurnInModel;
import com.android.systemui.keyguard.shared.model.KeyguardState;

import javax.inject.Inject;

import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function3;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;

/**
 * View-model for the Now Playing area: the ambient-music state plus the AOD burn-in translation
 * offsets (burn-in model scaled by the AOD transition progress), mirroring the A17 stock
 * {@code KeyguardAmbientIndicationViewModel}.
 */
@SysUISingleton
public final class KeyguardAmbientIndicationViewModel {

    private final AmbientIndicationInteractor mInteractor;
    private final Flow mIndicationAreaTranslationX;
    private final Flow mIndicationAreaTranslationY;

    @Inject
    @SuppressWarnings({"unchecked", "rawtypes"})
    public KeyguardAmbientIndicationViewModel(
            AmbientIndicationInteractor interactor,
            BurnInInteractor burnInInteractor,
            KeyguardTransitionInteractor keyguardTransitionInteractor,
            @Background CoroutineContext backgroundContext,
            @Main CoroutineContext mainContext) {
        mInteractor = interactor;
        final int offsetDimen = com.android.systemui.res.R.dimen.default_burn_in_prevention_offset;
        final Flow burnInFlow = burnInInteractor.burnIn(offsetDimen, offsetDimen);
        final Flow aodProgressFlow = keyguardTransitionInteractor.transitionValue(KeyguardState.AOD);
        final Flow burnIn = FlowKt.flowOn(
                FlowKt.distinctUntilChanged(
                        FlowKt.combine(burnInFlow, aodProgressFlow,
                                (Function3) (model, progress, continuation) -> {
                                    BurnInModel burnInModel = (BurnInModel) model;
                                    float aodProgress = ((Number) progress).floatValue();
                                    return new BurnInModel(
                                            (int) (burnInModel.getTranslationX() * aodProgress),
                                            (int) (burnInModel.getTranslationY() * aodProgress),
                                            burnInModel.getScale(),
                                            burnInModel.getScaleClockOnly());
                                })),
                backgroundContext);
        mIndicationAreaTranslationX = FlowKt.flowOn(
                AmbientIndicationFlows.map(burnIn,
                        model -> (float) ((BurnInModel) model).getTranslationX()),
                mainContext);
        mIndicationAreaTranslationY = FlowKt.flowOn(
                AmbientIndicationFlows.map(burnIn,
                        model -> (float) ((BurnInModel) model).getTranslationY()),
                mainContext);
    }

    /** {@code Flow<AmbientIndicationMusic>}. */
    public Flow getAmbientMusicState() {
        return mInteractor.getAmbientMusicState();
    }

    /** {@code Flow<Float>}: horizontal burn-in offset to apply to the area on AOD. */
    public Flow getIndicationAreaTranslationX() {
        return mIndicationAreaTranslationX;
    }

    /** {@code Flow<Float>}: vertical burn-in offset to apply to the area on AOD. */
    public Flow getIndicationAreaTranslationY() {
        return mIndicationAreaTranslationY;
    }
}
