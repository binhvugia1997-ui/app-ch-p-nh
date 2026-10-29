package com.aiphotographer.photography

import com.aiphotographer.model.Landmark
import com.aiphotographer.model.LandmarkUsability
import kotlin.math.abs

/**
 * The `FRAME_*` rules, one pure function each. No clock reads, no I/O, no state: hysteresis comes in as a
 * `latched` flag and goes out as a new severity, exactly as `composition-engine.md` §7 requires.
 *
 * Severity here is the **rule constant** from `specs/rules/mvp-rules.json`. Lowering it for uncalibrated
 * thresholds is a policy decision applied once by `CompositionEngine`, not duplicated per rule.
 */
internal object FramingRules {

    private val HEAD_IDS = setOf("left_eye", "right_eye", "left_ear", "right_ear")
    private val BODY_IDS = setOf(
        "left_shoulder", "right_shoulder", "left_hip", "right_hip",
        "left_knee", "right_knee", "left_ankle", "right_ankle",
    )
    private val LIMB_IDS = BODY_IDS + setOf(
        "left_elbow", "right_elbow", "left_wrist", "right_wrist",
        "left_foot_index", "right_foot_index",
    )

    /** Bones checked by FRAME_JOINT_CROP, as landmark-id pairs. */
    private val BONES = listOf(
        "left_shoulder" to "right_shoulder",
        "left_shoulder" to "left_elbow", "left_elbow" to "left_wrist",
        "right_shoulder" to "right_elbow", "right_elbow" to "right_wrist",
        "left_shoulder" to "left_hip", "right_shoulder" to "right_hip", "left_hip" to "right_hip",
        "left_hip" to "left_knee", "left_knee" to "left_ankle", "left_ankle" to "left_foot_index",
        "right_hip" to "right_knee", "right_knee" to "right_ankle", "right_ankle" to "right_foot_index",
    )

    // ---- gate -------------------------------------------------------------------------------------

