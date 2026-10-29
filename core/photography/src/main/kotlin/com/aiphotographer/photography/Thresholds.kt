package com.aiphotographer.photography

/**
 * Single home for every numeric constant the framing engine uses (`composition-engine.md` §5: rule
 * constants are never inline in rule code).
 *
 * Status marking rules (AGENTS.md, "No invented numbers"):
 *  * `SETTLED`      — traced to a source and deliberately not a photographic judgement call.
 *  * `CALIBRATION_REQUIRED` — a documented proposal that has **not** been fitted to local captures. It
 *    may run in a developer build but must not drive a user-visible hard error (`EngineConfig`).
 *
 * Values marked `CALIBRATION_REQUIRED` are copied from `specs/rules/mvp-rules.json` and
 * `docs/composition-engine.md` §4; none of them is measured. `SpecSyncTest` asserts the rule ids and the
 * `V_min`/band numbers still agree with the rule file, so this table cannot drift silently.
 */

/** A band plus the values that end a violation. `clearLow > low`, `clearHigh < high`. */
data class HysteresisBand(
    val low: Float,
    val high: Float,
    val clearLow: Float,
    val clearHigh: Float,
    val status: ThresholdStatus = ThresholdStatus.CALIBRATION_REQUIRED,
) {
    init {
        require(low < high) { "empty band $low..$high" }
        require(clearLow >= low && clearLow <= high) { "clearLow outside band" }
        require(clearHigh >= low && clearHigh <= high) { "clearHigh outside band" }
    }

    val range: ClosedFloatingPointRange<Float> get() = low..high

    fun isBelow(value: Float): Boolean = value < low
    fun isAbove(value: Float): Boolean = value > high

    /** Has a "too small" violation ended (value came back inside, past the clear point)? */
    fun clearedLow(value: Float): Boolean = value >= clearLow

    /** Has a "too large" violation ended? */
    fun clearedHigh(value: Float): Boolean = value <= clearHigh
}

object Thresholds {
    // ---- framing gate (specs/rules/mvp-rules.json → FRAME_SUBJECT_PRESENT) -------------------------
    /** Framing eligibility. Not to be confused with the pose engine's 0.50 "scoreable" gate. */
    const val V_MIN = 0.55f
    const val V_MIN_EXIT = 0.60f
    const val N_ABSENT = 15
    const val GATE_CAP = 0.95f

    // ---- rule freshness (photography-rules.md §4; NOT PerceptionFreshness.MAX_AGE_MS) -------------
    /** Rule budget: a measurement older than this earns no full confidence credit. */
    const val RULE_FRESHNESS_MS = 200L

    // ---- shot-type estimator (docs/composition-engine.md §4) ---------------------------------------
    const val T_HEADSHOT = 0.30
    const val T_CLOSE = 0.20
    const val T_FULL = 0.72
    const val T_THREE_QUARTER = 0.50
    const val T_HALF = 0.28

    /** Head-top estimate: headTopY = centreY − K · radius (FRAME_HEADROOM_* → `headTopK`). */
    const val HEAD_TOP_K = 1.45
    /** Eyes/ears must be at least this visible before a head-top estimate is trusted. */
    const val HEAD_LANDMARK_MIN_VISIBILITY = 0.5

    // ---- subject size bands, keyed by the strategy that owns them ----------------------------------
    // Bands: composition-engine.md §4.3. `clear*` values are the JSON `exitValue`s where the file
    // publishes one, otherwise 10 % of the band span inside the limit (composition-engine.md §4.3 note).
    val FULL_BODY_SIZE = HysteresisBand(0.75f, 0.98f, 0.79f, 0.94f)      // fullBodyMin 0.75/0.79, fullBodyMax 0.98/0.94
    val THREE_QUARTER_SIZE = HysteresisBand(0.55f, 0.85f, 0.58f, 0.82f)
    val HALF_BODY_SIZE = HysteresisBand(0.35f, 0.60f, 0.375f, 0.575f)
    val CLOSE_PORTRAIT_FACE_SIZE = HysteresisBand(0.18f, 0.35f, 0.20f, 0.33f)
    val HEADSHOT_FACE_SIZE = HysteresisBand(0.28f, 0.45f, 0.30f, 0.43f)

