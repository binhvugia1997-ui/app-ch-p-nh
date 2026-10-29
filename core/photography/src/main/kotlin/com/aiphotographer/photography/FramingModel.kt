package com.aiphotographer.photography

/**
 * Pure data model for the Phase 5 framing engine.
 *
 * No Android types, no user-visible text. Every visible string is referenced by a message key from
 * `specs/i18n/messages.json` (AGENTS.md rule 6), and every rule id here must exist in
 * `specs/rules/mvp-rules.json` with `phase = 5` (enforced by `SpecSyncTest`).
 */

enum class RuleCategory { FRAMING, COMPOSITION, POSE, LIGHTING, READINESS, INFORMATION }

enum class Severity(val weight: Int) {
    INFO(0), NUDGE(1), IMPORTANT(2), BLOCKING(3);

    /** A policy may lower severity (release builds never raise one). */
    fun cappedBy(ceiling: Severity): Severity = if (weight <= ceiling.weight) this else ceiling
}

enum class Actor { PHOTOGRAPHER, SUBJECT, BOTH, NONE }

enum class AbstainReason {
    NONE, NOT_APPLICABLE_SHOT_TYPE, INSUFFICIENT_VISIBILITY, SUBJECT_ABSENT, SOURCE_STALE,
    SOURCE_UNAVAILABLE, LOW_LIGHT_OR_DEGRADED_FRAME, AE_UNSTABLE, REGION_TOO_SMALL,
    TEMPLATE_NOT_ACTIVE, SUBJECT_MOVING_FAST, MODE_NOT_ACTIONABLE, COOLDOWN_ACTIVE,
}

enum class MeasurementKind {
    RATIO_OF_FRAME_HEIGHT, RATIO_OF_FRAME_WIDTH, NORMALIZED_DISTANCE, DEGREES,
    FRACTION_OF_PIXELS, LUMA_MEDIAN, LUMA_SPREAD, SCORE_0_1, COUNT, BOOLEAN,
}

enum class ThresholdStatus { SETTLED, CALIBRATION_REQUIRED, DEVICE_TUNED }

enum class InstructionSource { COMPOSITION, POSE, LIGHTING, READINESS, SYSTEM }

enum class Direction { LEFT, RIGHT, UP, DOWN, CLOSER, FARTHER, CW, CCW, TOWARD_CAMERA, AWAY_FROM_CAMERA }

/**
 * The framing rules the engine implements. Ids, category and explanation key are copied from
 * `specs/rules/mvp-rules.json` (phase 5) and `specs/i18n/messages.json` (`explanations`).
 * `FRAME_BRIGHT_BLOB_BEHIND_HEAD` is deliberately absent: it is Phase 5 in the rule file but its UI
 * inclusion is open question Q9, so it is not implemented rather than silently expanded.
 */
enum class RuleId(val id: String, val category: RuleCategory, val whyKey: String) {
    FRAME_SUBJECT_PRESENT("FRAME_SUBJECT_PRESENT", RuleCategory.FRAMING, "guidance.why.frame_subject_present"),
    FRAME_SUBJECT_TOO_SMALL("FRAME_SUBJECT_TOO_SMALL", RuleCategory.FRAMING, "guidance.why.frame_subject_too_small"),
    FRAME_SUBJECT_TOO_LARGE("FRAME_SUBJECT_TOO_LARGE", RuleCategory.FRAMING, "guidance.why.frame_subject_too_large"),
    FRAME_HEADROOM_EXCESSIVE("FRAME_HEADROOM_EXCESSIVE", RuleCategory.FRAMING, "guidance.why.frame_headroom_excessive"),
    FRAME_HEADROOM_INSUFFICIENT("FRAME_HEADROOM_INSUFFICIENT", RuleCategory.FRAMING, "guidance.why.frame_headroom_insufficient"),
    FRAME_EDGE_MARGIN("FRAME_EDGE_MARGIN", RuleCategory.FRAMING, "guidance.why.frame_edge_margin"),
    FRAME_JOINT_CROP("FRAME_JOINT_CROP", RuleCategory.FRAMING, "guidance.why.frame_joint_crop"),
    FRAME_SUBJECT_OFF_CENTER_EXTREME(
        "FRAME_SUBJECT_OFF_CENTER_EXTREME", RuleCategory.FRAMING, "guidance.why.frame_subject_off_center_extreme",
    ),
    FRAME_CAMERA_TILT("FRAME_CAMERA_TILT", RuleCategory.FRAMING, "guidance.why.frame_camera_tilt"),
    ;

