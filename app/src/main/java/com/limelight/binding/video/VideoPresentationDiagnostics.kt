package com.limelight.binding.video

import com.limelight.LimeLog

class VideoPresentationDiagnostics {
    private var windowStartNs = 0L
    private var previousRenderNs = 0L
    private var frames = 0L
    private var maxRenderGapMs = 0L
    private var maxCallbackLagMs = 0L
    private var lastPresentationTimeUs = -1L
    private var lastRenderTimeNs = -1L

    fun onFrameRendered(presentationTimeUs: Long, renderTimeNs: Long) {
        val callbackNowNs = System.nanoTime()
        if (windowStartNs == 0L) windowStartNs = callbackNowNs
        frames++
        if (renderTimeNs > 0L) {
            if (previousRenderNs > 0L && renderTimeNs >= previousRenderNs) {
                maxRenderGapMs = maxOf(maxRenderGapMs, (renderTimeNs - previousRenderNs) / 1_000_000)
            }
            if (callbackNowNs >= renderTimeNs) {
                maxCallbackLagMs = maxOf(maxCallbackLagMs, (callbackNowNs - renderTimeNs) / 1_000_000)
            }
            previousRenderNs = renderTimeNs
        }
        lastPresentationTimeUs = presentationTimeUs
        lastRenderTimeNs = renderTimeNs
        if (callbackNowNs - windowStartNs < 5_000_000_000L) return

        LimeLog.info(
            "VideoPresentationDiag callbackNowNs=$callbackNowNs windowMs=${(callbackNowNs - windowStartNs) / 1_000_000} " +
                "frames=$frames maxRenderGapMs=$maxRenderGapMs maxCallbackLagMs=$maxCallbackLagMs " +
                "lastPresentationTimeUs=$lastPresentationTimeUs lastRenderTimeNs=$lastRenderTimeNs"
        )
        windowStartNs = callbackNowNs
        frames = 0
        maxRenderGapMs = 0
        maxCallbackLagMs = 0
    }

    fun reset() {
        windowStartNs = 0
        previousRenderNs = 0
        frames = 0
        maxRenderGapMs = 0
        maxCallbackLagMs = 0
        lastPresentationTimeUs = -1
        lastRenderTimeNs = -1
    }
}
