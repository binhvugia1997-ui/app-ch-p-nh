package com.aiphotographer.photography

import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.ShotType
import kotlin.math.abs

data class SmartFramingOutput(
    val enabled: Boolean,
    /** Null when Smart Framing is off: the composition engine is not called at all (D3). */
    val composition: CompositionResult?,
    val guidance: GuidanceState,
    val readiness: ReadinessReport,
    val strategyId: StrategyId?,
    val target: FramingTarget?,
    val summary: CompositionSummary?,
    val neutral: NeutralReason?,
    /** Null when disabled: the overlay must draw nothing at all (D3, "no ghost box"). */
    val subjectBox: NormalizedRect? = null,
) {
    val primary: GuidanceInstruction? get() = guidance.primary
    val secondary: GuidanceInstruction? get() = guidance.secondary
}

/**
 * Orchestrates one Smart Framing update per pose batch and owns the enable/disable contract
 * (owner decisions D2, D3, D8 / `docs/ai-smart-framing-design.md` §3.3, §3.4, §5.3).
 *
 * Enable/disable invariants enforced here and covered by tests:
 *  1. Off ⇒ the composition engine is **not called** (no target, no summary, no framing instruction).
 *  2. Off ⇒ readiness is computed from non-composition criteria only, from a fresh machine.
 *  3. Toggling in either direction resets every latch, EMA, target, arbiter cooldown and readiness hold,
 *     so turning the feature back on cannot inherit a stale `READY` or a stale instruction.
 *  4. Nothing except the composition/guidance layer is gated: perception, camera and capture are untouched.
 */
