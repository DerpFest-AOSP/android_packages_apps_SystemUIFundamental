/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic.nonpixel.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AmbientAudioRecorder {

    companion object {
        private const val TAG = "AmbientAudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SILENCE_RMS_THRESHOLD = 25.0
        private const val EARLY_SILENCE_CHECK_BYTES = SAMPLE_RATE * 2 * 1 // 1.0 second of audio
    }

    private fun calculateRms(pcmBytes: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sumSquare = 0.0
        val numShorts = length / 2
        val byteBuffer = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numShorts) {
            val sample = byteBuffer.short.toDouble()
            sumSquare += sample * sample
        }
        return Math.sqrt(sumSquare / numShorts)
    }

    @SuppressLint("MissingPermission")
    suspend fun recordSample(
        durationSeconds: Int = 8,
        abortOnSilence: Boolean = true,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size for audio recording: $minBufferSize")
            return@withContext null
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
        var audioRecord: AudioRecord? = null

        try {
            // MIC keeps music intact. VOICE_RECOGNITION applies speech processing that
            // can strip a song down to silence, which is what Spacewar was capturing.
            val sources = intArrayOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
            )
            for (source in sources) {
                try {
                    val candidate = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize,
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = candidate
                        break
                    }
                    candidate.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Audio source $source unavailable", e)
                }
            }

            val record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return@withContext null
            }
            val totalBytesTarget = SAMPLE_RATE * 2 * durationSeconds
            val outputStream = ByteArrayOutputStream(totalBytesTarget)
            val buffer = ByteArray(bufferSize)

            record.startRecording()
            var bytesReadTotal = 0
            var earlySilenceChecked = false

            while (bytesReadTotal < totalBytesTarget && isActive) {
                val bytesToRead = (totalBytesTarget - bytesReadTotal).coerceAtMost(buffer.size)
                val read = record.read(buffer, 0, bytesToRead)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    bytesReadTotal += read

                    // Early silence detection: check RMS after ~1 second of recorded audio
                    if (abortOnSilence && !earlySilenceChecked
                        && bytesReadTotal >= EARLY_SILENCE_CHECK_BYTES
                    ) {
                        earlySilenceChecked = true
                        val earlyBytes = outputStream.toByteArray()
                        val earlyRms = calculateRms(earlyBytes, earlyBytes.size)
                        if (earlyRms < SILENCE_RMS_THRESHOLD) {
                            Log.d(TAG, "Early silence detected (RMS: $earlyRms < $SILENCE_RMS_THRESHOLD), aborting capture early to save battery")
                            return@withContext null
                        }
                    }
                } else if (read < 0) {
                    Log.e(TAG, "Error while reading audio buffer: $read")
                    break
                }
            }

            if (bytesReadTotal >= totalBytesTarget / 2) {
                val recordedBytes = outputStream.toByteArray()
                val rms = calculateRms(recordedBytes, recordedBytes.size)
                if (rms < SILENCE_RMS_THRESHOLD) {
                    Log.d(TAG, "Audio sample RMS ($rms) is below silence threshold ($SILENCE_RMS_THRESHOLD), ignoring silence")
                    return@withContext null
                }
                return@withContext recordedBytes
            } else {
                Log.w(TAG, "Insufficient audio recorded: $bytesReadTotal bytes")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed during audio recording", e)
            return@withContext null
        } finally {
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
                audioRecord?.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
