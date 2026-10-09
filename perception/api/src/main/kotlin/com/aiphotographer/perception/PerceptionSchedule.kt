package com.aiphotographer.perception

import com.aiphotographer.model.*

/** performance-strategy §5 engineering seeds, all CALIBRATION_REQUIRED until device-tuned. */
class PerceptionSchedule(private val tier: DeviceTier) {
    var level = 0
        private set
    private var overloadedSince: Long? = null
    private var recoverySince: Long? = null
    private var stillSince: Long? = null
    private var previousPose: List<Landmark>? = null
    private var still = false
    val posePeriodMs: Double get() = 1000.0 / (if (level >= 5) 8 else if (tier == DeviceTier.LOW) 10 else 15) * if (still) 2 else 1
    val facePeriodMs: Long get() = if (level >= 3) 500 else if (still) 1000 else if (tier == DeviceTier.LOW) 200 else 100
    val useLite: Boolean get() = tier == DeviceTier.LOW || level >= 4
    val recommend480p: Boolean get() = level >= 6
    fun observe(timestampMs: Long, thermal: ThermalStatus, busyRatio: Double, poseP95Ms: Double?, frame: List<Landmark>?) {
        val pressure = (thermal != ThermalStatus.UNKNOWN && thermal >= ThermalStatus.MODERATE) || busyRatio > .15 || (poseP95Ms ?: 0.0) > 40
        if (pressure) {
            recoverySince = null
            val since = overloadedSince ?: timestampMs.also { overloadedSince = it }
            if (timestampMs - since >= 3000) { level = if (level < 3) 3 else (level + 1).coerceAtMost(6); overloadedSince = timestampMs }
        } else {
            overloadedSince = null
            val since = recoverySince ?: timestampMs.also { recoverySince = it }
            if (timestampMs - since >= 15000 && level > 0) { level = if (level == 3) 0 else level - 1; recoverySince = timestampMs }
        }
        val comparable = frame?.filter(LandmarkUsability::measurable)?.associateBy { it.id }.orEmpty()
        val prior = previousPose?.filter(LandmarkUsability::measurable)?.filter { it.id in comparable }.orEmpty()
        val motion = if (prior.isEmpty()) null else prior.map { p -> val q = comparable.getValue(p.id); kotlin.math.hypot(p.x - q.x, p.y - q.y) }.average()
        if (motion != null && motion < .003) {
            val since = stillSince ?: timestampMs.also { stillSince = it }
            still = timestampMs - since >= 1000
        } else { stillSince = null; still = false }
        previousPose = frame
    }
    fun resetMotion() { previousPose = null; stillSince = null; still = false }
    fun resetObservations() { resetMotion(); overloadedSince = null; recoverySince = null }
}