    // ---- headroom bands ---------------------------------------------------------------------------
    val FULL_BODY_HEADROOM = HysteresisBand(0.03f, 0.20f, 0.045f, 0.16f)   // headshot/fullBody min+max exits
    val THREE_QUARTER_HEADROOM = HysteresisBand(0.05f, 0.25f, 0.07f, 0.23f)
    val HALF_BODY_HEADROOM = HysteresisBand(0.05f, 0.25f, 0.07f, 0.23f)
    val CLOSE_PORTRAIT_HEADROOM = HysteresisBand(0.04f, 0.18f, 0.054f, 0.166f)
    val HEADSHOT_HEADROOM = HysteresisBand(0.04f, 0.14f, 0.055f, 0.11f)

    // ---- edge margin (FRAME_EDGE_MARGIN → m_min 0.02 / exit 0.03) ----------------------------------
    val EDGE_MARGIN = HysteresisBand(0.02f, 1f, 0.03f, 1f)

    // ---- joint crop (FRAME_JOINT_CROP → t_joint 0.15, d_joint 0.03) --------------------------------
    const val T_JOINT = 0.15f
    const val D_JOINT = 0.03f

    // ---- off-centre (FRAME_SUBJECT_OFF_CENTER_EXTREME → 0.55 enter / 0.45 exit) --------------------
    const val CENTRE_OFFSET_ENTER = 0.55f
    const val CENTRE_OFFSET_EXIT = 0.45f
    /** A subject turned further than this is looking into negative space on purpose, so abstain. */
    const val NEGATIVE_SPACE_YAW_DEGREES = 30.0

    // ---- camera tilt (FRAME_CAMERA_TILT → 1.5 enter / 1.0 exit) ------------------------------------
    const val ROLL_ENTER_DEGREES = 1.5f
    const val ROLL_EXIT_DEGREES = 1.0f

    // ---- rule confidence caps (specs/rules/mvp-rules.json → confidenceCap) -------------------------
    const val CAP_GATE = 0.95f
    const val CAP_SIZE = 0.95f
    const val CAP_HEADROOM = 0.85f
    const val CAP_MARGIN = 0.90f
    const val CAP_TILT = 0.95f

    // ---- temporal stability (guidance-engine.md §5.3, §6.2) ----------------------------------------
    const val MIN_DWELL_MS = 900L
    const val COOLDOWN_MS = 2500L
    const val REPEAT_COOLDOWN_MS = 4000L
    const val FLIP_GUARD_MS = 1500L
    const val PRAISE_WINDOW_MS = 600L
    const val MIN_SCORE = 0.25f
    const val CONFIDENCE_FLOOR = 0.5f
    const val SECONDARY_SCORE_RATIO = 0.8f
    /** N-of-M voting for discrete decisions (guidance-engine.md §6.2). */
    const val VOTE_N = 3
    const val VOTE_M = 5

    // ---- target smoothing (composition-engine.md §4.5, guidance-engine.md §6.1) --------------------
    const val TARGET_EMA_ALPHA = 0.2f
    const val TARGET_MIN_DWELL_MS = 900L
    /** A subject unseen for this long invalidates the smoothed target. */
    const val TARGET_GAP_MS = 500L

    // ---- readiness (guidance-engine.md §7, mvp-rules.json → READY_*) -------------------------------
    const val READY_HOLD_MS = 400L
    val MOTION_ENERGY = HysteresisBand(0f, 0.25f, 0f, 0.19f)
    val IMU_STABILITY = HysteresisBand(0.7f, 1f, 0.75f, 1f)
    const val CAP_READY_STABLE = 0.95f
    const val CAP_READY_CAMERA = 0.90f

    // ---- arbiter score weights (guidance-engine.md §4) ---------------------------------------------
    const val PRIORITY_COMPOSITION_FRAMING = 70
    const val PRIORITY_COMPOSITION_ADVISORY = 40
    const val PRIORITY_READINESS = 100
    const val PRIORITY_LIGHTING = 80
    const val PRIORITY_POSE = 60
    const val PRIORITY_SYSTEM = 90

    fun severityFactor(severity: Severity): Float = when (severity) {
        Severity.BLOCKING -> 1.6f
        Severity.IMPORTANT -> 1.3f
        Severity.NUDGE -> 1.0f
        Severity.INFO -> 0.4f
    }
}
