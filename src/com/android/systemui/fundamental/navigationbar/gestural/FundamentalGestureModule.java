/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Feature-owned Dagger module that binds the Pixel TFLite back-gesture classifier
 * (BackGestureTfClassifierProviderGoogle) in place of the AOSP no-op
 * com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider.
 *
 * WIRED: FundamentalReferenceSystemUIModule (our in-repo fork of ReferenceSystemUIModule)
 * swaps AOSP com.android.systemui.navigationbar.gestural.GestureModule out for this module,
 * so BackGestureTfClassifierProvider has exactly one binding (the Google TFLite one).
 * NOTE: runtime-gated OFF on caiman until device overlay config_useBackGestureML=true and
 * DeviceConfig systemui/use_back_gesture_ml_model=true.
 */
package com.android.systemui.fundamental.navigationbar.gestural;

import android.content.Context;
import android.provider.DeviceConfig;

import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider;

import dagger.Module;
import dagger.Provides;

@Module
public interface FundamentalGestureModule {

    /** DeviceConfig key (namespace "systemui") selecting the back-gesture model basename. */
    String BACK_GESTURE_ML_MODEL_NAME = "back_gesture_ml_model_name";

    /** Default model basename; resolves to backgesture.tflite + backgesture.vocab in assets. */
    String DEFAULT_MODEL_NAME = "backgesture";

    /**
     * Provides the Pixel TFLite-backed classifier. Unscoped: EdgeBackGestureHandler injects a
     * {@code Provider<BackGestureTfClassifierProvider>} and constructs a fresh instance each time
     * gesture-nav ML loading is (re)triggered, releasing it when disabled.
     */
    @Provides
    static BackGestureTfClassifierProvider provideBackGestureTfClassifierProvider(
            Context context) {
        String modelName = DeviceConfig.getString(
                DeviceConfig.NAMESPACE_SYSTEMUI, BACK_GESTURE_ML_MODEL_NAME, DEFAULT_MODEL_NAME);
        return new BackGestureTfClassifierProviderGoogle(context.getAssets(), modelName);
    }
}
