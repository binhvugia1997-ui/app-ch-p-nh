package com.aiphotographer.photography

import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.FrameGeometry
import com.aiphotographer.model.ShotType

/** Consecutive-frame counter and hysteresis latch for one rule. */
data class RuleLatch(val active: Boolean = false, val persistenceFrames: Int = 0)

data class FramingTarget(
    val box: NormalizedRect,
    val strategyId: StrategyId,
    val anchor: TargetAnchor,
    val updatedAtMs: Long,
    val validFor: FrameGeometry,
)

data class CompositionEngineState(
    val latches: Map<RuleId, RuleLatch> = emptyMap(),
    val absentFrames: Int = 0,
    val strategyId: StrategyId? = null,
    val pendingStrategyId: StrategyId? = null,
    val pendingSinceMs: Long = -1L,
    val emaSize: Float? = null,
    val emaHeadroom: Float? = null,
    val target: FramingTarget? = null,
    val trackId: Int? = null,
    val geometry: FrameGeometry? = null,
    val lastSeenMs: Long = -1L,
) {
    fun latch(id: RuleId): RuleLatch = latches[id] ?: RuleLatch()
}

data class CompositionResult(
    val state: CompositionEngineState,
    val strategy: FramingStrategy?,
    val selection: StrategySelection?,
    val target: FramingTarget?,
    val results: List<RuleResult>,
    val summary: CompositionSummary?,
    val neutral: NeutralReason?,
    /** Measured subject box (a rectangle, never landmarks), for the overlay's thin outline. */
    val subjectBox: NormalizedRect? = null,
) {
    val firing: List<RuleResult> get() = results.filter { it.fired }
}

/**
 * Release policy: an uncalibrated threshold (`CALIBRATION_REQUIRED`) may not drive a user-visible hard
 * error (`composition-engine.md` §5, §9.2). The default configuration therefore caps such a rule at
 * `NUDGE`; a developer build may opt in to full severity for calibration sessions.
 */
data class EngineConfig(val allowUncalibratedHardErrors: Boolean = false)

/**
 * The Phase 5 composition engine: `(FrameAnalysis, state) → (result, state)`.
 *
 * Deterministic: no clock reads (time comes from `FrameAnalysis.timestampMs`), no randomness, no I/O, no
 * user-visible text. Everything that changes across frames lives in [CompositionEngineState], so tests can
 * replay a frame sequence and assert exact transitions.
 */
class CompositionEngine(private val config: EngineConfig = EngineConfig()) {

    fun initialState() = CompositionEngineState()

