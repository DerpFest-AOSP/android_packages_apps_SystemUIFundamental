/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Ported from the Pixel SystemUIGoogle implementation
 * (com.google.android.systemui.gesture.BackGestureTfClassifierProviderGoogle), Android 17
 * (CP2A) revision. Loads the Pixel TFLite back-gesture model + vocab to reduce false back-gesture
 * triggers. Overrides the AOSP base
 * com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider.
 *
 * Android 17 vs. the Android 16 port: the constructor no longer opens the model. The provider is
 * a cheap holder of the asset names; the interpreter is memory-mapped lazily from loadVocab()
 * (EdgeBackGestureHandler calls it on its background executor) under a process-wide lock, the
 * vocab is cached on the provider, and release() only drops the loaded state so the same provider
 * can be loaded again. The AssetFileDescriptor is closed as soon as the mapping exists (the
 * MappedByteBuffer stays valid), instead of being kept open until release().
 */
package com.android.systemui.fundamental.navigationbar.gestural;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Trace;
import android.util.Log;

import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class BackGestureTfClassifierProviderGoogle extends BackGestureTfClassifierProvider {
    private static final String TAG = "BackGestureTfClassifier";

    /** Serializes model + vocab loading across provider instances (stock: sModelLoadingLock). */
    private static final Object sModelLoadingLock = new Object();

    private final String mModelFile;
    private final String mVocabFile;
    private final Map<Integer, Object> mOutputMap = new HashMap<>();
    private final float[][] mOutput = (float[][]) Array.newInstance(float.class, 1, 1);

    private Interpreter mInterpreter;
    private boolean mModelLoaded;
    private Map<String, Integer> mVocab;

    public BackGestureTfClassifierProviderGoogle(String modelName) {
        mModelFile = modelName + ".tflite";
        mVocabFile = modelName + ".vocab";
        mOutputMap.put(0, mOutput);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    /**
     * Loads the model (if not loaded yet) and returns the cached vocab. EdgeBackGestureHandler
     * calls this off the main thread before the provider is used for prediction.
     */
    @Override
    public Map<String, Integer> loadVocab(AssetManager assetManager) {
        synchronized (sModelLoadingLock) {
            if (!mModelLoaded) {
                loadModel(assetManager);
            }
            if (mVocab == null) {
                mVocab = readVocab(assetManager);
            }
            return mVocab;
        }
    }

    @Override
    public float predict(Object[] featuresVector) {
        if (!mModelLoaded) {
            Log.e(TAG, "cannot predict; model not loaded");
            return -1.0f;
        }
        mInterpreter.runForMultipleInputsOutputs(featuresVector, mOutputMap);
        return mOutput[0][0];
    }

    @Override
    public void release() {
        mVocab = null;
        mModelLoaded = false;
        Interpreter interpreter = mInterpreter;
        if (interpreter != null) {
            interpreter.close();
            mInterpreter = null;
        }
    }

    private void loadModel(AssetManager assetManager) {
        Trace.beginSection("BackGestureTfClassifierProviderGoogle#modelLoading");
        try (AssetFileDescriptor fd = assetManager.openFd(mModelFile)) {
            mInterpreter = new Interpreter(fd.createInputStream().getChannel().map(
                    FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength()));
            mModelLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Load TFLite file error:", e);
            mModelLoaded = false;
        } finally {
            Trace.endSection();
        }
    }

    private Map<String, Integer> readVocab(AssetManager assetManager) {
        HashMap<String, Integer> vocab = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assetManager.open(mVocabFile)))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                vocab.put(line, index++);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load vocab file error: ", e);
        }
        return vocab;
    }
}
