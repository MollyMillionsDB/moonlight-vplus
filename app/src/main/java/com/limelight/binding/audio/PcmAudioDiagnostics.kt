package com.limelight.binding.audio

import android.media.AudioTrack
import android.os.Build
import com.limelight.LimeLog
import com.limelight.nvstream.jni.MoonBridge

class PcmAudioDiagnostics {
    private var windowStartNs = 0L
    private var previousArrivalNs = 0L
    private var callbacks = 0L
    private var windowWrittenFrames = 0L
    private var submittedFrames = 0L
    private var maxArrivalGapMs = 0L
    private var maxPendingAudioMs = 0
    private var totalWriteNs = 0L
    private var maxWriteMs = 0L
    private var shortWrites = 0L
    private var writeErrors = 0L
    private var validTimestamps = 0L
    private var lastTimestampPosition = -1L
    private var lastTimestampNanoTime = -1L

    fun onArrival() {
        val now = System.nanoTime()
        if (windowStartNs == 0L) windowStartNs = now
        if (previousArrivalNs != 0L) {
            maxArrivalGapMs = maxOf(maxArrivalGapMs, (now - previousArrivalNs) / 1_000_000)
        }
        previousArrivalNs = now
        callbacks++
        if (callbacks % 20L == 0L) {
            maxPendingAudioMs = maxOf(maxPendingAudioMs, MoonBridge.getPendingAudioDuration())
        }
    }

    fun onWrite(
        track: AudioTrack,
        requestedSamples: Int,
        writtenSamples: Int,
        channelCount: Int,
        sampleRate: Int,
        elapsedNs: Long,
        timestampValid: Boolean,
        timestampPosition: Long,
        timestampNanoTime: Long
    ) {
        val now = System.nanoTime()
        if (writtenSamples > 0 && channelCount > 0) {
            val frames = writtenSamples / channelCount
            windowWrittenFrames += frames
            submittedFrames += frames
        }
        totalWriteNs += elapsedNs
        maxWriteMs = maxOf(maxWriteMs, elapsedNs / 1_000_000)
        if (writtenSamples in 1 until requestedSamples) shortWrites++
        if (writtenSamples <= 0) writeErrors++
        if (timestampValid) {
            validTimestamps++
            lastTimestampPosition = timestampPosition
            lastTimestampNanoTime = timestampNanoTime
        }
        if (now - windowStartNs < 5_000_000_000L) return

        val underruns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) track.underrunCount else -1
        val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) track.routedDevice?.type ?: -1 else -1
        val bufferFrames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) track.bufferSizeInFrames else -1
        LimeLog.info(
            "PcmAudioDiag mode=opus-pcm clientNowNs=$now windowMs=${(now - windowStartNs) / 1_000_000} " +
                "callbacks=$callbacks windowWrittenFrames=$windowWrittenFrames submittedFrames=$submittedFrames sampleRate=$sampleRate " +
                "maxArrivalGapMs=$maxArrivalGapMs maxPendingAudioMs=$maxPendingAudioMs " +
                "avgWriteUs=${totalWriteNs / callbacks / 1_000} maxWriteMs=$maxWriteMs " +
                "shortWrites=$shortWrites writeErrors=$writeErrors underruns=$underruns routeType=$route " +
                "bufferFrames=$bufferFrames playbackHeadPosition=${track.playbackHeadPosition.toLong() and 0xffffffffL} " +
                "validTimestamps=$validTimestamps timestampPosition=$lastTimestampPosition " +
                "timestampNanoTime=$lastTimestampNanoTime"
        )
        windowStartNs = now
        callbacks = 0
        windowWrittenFrames = 0
        maxArrivalGapMs = 0
        maxPendingAudioMs = 0
        totalWriteNs = 0
        maxWriteMs = 0
        shortWrites = 0
        writeErrors = 0
        validTimestamps = 0
        lastTimestampPosition = -1
        lastTimestampNanoTime = -1
    }

    fun reset() {
        windowStartNs = 0
        previousArrivalNs = 0
        callbacks = 0
        windowWrittenFrames = 0
        submittedFrames = 0
        maxArrivalGapMs = 0
        maxPendingAudioMs = 0
        totalWriteNs = 0
        maxWriteMs = 0
        shortWrites = 0
        writeErrors = 0
        validTimestamps = 0
        lastTimestampPosition = -1
        lastTimestampNanoTime = -1
    }
}
