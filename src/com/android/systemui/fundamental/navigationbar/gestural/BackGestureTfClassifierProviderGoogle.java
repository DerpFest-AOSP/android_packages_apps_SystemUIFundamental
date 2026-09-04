/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 *
 * Ported from the Pixel SystemUIGoogle implementation
 * (com.google.android.systemui.gesture.BackGestureTfClassifierProviderGoogle).
 * Loads the Pixel TFLite back-gesture model + vocab to reduce false back-gesture
 * triggers. Overrides the AOSP base
 * com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider.
 */
package com.android.systemui.fundamental.navigationbar.gestural;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
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

    private final String mVocabFile;
    private Interpreter mInterpreter;
    private AssetFileDescriptor mModelFileDescriptor;
    private final Map<Integer, Object> mOutputMap = new HashMap();
    private final float[][] mOutput = (float[][]) Array.newInstance(float.class, 1, 1);

    public BackGestureTfClassifierProviderGoogle(AssetManager assetManager, String modelName) {
        mModelFileDescriptor = null;
        mInterpreter = null;
        mVocabFile = modelName + ".vocab";
        mOutputMap.put(0, mOutput);
        try {
            AssetFileDescriptor openFd = assetManager.openFd(modelName + ".tflite");
            mModelFileDescriptor = openFd;
            mInterpreter = new Interpreter(openFd.createInputStream().getChannel().map(
                    FileChannel.MapMode.READ_ONLY,
                    mModelFileDescriptor.getStartOffset(),
                    mModelFileDescriptor.getDeclaredLength()));
        } catch (Exception e) {
            Log.e(TAG, "Load TFLite file error:", e);
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, Integer> loadVocab(AssetManager assetManager) {
        HashMap<String, Integer> hashMap = new HashMap();
        try {
            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(assetManager.open(mVocabFile)));
            int i = 0;
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    break;
                }
                hashMap.put(readLine, Integer.valueOf(i));
                i++;
            }
            bufferedReader.close();
        } catch (Exception e) {
            Log.e(TAG, "Load vocab file error: ", e);
        }
        return hashMap;
    }

    @Override
    public float predict(Object[] featuresVector) {
        Interpreter interpreter = mInterpreter;
        if (interpreter == null) {
            return -1.0f;
        }
        interpreter.runForMultipleInputsOutputs(featuresVector, mOutputMap);
        return mOutput[0][0];
    }

    @Override
    public void release() {
        Interpreter interpreter = mInterpreter;
        if (interpreter != null) {
            interpreter.close();
        }
        AssetFileDescriptor assetFileDescriptor = mModelFileDescriptor;
        if (assetFileDescriptor != null) {
            try {
                assetFileDescriptor.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close model file descriptor: ", e);
            }
        }
    }
}