class SmartFramingSession(
    private val engineConfig: EngineConfig = EngineConfig(),
    private val guidanceConfig: GuidanceConfig = GuidanceConfig(),
    readinessConfig: ReadinessConfig = ReadinessConfig(),
    startEnabled: Boolean = true,
) {
    private val engine = CompositionEngine(engineConfig)
    private val arbiter = GuidanceArbiter(guidanceConfig)
    private val readiness = ReadinessMachine(readinessConfig)
    private val motion = MotionTracker()

    private var compositionState = engine.initialState()
    private var stabilityLatched = false
    private var cameraLatched = false

    var enabled: Boolean = startEnabled
        private set

    /** Returns true when the value actually changed (and therefore when state was reset). */
    fun setEnabled(value: Boolean): Boolean {
        if (value == enabled) return false
        enabled = value
        reset()
        return true
    }

    fun reset() {
        compositionState = engine.initialState()
        arbiter.reset()
        readiness.reset()
        motion.reset()
        stabilityLatched = false
        cameraLatched = false
    }

    fun update(analysis: FrameAnalysis, intent: ShotType? = null): SmartFramingOutput {
        val now = analysis.timestampMs
        val subject = FramingMeasurements.choose(analysis.subjects)
        val energy = if (subject == null) {
            motion.reset()
            null
        } else {
            motion.update(subject.landmarks, now)
        }

        if (!enabled) {
            val criteria = nonCompositionCriteria(analysis, subject != null, energy)
            val report = readiness.evaluate(criteria, now, compositionIncluded = false)
            return SmartFramingOutput(
                enabled = false, composition = null, guidance = GuidanceState.neutral(NeutralReason.DISABLED),
                readiness = report, strategyId = null, target = null, summary = null,
                neutral = NeutralReason.DISABLED, subjectBox = null,
            )
        }

        val composition = engine.evaluate(analysis, intent, compositionState, now)
        compositionState = composition.state
        val candidates = composition.firing.mapNotNull { candidateOf(it, now) }
        val guidance = arbiter.update(candidates, now)
        val criteria = compositionCriteria(analysis, composition, subject != null, energy)
        val report = readiness.evaluate(criteria, now, compositionIncluded = composition.strategy != null)
        return SmartFramingOutput(
            enabled = true,
            composition = composition,
            guidance = GuidanceState(guidance.primary, guidance.secondary, composition.neutral),
            readiness = report,
            strategyId = composition.strategy?.id,
            target = composition.target,
            summary = composition.summary,
            neutral = composition.neutral,
            subjectBox = composition.subjectBox,
        )
    }

    // ---- readiness criteria ----------------------------------------------------------------------

    /**
     * When Smart Framing is off, only non-composition criteria remain (D8). Subject **presence** stays
     * required — with nobody in frame there is nothing to photograph — while the edge-margin check, which
     * is a composition decision, is dropped (`guidance-engine.md` §7.1.1).
     */
    private fun nonCompositionCriteria(analysis: FrameAnalysis, present: Boolean, energy: Double?): List<ReadinessCriterion> =
        listOf(
            ReadinessCriterion(ReadinessCriterionId.SUBJECT_PRESENT, required = true, satisfied = present),
            ReadinessCriterion(ReadinessCriterionId.SUBJECT_STABLE, required = true, satisfied = stable(energy)),
            ReadinessCriterion(ReadinessCriterionId.CAMERA_STABLE, required = true, satisfied = cameraSteady(analysis)),
        )

    private fun compositionCriteria(
        analysis: FrameAnalysis,
        composition: CompositionResult,
        present: Boolean,
        energy: Double?,
    ): List<ReadinessCriterion> {
        val fired = composition.firing.map { it.ruleId }.toSet()
        val criteria = mutableListOf(
            ReadinessCriterion(
                ReadinessCriterionId.SUBJECT_PRESENT, required = true,
                satisfied = present && RuleId.FRAME_SUBJECT_PRESENT !in fired,
            ),
        )
        if (composition.strategy != null) {
            criteria += ReadinessCriterion(
                ReadinessCriterionId.SUBJECT_IN_SIZE_BAND, required = true,
                satisfied = RuleId.FRAME_SUBJECT_TOO_SMALL !in fired && RuleId.FRAME_SUBJECT_TOO_LARGE !in fired,
            )
            criteria += ReadinessCriterion(
                ReadinessCriterionId.HEADROOM_IN_BAND, required = false,
                satisfied = composition.summary?.headroomRatio != null &&
                    RuleId.FRAME_HEADROOM_EXCESSIVE !in fired && RuleId.FRAME_HEADROOM_INSUFFICIENT !in fired,
            )
            criteria += ReadinessCriterion(
                ReadinessCriterionId.EDGE_MARGIN, required = true,
                satisfied = RuleId.FRAME_EDGE_MARGIN !in fired && RuleId.FRAME_JOINT_CROP !in fired,
            )
        }
        criteria += ReadinessCriterion(ReadinessCriterionId.SUBJECT_STABLE, required = true, satisfied = stable(energy))
        criteria += ReadinessCriterion(ReadinessCriterionId.CAMERA_STABLE, required = true, satisfied = cameraSteady(analysis))
        return criteria
    }

    /** Hysteresis on the motion band: enter above `high`, clear below `clearHigh`. */
    private fun stable(energy: Double?): Boolean {
        if (energy == null) return false
        val band = Thresholds.MOTION_ENERGY
        stabilityLatched = if (stabilityLatched) energy > band.clearHigh else energy > band.high
        return !stabilityLatched
    }

    private fun cameraSteady(analysis: FrameAnalysis): Boolean {
        val imu = analysis.device.imuStability ?: return false
        val band = Thresholds.IMU_STABILITY
        cameraLatched = if (cameraLatched) imu < band.clearLow else imu < band.low
        return !cameraLatched
    }

    // ---- candidates ------------------------------------------------------------------------------

    private fun candidateOf(rule: RuleResult, nowMs: Long): GuidanceCandidate? {
        val action = rule.suggestedAction ?: return null
        val instruction = GuidanceInstruction(
            id = action.instructionId,
            action = action,
            messageId = action.messageKey,
            params = emptyMap(),
            actor = action.actor,
            direction = directionOf(action),
            magnitude = magnitudeOf(rule),
            priority = Thresholds.PRIORITY_COMPOSITION_FRAMING,
            severity = rule.severity,
            confidence = rule.confidence,
            source = InstructionSource.COMPOSITION,
            ruleIds = listOf(rule.ruleId),
            persistenceFrames = rule.persistenceFrames,
            firstSeenMs = nowMs,
        )
        val persistenceBonus = (1f + 0.05f * rule.persistenceFrames).coerceAtMost(1.35f)
        val score = Thresholds.PRIORITY_COMPOSITION_FRAMING *
            Thresholds.severityFactor(rule.severity) * rule.confidence * persistenceBonus
        return GuidanceCandidate(instruction, score)
    }

    private fun directionOf(action: SemanticAction): Direction? = when (action) {
        SemanticAction.MOVE_CLOSER, SemanticAction.ZOOM_IN, SemanticAction.COMPOSITE -> Direction.CLOSER
        SemanticAction.MOVE_FARTHER, SemanticAction.ZOOM_OUT -> Direction.FARTHER
        SemanticAction.INCREASE_HEADROOM, SemanticAction.MOVE_CAMERA_DOWN -> Direction.DOWN
        SemanticAction.DECREASE_HEADROOM -> Direction.UP
        else -> null
    }

    /** How far outside the band the measurement is, normalized to 0..1. */
    private fun magnitudeOf(rule: RuleResult): Float {
        val measurement = rule.measurement ?: return 0.5f
        val range = rule.targetRange ?: return if (measurement.kind == MeasurementKind.DEGREES) {
            (abs(measurement.value) / 10f).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val value = measurement.value
        return when {
            value < range.start -> ((range.start - value) / (range.start + 1e-4f)).coerceIn(0f, 1f)
            value > range.endInclusive -> ((value - range.endInclusive) / (1e-4f + (1f - range.endInclusive).coerceAtLeast(1e-4f)))
                .coerceIn(0f, 1f)
            else -> 0f
        }
    }
}