    fun subjectPresent(
        measurement: FramingMeasurement?,
        absentFrames: Int,
        latched: Boolean,
        persistence: Int,
    ): RuleResult {
        if (measurement == null) {
            if (absentFrames < Thresholds.N_ABSENT) {
                return RuleResult.abstain(RuleId.FRAME_SUBJECT_PRESENT, AbstainReason.SUBJECT_ABSENT)
            }
            return result(
                RuleId.FRAME_SUBJECT_PRESENT, severity = Severity.BLOCKING,
                measurement = Measurement(MeasurementKind.COUNT, absentFrames.toFloat()),
                targetRange = null, action = SemanticAction.WAIT, actor = Actor.PHOTOGRAPHER,
                confidence = 0.9f, persistence = persistence,
            )
        }
        val value = measurement.visibleFraction
        val violating = if (latched) value < Thresholds.V_MIN_EXIT else value < Thresholds.V_MIN
        val measured = Measurement(MeasurementKind.SCORE_0_1, value)
        if (!violating) {
            return RuleResult(
                ruleId = RuleId.FRAME_SUBJECT_PRESENT, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_GATE, measurement, BODY_IDS),
                measurement = measured, targetRange = Thresholds.V_MIN..1f, suggestedAction = null,
                actor = Actor.NONE, thresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED,
                persistenceFrames = persistence, humanExplanationKey = RuleId.FRAME_SUBJECT_PRESENT.whyKey,
            )
        }
        return result(
            RuleId.FRAME_SUBJECT_PRESENT, severity = Severity.BLOCKING, measurement = measured,
            targetRange = Thresholds.V_MIN..1f, action = SemanticAction.WAIT, actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_GATE, measurement, BODY_IDS), persistence = persistence,
        )
    }

    // ---- subject size -----------------------------------------------------------------------------

    fun subjectTooSmall(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
    ): RuleResult {
        val value = sizeValue(measurement, strategy)
            ?: return RuleResult.abstain(RuleId.FRAME_SUBJECT_TOO_SMALL, AbstainReason.INSUFFICIENT_VISIBILITY)
        val band = strategy.sizeBand
        val violating = if (latched) !band.clearedLow(value) else value < band.low
        val measured = Measurement(MeasurementKind.RATIO_OF_FRAME_HEIGHT, value)
        if (!violating) {
            return RuleResult(
                ruleId = RuleId.FRAME_SUBJECT_TOO_SMALL, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_SIZE, measurement, domainIds(strategy)),
                measurement = measured, targetRange = band.range, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = band.status, persistenceFrames = persistence,
                humanExplanationKey = RuleId.FRAME_SUBJECT_TOO_SMALL.whyKey,
            )
        }
        return result(
            RuleId.FRAME_SUBJECT_TOO_SMALL, severity = Severity.IMPORTANT, measurement = measured,
            targetRange = band.range, action = SemanticAction.MOVE_CLOSER, actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_SIZE, measurement, domainIds(strategy)), persistence = persistence,
        )
    }

    fun subjectTooLarge(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
    ): RuleResult {
        val value = sizeValue(measurement, strategy)
            ?: return RuleResult.abstain(RuleId.FRAME_SUBJECT_TOO_LARGE, AbstainReason.INSUFFICIENT_VISIBILITY)
        val band = strategy.sizeBand
        val violating = if (latched) !band.clearedHigh(value) else value > band.high
        val measured = Measurement(MeasurementKind.RATIO_OF_FRAME_HEIGHT, value)
        if (!violating) {
            return RuleResult(
                ruleId = RuleId.FRAME_SUBJECT_TOO_LARGE, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_SIZE, measurement, domainIds(strategy)),
                measurement = measured, targetRange = band.range, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = band.status, persistenceFrames = persistence,
                humanExplanationKey = RuleId.FRAME_SUBJECT_TOO_LARGE.whyKey,
            )
        }
        return result(
            RuleId.FRAME_SUBJECT_TOO_LARGE, severity = Severity.IMPORTANT, measurement = measured,
            targetRange = band.range, action = SemanticAction.MOVE_FARTHER, actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_SIZE, measurement, domainIds(strategy)), persistence = persistence,
        )
    }

    // ---- headroom ---------------------------------------------------------------------------------

    fun headroomExcessive(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
    ): RuleResult = headroom(measurement, strategy, latched, persistence, excessive = true)

    fun headroomInsufficient(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
    ): RuleResult = headroom(measurement, strategy, latched, persistence, excessive = false)

    private fun headroom(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
        excessive: Boolean,
    ): RuleResult {
        val ruleId = if (excessive) RuleId.FRAME_HEADROOM_EXCESSIVE else RuleId.FRAME_HEADROOM_INSUFFICIENT
        val value = measurement.headroom
            ?: return RuleResult.abstain(ruleId, AbstainReason.INSUFFICIENT_VISIBILITY)
        val band = strategy.headroomBand
        val violating = if (latched) {
            if (excessive) !band.clearedHigh(value) else !band.clearedLow(value)
        } else {
            if (excessive) band.isAbove(value) else band.isBelow(value)
        }
        val measured = Measurement(MeasurementKind.RATIO_OF_FRAME_HEIGHT, value, landmarkRefs = HEAD_IDS.toList().sorted())
        if (!violating) {
            return RuleResult(
                ruleId = ruleId, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_HEADROOM, measurement, HEAD_IDS),
                measurement = measured, targetRange = band.range, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = band.status, persistenceFrames = persistence,
                humanExplanationKey = ruleId.whyKey,
            )
        }
        return result(
            ruleId, severity = Severity.IMPORTANT, measurement = measured, targetRange = band.range,
            action = if (excessive) SemanticAction.DECREASE_HEADROOM else SemanticAction.INCREASE_HEADROOM,
            actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_HEADROOM, measurement, HEAD_IDS), persistence = persistence,
        )
    }

    // ---- edge margin ------------------------------------------------------------------------------

    fun edgeMargin(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
    ): RuleResult {
        if (strategy.marginPolicy == MarginPolicy.TIGHT_CROP) {
            return RuleResult.abstain(RuleId.FRAME_EDGE_MARGIN, AbstainReason.NOT_APPLICABLE_SHOT_TYPE)
        }
        val value = measurement.minEdgeMargin
        val band = Thresholds.EDGE_MARGIN
        val violating = if (latched) !band.clearedLow(value) else band.isBelow(value)
        val measured = Measurement(MeasurementKind.RATIO_OF_FRAME_WIDTH, value)
        if (!violating) {
            return RuleResult(
                ruleId = RuleId.FRAME_EDGE_MARGIN, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_MARGIN, measurement, BODY_IDS),
                measurement = measured, targetRange = band.range, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = band.status, persistenceFrames = persistence,
                humanExplanationKey = RuleId.FRAME_EDGE_MARGIN.whyKey,
            )
        }
        return result(
            RuleId.FRAME_EDGE_MARGIN, severity = Severity.NUDGE, measurement = measured,
            targetRange = band.range, action = SemanticAction.REFRAME, actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_MARGIN, measurement, BODY_IDS), persistence = persistence,
        )
    }

    // ---- joint crop -------------------------------------------------------------------------------

    fun jointCrop(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        persistence: Int,
    ): RuleResult {
        if (strategy.marginPolicy == MarginPolicy.TIGHT_CROP) {
            return RuleResult.abstain(RuleId.FRAME_JOINT_CROP, AbstainReason.NOT_APPLICABLE_SHOT_TYPE)
        }
        val cut = clippedJoint(measurement.subject.landmarks)
        if (cut == null) {
            return RuleResult(
                ruleId = RuleId.FRAME_JOINT_CROP, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_MARGIN, measurement, LIMB_IDS),
                measurement = Measurement(MeasurementKind.BOOLEAN, 0f), targetRange = null,
                suggestedAction = null, actor = Actor.NONE, thresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED,
                persistenceFrames = persistence, humanExplanationKey = RuleId.FRAME_JOINT_CROP.whyKey,
            )
        }
        val measured = Measurement(MeasurementKind.NORMALIZED_DISTANCE, cut.second, landmarkRefs = listOf(cut.first))
        return result(
            RuleId.FRAME_JOINT_CROP, severity = Severity.NUDGE, measurement = measured,
            targetRange = Thresholds.D_JOINT..1f, action = SemanticAction.REFRAME_JOINT, actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_MARGIN, measurement, LIMB_IDS), persistence = persistence,
        )
    }

    /**
     * Finds a joint that is cut by the frame edge.
     *
     * Deviation from the prose algorithm, documented on purpose: `LandmarkUsability.measurable` requires
     * coordinates inside 0..1, so landmarks outside the frame never reach the engine and the segment
     * parameter `t` cannot be computed. The equivalent in-frame test is "endpoint within `d_joint` of an
     * edge while its bone neighbour is further inside"; that is the `d_joint` half of the specified rule
     * and it carries the same meaning (a joint sits on the frame border).
     */
    private fun clippedJoint(landmarks: List<Landmark>): Pair<String, Float>? {
        val usable = landmarks.filter(LandmarkUsability::measurable).associateBy { it.id }
        for ((a, b) in BONES) {
            val first = usable[a] ?: continue
            val second = usable[b] ?: continue
            val edgeA = edgeDistance(first)
            val edgeB = edgeDistance(second)
            if (edgeA <= Thresholds.D_JOINT && edgeB > Thresholds.D_JOINT) return a to edgeA.toFloat()
            if (edgeB <= Thresholds.D_JOINT && edgeA > Thresholds.D_JOINT) return b to edgeB.toFloat()
        }
        return null
    }

    private fun edgeDistance(landmark: Landmark): Double =
        minOf(landmark.x, landmark.y, 1.0 - landmark.x, 1.0 - landmark.y)

    // ---- off centre -------------------------------------------------------------------------------

    fun offCentreExtreme(
        measurement: FramingMeasurement,
        strategy: FramingStrategy,
        latched: Boolean,
        persistence: Int,
    ): RuleResult {
        val yaw = measurement.yawDegrees
        if (yaw != null && abs(yaw) > Thresholds.NEGATIVE_SPACE_YAW_DEGREES) {
            // Turning away is deliberate negative space, not a framing mistake (design §4.4/§4.6).
            return RuleResult.abstain(RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME, AbstainReason.NOT_APPLICABLE_SHOT_TYPE)
        }
        val value = measurement.centreOffset
        val violating = if (latched) value > Thresholds.CENTRE_OFFSET_EXIT else value > Thresholds.CENTRE_OFFSET_ENTER
        val measured = Measurement(MeasurementKind.NORMALIZED_DISTANCE, value)
        val band = 0f..Thresholds.CENTRE_OFFSET_ENTER
        if (!violating) {
            return RuleResult(
                ruleId = RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO,
                confidence = confidence(Thresholds.CAP_MARGIN, measurement, BODY_IDS),
                measurement = measured, targetRange = band, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED, persistenceFrames = persistence,
                humanExplanationKey = RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME.whyKey,
            )
        }
        return result(
            RuleId.FRAME_SUBJECT_OFF_CENTER_EXTREME, severity = Severity.NUDGE, measurement = measured,
            targetRange = band, action = SemanticAction.REFRAME, actor = Actor.PHOTOGRAPHER,
            confidence = confidence(Thresholds.CAP_MARGIN, measurement, BODY_IDS), persistence = persistence,
        )
    }

    // ---- camera tilt ------------------------------------------------------------------------------

    fun cameraTilt(latched: Boolean, persistence: Int, roll: Float?): RuleResult {
        if (roll == null) return RuleResult.abstain(RuleId.FRAME_CAMERA_TILT, AbstainReason.SOURCE_UNAVAILABLE)
        val magnitude = abs(roll)
        val violating = if (latched) magnitude > Thresholds.ROLL_EXIT_DEGREES else magnitude > Thresholds.ROLL_ENTER_DEGREES
        val measured = Measurement(MeasurementKind.DEGREES, roll)
        val band = -Thresholds.ROLL_ENTER_DEGREES..Thresholds.ROLL_ENTER_DEGREES
        if (!violating) {
            return RuleResult(
                ruleId = RuleId.FRAME_CAMERA_TILT, category = RuleCategory.FRAMING, evaluated = true,
                abstainReason = AbstainReason.NONE, severity = Severity.INFO, confidence = Thresholds.CAP_TILT,
                measurement = measured, targetRange = band, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED, persistenceFrames = persistence,
                humanExplanationKey = RuleId.FRAME_CAMERA_TILT.whyKey,
            )
        }
        return result(
            RuleId.FRAME_CAMERA_TILT, severity = Severity.NUDGE, measurement = measured, targetRange = band,
            action = SemanticAction.LEVEL_CAMERA, actor = Actor.PHOTOGRAPHER, confidence = Thresholds.CAP_TILT,
            persistence = persistence,
        )
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private fun sizeValue(measurement: FramingMeasurement, strategy: FramingStrategy): Float? =
        if (strategy.domain == MeasureDomain.FACE_BBOX) measurement.faceHeight else measurement.bodyHeight

    private fun domainIds(strategy: FramingStrategy) = if (strategy.domain == MeasureDomain.FACE_BBOX) HEAD_IDS else BODY_IDS

    /**
     * `confidence = min(cap, mean U · freshness · shot-type factor)`.
     *
     * `U(l) = (g − 0.5) / 0.5` is the normative usability of `pose-system.md` §4.1.1 — reused, not
     * redefined. A rule with unknown landmarks therefore lands at `U = 0` and cannot reach the arbiter's
     * 0.5 floor, which is exactly the "0.50–0.55 must not produce a framing instruction" contract.
     */
    fun confidence(cap: Float, measurement: FramingMeasurement, ids: Set<String>): Float {
        val usability = meanUsability(measurement.subject.landmarks, ids)
        val freshness = if (measurement.sourceAgeMs <= Thresholds.RULE_FRESHNESS_MS) {
            1f
        } else {
            (Thresholds.RULE_FRESHNESS_MS.toFloat() / measurement.sourceAgeMs.toFloat()).coerceIn(0f, 1f)
        }
        val shot = (0.5f + 0.5f * measurement.shot.confidence.toFloat()).coerceIn(0f, 1f)
        return minOf(cap, usability * freshness * shot).coerceIn(0f, 1f)
    }

    private fun meanUsability(landmarks: List<Landmark>, ids: Set<String>): Float {
        val relevant = landmarks.filter { it.id in ids }
        if (relevant.isEmpty()) return 0f
        return (relevant.sumOf { LandmarkUsability.evaluate(it).value } / relevant.size).toFloat().coerceIn(0f, 1f)
    }

    private fun result(
        ruleId: RuleId,
        severity: Severity,
        measurement: Measurement,
        targetRange: ClosedFloatingPointRange<Float>?,
        action: SemanticAction,
        actor: Actor,
        confidence: Float,
        persistence: Int,
    ) = RuleResult(
        ruleId = ruleId, category = ruleId.category, evaluated = true, abstainReason = AbstainReason.NONE,
        severity = severity, confidence = confidence.coerceIn(0f, 1f), measurement = measurement,
        targetRange = targetRange, suggestedAction = action, actor = actor,
        thresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED, persistenceFrames = persistence,
        humanExplanationKey = ruleId.whyKey,
    )
}
