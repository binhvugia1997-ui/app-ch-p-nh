package com.aiphotographer.model

enum class Stage { LUMA, RGB, POSE, FACE, HANDS, SCENE }
class StageScheduler(private val periodsNs: Map<Stage, Long>) {
    private val inFlight = mutableSetOf<Stage>()
    private val lastStart = mutableMapOf<Stage, Long>()
    @Synchronized fun tryStart(stage: Stage, nowNs: Long): Boolean {
        val period = periodsNs[stage] ?: return false
        if (stage in inFlight) return false
        val last = lastStart[stage]
        if (last != null && nowNs - last < period) return false
        inFlight.add(stage)
        lastStart[stage] = nowNs
        return true
    }
    @Synchronized fun finish(stage: Stage) { inFlight.remove(stage) }
    @Synchronized fun reset() { inFlight.clear(); lastStart.clear() }
}
data class LatencySummary(val count: Int, val p50Ms: Double?, val p95Ms: Double?)
class LatencyWindow(private val capacity: Int = 4096) {
    private val values = LongArray(capacity)
    private var count = 0
    private var next = 0
    init { require(capacity > 0) }
    @Synchronized fun record(durationNs: Long) {
        values[next] = durationNs.coerceAtLeast(0)
        next = (next + 1) % capacity
        count = (count + 1).coerceAtMost(capacity)
    }
    @Synchronized fun clear() { count = 0; next = 0 }
    @Synchronized fun summary(): LatencySummary {
        if (count == 0) return LatencySummary(0, null, null)
        val sorted = values.copyOf(count).sortedArray()
        fun quantile(p: Double) = sorted[(kotlin.math.ceil(count * p).toInt() - 1).coerceAtLeast(0)] / 1e6
        return LatencySummary(count, quantile(.5), quantile(.95))
    }
}