    companion object {
        fun fromId(id: String): RuleId? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Closed subset of the semantic action ids (`specs/schemas/guidance-instruction.schema.json`) that the
 * FRAME_* rules may emit, each with the message key the catalog registers for it
 * (`specs/i18n/messages.json` → `actions`). Two entries may share one instruction id and differ only in
 * the message key (REFRAME variants), which the catalog allows.
 */
enum class SemanticAction(val instructionId: String, val messageKey: String, val actor: Actor) {
    WAIT("WAIT", "guidance.frame.wait_for_subject", Actor.PHOTOGRAPHER),
    MOVE_CLOSER("MOVE_CLOSER", "guidance.frame.move_closer", Actor.PHOTOGRAPHER),
    MOVE_FARTHER("MOVE_FARTHER", "guidance.frame.move_farther", Actor.PHOTOGRAPHER),
    ZOOM_IN("ZOOM_IN", "guidance.frame.zoom_in", Actor.PHOTOGRAPHER),
    ZOOM_OUT("ZOOM_OUT", "guidance.frame.zoom_out", Actor.PHOTOGRAPHER),
    INCREASE_HEADROOM("INCREASE_HEADROOM", "guidance.frame.increase_headroom", Actor.PHOTOGRAPHER),
    DECREASE_HEADROOM("DECREASE_HEADROOM", "guidance.frame.reduce_headroom", Actor.PHOTOGRAPHER),
    REFRAME("REFRAME", "guidance.frame.reframe", Actor.PHOTOGRAPHER),
    REFRAME_JOINT("REFRAME", "guidance.frame.reframe_joint", Actor.PHOTOGRAPHER),
    SUBJECT_MOVE_INWARD("SUBJECT_MOVE_INWARD", "guidance.pose.step_inward", Actor.SUBJECT),
    LEVEL_CAMERA("LEVEL_CAMERA", "guidance.frame.level_camera", Actor.PHOTOGRAPHER),
    MOVE_CAMERA_DOWN("MOVE_CAMERA_DOWN", "guidance.frame.move_camera_down", Actor.PHOTOGRAPHER),
    COMPOSITE("COMPOSITE", "guidance.frame.move_closer_lower_camera", Actor.PHOTOGRAPHER),
    ;

    /** Opposite actions: the flip guard must never alternate between them within the guard window. */
    fun opposite(): SemanticAction? = when (this) {
        MOVE_CLOSER -> MOVE_FARTHER
        MOVE_FARTHER -> MOVE_CLOSER
        ZOOM_IN -> ZOOM_OUT
        ZOOM_OUT -> ZOOM_IN
        INCREASE_HEADROOM -> DECREASE_HEADROOM
        DECREASE_HEADROOM -> INCREASE_HEADROOM
        else -> null
    }
}

data class Measurement(
    val kind: MeasurementKind,
    val value: Float,
    val secondaryValue: Float? = null,
    val landmarkRefs: List<String> = emptyList(),
    val description: String? = null,
)

/**
 * One rule evaluation. Mirrors `specs/schemas/rule-result.schema.json`; `abstainReason` is `NONE` exactly
 * when `evaluated` is true, which the schema cannot express.
 */
data class RuleResult(
    val ruleId: RuleId,
    val category: RuleCategory,
    val evaluated: Boolean,
    val abstainReason: AbstainReason,
    val severity: Severity,
    val confidence: Float,
    val measurement: Measurement?,
    val targetRange: ClosedFloatingPointRange<Float>?,
    val suggestedAction: SemanticAction?,
    val actor: Actor,
    val thresholdStatus: ThresholdStatus,
    val persistenceFrames: Int,
    val humanExplanationKey: String?,
) {
    init {
        require(confidence in 0f..1f) { "confidence out of range: $confidence" }
        require(evaluated == (abstainReason == AbstainReason.NONE)) { "abstained results must not be evaluated" }
        require(!evaluated || suggestedAction != null || severity == Severity.INFO) {
            "a firing rule needs an action unless it is informational"
        }
    }

    val fired: Boolean get() = evaluated && severity != Severity.INFO

    companion object {
        fun abstain(ruleId: RuleId, reason: AbstainReason, confidence: Float = 0f, measurement: Measurement? = null) =
            RuleResult(
                ruleId = ruleId, category = ruleId.category, evaluated = false, abstainReason = reason,
                severity = Severity.INFO, confidence = confidence.coerceIn(0f, 1f), measurement = measurement,
                targetRange = null, suggestedAction = null, actor = Actor.NONE,
                thresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED, persistenceFrames = 0,
                humanExplanationKey = ruleId.whyKey,
            )
    }
}

/** Normalized (0..1) rectangle in upright, unmirrored ANALYSIS coordinates. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(right >= left && bottom >= top) { "inverted rect" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centreX: Float get() = (left + right) / 2f
    val centreY: Float get() = (top + bottom) / 2f

    companion object {
        fun of(box: com.aiphotographer.model.BoundingBox) = NormalizedRect(
            box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(),
        )
    }
}

/** Display-only quality band (`composition-engine.md` §2.2). Its wording is `label.band.*`. */
enum class Band(val labelKey: String) {
    GOOD("label.band.good"),
    ACCEPTABLE("label.band.acceptable"),
    NEEDS_WORK("label.band.needs_work"),
}

/** Why the engine has nothing to say. Never an error; the UI shows a neutral state. */
enum class NeutralReason { NO_SUBJECT, LOW_CONFIDENCE, DEGRADED, STALE, SHOT_TYPE_UNKNOWN, DISABLED }

data class CompositionSummary(
    val shotType: com.aiphotographer.model.ShotType,
    val intendedShotType: com.aiphotographer.model.ShotType?,
    val subjectHeightFraction: Float,
    val headroomRatio: Float?,
    val eyeLineFromTop: Float?,
    val minEdgeMargin: Float?,
    val subjectCentreOffset: Float,
    val cameraRollDegrees: Float?,
    val overallBand: Band,
)
