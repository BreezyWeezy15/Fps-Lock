package com.rick.fps_app

import android.view.Choreographer
import android.widget.TextView
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class FPSMonitor(samplePeriodMs: Long) {
    private val samplePeriodNs: Long = TimeUnit.NANOSECONDS.convert(samplePeriodMs, TimeUnit.MILLISECONDS)
    private val frameTimestamps = mutableListOf<Long>()
    private var monitoring = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!monitoring) return


            frameTimestamps.add(frameTimeNanos)

            val oldestTimestamp = frameTimeNanos - samplePeriodNs
            frameTimestamps.removeAll { it < oldestTimestamp }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        frameTimestamps.clear()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopMonitoring() {
        monitoring = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun calculateFPS(): Int {
        if (frameTimestamps.size < 2) {
            println("Not enough data to calculate FPS")
            return 0
        }

        val durationNs = frameTimestamps.last() - frameTimestamps.first()
        val durationSeconds = durationNs / 1_000_000_000f

        val fps = (frameTimestamps.size - 1) / durationSeconds

        // Round the FPS value to the nearest whole number
        return fps.roundToInt()
    }

}