    fun evaluate(
        analysis: FrameAnalysis,
        intent: ShotType?,
        previous: CompositionEngineState,
        nowMs: Long = analysis.timestampMs,
    ): CompositionResult {
        val geometryChanged = previous.geometry != null && previous.geometry != analysis.frame
        val state = if (geometryChanged) initialState().copy(geometry = analysis.frame) else previous.copy(geometry = analysis.frame)

        if (analysis.quality.degradationLevel >= DEGRADATION_RESET_LEVEL) {
            return CompositionResult(
                state = initialState().copy(geometry = analysis.frame),
                strategy = null, selection = null, target = null, results = emptyList(),
                summary = null, neutral = NeutralReason.DEGRADED, subjectBox = null,
            )
        }

        val subject = FramingMeasurements.choose(analysis.subjects)
        val measurement = subject?.let { FramingMeasurements.measure(analysis, it) }
        val trackChanged = subject != null && state.trackId != null && subject.trackId != state.trackId
        val gapTooLong = measurement != null && state.lastSeenMs >= 0 &&
            nowMs - state.lastSeenMs > Thresholds.TARGET_GAP_MS && state.strategyId != null
        val base = if (trackChanged || gapTooLong) initialState().copy(geometry = analysis.frame) else state

        if (measurement == null) {
            val absent = base.absentFrames + 1
            val gate = FramingRules.subjectPresent(null, absent, latched = false, persistence = 0)
            val next = base.copy(absentFrames = absent, lastSeenMs = nowMs, latches = emptyMap(), target = null)
            return CompositionResult(
                state = next, strategy = null, selection = null, target = null,
                results = listOf(gate), summary = null, neutral = NeutralReason.NO_SUBJECT, subjectBox = null,
            )
        }

        val selection = FramingStrategySelector.select(measurement.shot.type, intent)
        val strategy = selection.strategy?.takeIf { measurement.shot.confidence >= MIN_SHOT_CONFIDENCE }
        val stale = measurement.sourceAgeMs > Thresholds.RULE_FRESHNESS_MS

        val gate = FramingRules.subjectPresent(measurement, 0, base.latch(RuleId.FRAME_SUBJECT_PRESENT).active, base.latch(RuleId.FRAME_SUBJECT_PRESENT).persistenceFrames)
        val collected = mutableListOf(gate)

        // The gate is the eligibility contract of composition-engine.md §3.2: a subject between 0.50 and
        // 0.55 is scoreable by the pose engine but must NOT produce a framing instruction.
        if (gate.fired) {
            for (rule in OTHER_RULES) collected.add(RuleResult.abstain(rule, AbstainReason.INSUFFICIENT_VISIBILITY))
            val next = base.copy(
                absentFrames = 0, lastSeenMs = nowMs, trackId = measurement.subject.trackId,
                target = null, strategyId = null, pendingStrategyId = null, pendingSinceMs = -1L,
            )
            return CompositionResult(
                next, null, selection, null, collected.map(::policy), null, NeutralReason.LOW_CONFIDENCE,
                subjectBox = NormalizedRect.of(measurement.subject.bbox),
            )
        }

        if (strategy == null || stale) {
            val reason = if (strategy == null) AbstainReason.NOT_APPLICABLE_SHOT_TYPE else AbstainReason.SOURCE_STALE
            for (rule in OTHER_RULES) collected.add(RuleResult.abstain(rule, reason))
            val neutral = if (strategy == null) NeutralReason.SHOT_TYPE_UNKNOWN else NeutralReason.STALE
            val next = base.copy(
                absentFrames = 0, lastSeenMs = nowMs, trackId = measurement.subject.trackId,
                target = null, strategyId = null, pendingStrategyId = null, pendingSinceMs = -1L,
            )
            return CompositionResult(
                next, null, selection, null, collected.map(::policy), null, neutral,
                subjectBox = NormalizedRect.of(measurement.subject.bbox),
            )
        }

        val displayed = displayedStrategy(base, strategy, nowMs)
        val raw = listOf(
            gate,
            FramingRules.subjectTooSmall(measurement, strategy, base.latch(RuleId.FRAME_SUBJECT_TOO_SMALL).active, base.latch(RuleId.FRAME_SUBJECT_TOO_SMALL).persistenceFrames),
            FramingRules.subjectTooLarge(measurement, strategy, base.latch(RuleId.FRAME_SUBJECT_TOO_LARGE).active, base.latch(RuleId.FRAME_SUBJECT_TOO_LARGE).persistenceFrames),
            FramingRules.headroomExcessive(measurement, strategy, base.latch(RuleId.FRAME_HEADROOM_EXCESSIVE).active, base.latch(RuleId.FRAME_HEADROOM_EXCESSIVE).persistenceFrames),
            FramingRules.headroomInsufficient(measurement, strategy, base.latch(RuleId.FRAME_HEADROOM_INSUFFICIENT).active, base.latch(RuleId.FRAME_HEADROOM_INSUFFICIENT).persistenceFrames),
            FramingRules.edgeMargin(measurement, strategy, base.latch(RuleId.FRAME_EDGE_MARGIN).active, base.latch(RuleId.FRAME_EDGE_MARGIN).persistenceFrames),
            FramingRules.jointCrop(measurement, strategy, base.latch(RuleId.FRAME_JOINT_CROP).persistenceFrames),
            FramingRules.offCentreExtreme(measurement, strategy, base.latch(RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME).active, base.latch(RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME).persistenceFrames),
            FramingRules.cameraTilt(base.latch(RuleId.FRAME_CAMERA_TILT).active, base.latch(RuleId.FRAME_CAMERA_TILT).persistenceFrames, measurement.rollDegrees),
        )
        val latches = raw.associate { rule ->
            val previousLatch = base.latch(rule.ruleId)
            rule.ruleId to RuleLatch(active = rule.fired, persistenceFrames = if (rule.fired) previousLatch.persistenceFrames + 1 else 0)
        }
        val evaluated = raw.map(::policy)
        val target = trackTarget(base, displayed, measurement, nowMs, analysis.frame)
        val (pendingId, pendingSince) = nextPending(base, displayed, strategy, nowMs)

        val next = base.copy(
            latches = latches,
            absentFrames = 0,
            trackId = measurement.subject.trackId,
            lastSeenMs = nowMs,
            target = target,
            strategyId = displayed.id,
            pendingStrategyId = pendingId,
            pendingSinceMs = pendingSince,
        )
        val summary = summarize(measurement, selection, evaluated, measurement.subject.shotType)
        return CompositionResult(
            next, displayed, selection, target, evaluated, summary, null,
            subjectBox = NormalizedRect.of(measurement.subject.bbox),
        )
    }

    // ---- strategy dwell --------------------------------------------------------------------------

    /**
     * The band the user is being measured against may only change after `minDwellMs` and only when the
     * whole-frame decision has settled, so the target box never jumps for one noisy frame
     * (`composition-engine.md` §4.5 step 4).
     */
    private fun displayedStrategy(state: CompositionEngineState, selected: FramingStrategy, nowMs: Long): FramingStrategy {
        val current = state.strategyId
        if (current == null) return selected
        if (current == selected.id) {
            return FramingStrategyTable.all.first { it.id == current }
        }
        val pendingSince = if (state.pendingStrategyId == selected.id) state.pendingSinceMs else nowMs
        return if (state.pendingStrategyId == selected.id && nowMs - pendingSince >= Thresholds.TARGET_MIN_DWELL_MS) {
            selected
        } else {
            FramingStrategyTable.all.first { it.id == current }
        }
    }

