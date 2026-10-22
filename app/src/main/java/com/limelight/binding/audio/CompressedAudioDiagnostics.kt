package com.limelight.binding.audio

import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import com.limelight.LimeLog
import com.limelight.nvstream.jni.MoonBridge

class CompressedAudioDiagnostics(private val mode: String, private val bytesPerWriteUnit: Int) {
    private val timestamp = AudioTimestamp()
    private var windowStartNs = 0L
    private var previousArrivalNs = 0L
    private var frames = 0L
    private var bytes = 0L
    private var maxArrivalGapMs = 0L
    private var maxPendingAudioMs = 0
    private var maxWriteMs = 0L
    private var totalWriteNs = 0L
    private var writes = 0L
    private var shortWrites = 0L
    private var writeErrors = 0L

    fun onArrival() {
        val now = System.nanoTime()
        if (windowStartNs == 0L) windowStartNs = now
        if (previousArrivalNs != 0L) {
            maxArrivalGapMs = maxOf(maxArrivalGapMs, (now - previousArrivalNs) / 1_000_000)
        }
        previousArrivalNs = now
        maxPendingAudioMs = maxOf(maxPendingAudioMs, MoonBridge.getPendingAudioDuration())
        frames++
    }

    fun onWrite(requested: Int, written: Int, elapsedNs: Long) {
        bytes += written.coerceAtLeast(0).toLong() * bytesPerWriteUnit
        totalWriteNs += elapsedNs
        writes++
        maxWriteMs = maxOf(maxWriteMs, elapsedNs / 1_000_000)
        if (written in 1 until requested) shortWrites++
        if (written <= 0) writeErrors++
    }

    fun reportIfDue(track: AudioTrack) {
        val now = System.nanoTime()
        if (windowStartNs == 0L || now - windowStartNs < 5_000_000_000L) return
        val timestampValid = try { track.getTimestamp(timestamp) } catch (_: Exception) { false }
        val underruns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) track.underrunCount else -1
        val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            track.routedDevice?.type ?: -1
        } else -1
        val bufferFrames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) track.bufferSizeInFrames else -1
        LimeLog.info(
            "CompressedAudioDiag mode=$mode clientNowNs=$now windowMs=${(now - windowStartNs) / 1_000_000} " +
                "frames=$frames bytes=$bytes maxArrivalGapMs=$maxArrivalGapMs " +
                "maxPendingAudioMs=$maxPendingAudioMs " +
                "avgWriteUs=${if (writes > 0) totalWriteNs / writes / 1_000 else 0} maxWriteMs=$maxWriteMs " +
                "shortWrites=$shortWrites writeErrors=$writeErrors underruns=$underruns routeType=$route " +
                "bufferFrames=$bufferFrames playbackHeadPosition=${track.playbackHeadPosition.toLong() and 0xffffffffL} " +
                "timestampValid=$timestampValid timestampPosition=${if (timestampValid) timestamp.framePosition else -1} " +
                "timestampNanoTime=${if (timestampValid) timestamp.nanoTime else -1}"
        )
        windowStartNs = now
        frames = 0
        bytes = 0
        maxArrivalGapMs = 0
        maxPendingAudioMs = 0
        maxWriteMs = 0
        totalWriteNs = 0
        writes = 0
        shortWrites = 0
        writeErrors = 0
    }

    fun reset() {
        windowStartNs = 0
        previousArrivalNs = 0
        frames = 0
        bytes = 0
        maxArrivalGapMs = 0
        maxPendingAudioMs = 0
        maxWriteMs = 0
        totalWriteNs = 0
        writes = 0
        shortWrites = 0
        writeErrors = 0
    }
}