    private fun nextPending(state: CompositionEngineState, displayed: FramingStrategy, selected: FramingStrategy, nowMs: Long) =
        when {
            displayed.id == selected.id -> null to -1L
            state.pendingStrategyId == selected.id -> selected.id to state.pendingSinceMs
            else -> selected.id to nowMs
        }

    // ---- target box ------------------------------------------------------------------------------

    /**
     * The target is a **tolerance box derived from the two bands the engine actually measures**, not an
     * aesthetic ideal: height from the size band midpoint, top edge from the headroom band midpoint, both
     * EMA-smoothed with α = 0.2. Horizontal placement is centred because `CENTERED_TOLERANT` is the only
     * policy in the MVP (D1: `USER_OFF_CENTER` belongs to Phase 6).
     */
    private fun trackTarget(
        state: CompositionEngineState,
        strategy: FramingStrategy,
        measurement: FramingMeasurement,
        nowMs: Long,
        geometry: FrameGeometry,
    ): FramingTarget {
        val sizeMid = (strategy.sizeBand.low + strategy.sizeBand.high) / 2f
        val headMid = (strategy.headroomBand.low + strategy.headroomBand.high) / 2f
        val emaSize = ema(state.emaSize, sizeMid)
        val emaHead = ema(state.emaHeadroom, headMid)
        val top = emaHead.coerceIn(0f, 1f - MIN_TARGET_HEIGHT)
        val height = emaSize.coerceIn(MIN_TARGET_HEIGHT, 1f - top)
        val bodyAspect = if (measurement.bodyHeight > 0f) {
            (measurement.subject.bbox.right - measurement.subject.bbox.left).toFloat() / measurement.bodyHeight
        } else {
            0.75f
        }
        val width = (height * bodyAspect).coerceIn(0.05f, 1f)
        val left = ((1f - width) / 2f).coerceIn(0f, 1f - width)
        return FramingTarget(
            box = NormalizedRect(left, top, left + width, top + height),
            strategyId = strategy.id,
            anchor = strategy.anchor,
            updatedAtMs = nowMs,
            validFor = geometry,
        )
    }

    private fun ema(previous: Float?, target: Float): Float =
        previous?.let { it + Thresholds.TARGET_EMA_ALPHA * (target - it) } ?: target

    // ---- summary ---------------------------------------------------------------------------------

    private fun summarize(
        measurement: FramingMeasurement,
        selection: StrategySelection,
        results: List<RuleResult>,
        detected: ShotType,
    ): CompositionSummary {
        val band = when {
            results.any { it.fired && it.severity.weight >= Severity.IMPORTANT.weight } -> Band.NEEDS_WORK
            results.any { it.fired } -> Band.ACCEPTABLE
            else -> Band.GOOD
        }
        return CompositionSummary(
            shotType = detected,
            intendedShotType = selection.intent,
            subjectHeightFraction = measurement.bodyHeight,
            headroomRatio = measurement.headroom,
            eyeLineFromTop = measurement.eyeLine,
            minEdgeMargin = measurement.minEdgeMargin,
            subjectCentreOffset = measurement.centreOffset,
            cameraRollDegrees = measurement.rollDegrees,
            overallBand = band,
        )
    }

    // ---- policy ----------------------------------------------------------------------------------

    private fun policy(rule: RuleResult): RuleResult =
        if (!config.allowUncalibratedHardErrors &&
            rule.thresholdStatus == ThresholdStatus.CALIBRATION_REQUIRED &&
            rule.severity.weight > Severity.NUDGE.weight
        ) {
            rule.copy(severity = rule.severity.cappedBy(Severity.NUDGE))
        } else {
            rule
        }

    companion object {
        /** `docs/ai-smart-framing-design.md` §5.2 rule 5: degrade to the neutral state at this level. */
        const val DEGRADATION_RESET_LEVEL = 5
        const val MIN_SHOT_CONFIDENCE = 0.5
        private const val MIN_TARGET_HEIGHT = 0.10f

        private val OTHER_RULES = listOf(
            RuleId.FRAME_SUBJECT_TOO_SMALL, RuleId.FRAME_SUBJECT_TOO_LARGE,
            RuleId.FRAME_HEADROOM_EXCESSIVE, RuleId.FRAME_HEADROOM_INSUFFICIENT,
            RuleId.FRAME_EDGE_MARGIN, RuleId.FRAME_JOINT_CROP,
            RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME, RuleId.FRAME_CAMERA_TILT,
        )
    }
}
