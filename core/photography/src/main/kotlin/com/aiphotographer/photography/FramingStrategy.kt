package com.aiphotographer.photography

import com.aiphotographer.model.ShotType

/** Which measurement the size rule compares against the band. */
enum class MeasureDomain { BODY_BBOX, FACE_BBOX }

/** What the target box hangs from. */
enum class TargetAnchor { HEAD_TOP, EYE_LINE, FEET }

/** MVP has one policy. `USER_OFF_CENTER` belongs with `COMP_*` (Phase 6, D1) and does not exist yet. */
enum class CentrePolicy { CENTERED_TOLERANT }

/** `LIMBS_SAFE` suppresses the edge-margin rule for tight crops (`composition-engine.md` §4.3). */
enum class MarginPolicy { LIMBS_SAFE, TIGHT_CROP }

enum class StrategyId { FULL_BODY_CENTERED, THREE_QUARTER_CENTERED, HALF_BODY_CENTERED, CLOSE_PORTRAIT_EYE_LINE, HEADSHOT_HEADROOM_FIRST }

/**
 * A measured target range set — not a "style". Selecting a strategy only chooses which bands apply
 * (`docs/ai-smart-framing-design.md` §4.1).
 */
data class FramingStrategy(
    val id: StrategyId,
    val shotType: ShotType,
    val domain: MeasureDomain,
    val sizeBand: HysteresisBand,
    val headroomBand: HysteresisBand,
    val anchor: TargetAnchor,
    val centrePolicy: CentrePolicy = CentrePolicy.CENTERED_TOLERANT,
    val marginPolicy: MarginPolicy = MarginPolicy.LIMBS_SAFE,
)

object FramingStrategyTable {
    val FULL_BODY = FramingStrategy(
        StrategyId.FULL_BODY_CENTERED, ShotType.FULL_BODY, MeasureDomain.BODY_BBOX,
        Thresholds.FULL_BODY_SIZE, Thresholds.FULL_BODY_HEADROOM, TargetAnchor.FEET,
    )
    val THREE_QUARTER = FramingStrategy(
        StrategyId.THREE_QUARTER_CENTERED, ShotType.THREE_QUARTER, MeasureDomain.BODY_BBOX,
        Thresholds.THREE_QUARTER_SIZE, Thresholds.THREE_QUARTER_HEADROOM, TargetAnchor.HEAD_TOP,
    )
    val HALF_BODY = FramingStrategy(
        StrategyId.HALF_BODY_CENTERED, ShotType.HALF_BODY, MeasureDomain.BODY_BBOX,
        Thresholds.HALF_BODY_SIZE, Thresholds.HALF_BODY_HEADROOM, TargetAnchor.HEAD_TOP,
    )
    val CLOSE_PORTRAIT = FramingStrategy(
        StrategyId.CLOSE_PORTRAIT_EYE_LINE, ShotType.CLOSE_PORTRAIT, MeasureDomain.FACE_BBOX,
        Thresholds.CLOSE_PORTRAIT_FACE_SIZE, Thresholds.CLOSE_PORTRAIT_HEADROOM, TargetAnchor.EYE_LINE,
        marginPolicy = MarginPolicy.TIGHT_CROP,
    )
    val HEADSHOT = FramingStrategy(
        StrategyId.HEADSHOT_HEADROOM_FIRST, ShotType.HEADSHOT, MeasureDomain.FACE_BBOX,
        Thresholds.HEADSHOT_FACE_SIZE, Thresholds.HEADSHOT_HEADROOM, TargetAnchor.EYE_LINE,
        marginPolicy = MarginPolicy.TIGHT_CROP,
    )

    val all = listOf(FULL_BODY, THREE_QUARTER, HALF_BODY, CLOSE_PORTRAIT, HEADSHOT)

    fun forShotType(type: ShotType): FramingStrategy? = all.firstOrNull { it.shotType == type }
}

/**
 * Result of selection. `strategy == null` means "abstain": no band applies, so no framing rule may fire.
 */
data class StrategySelection(
    val strategy: FramingStrategy?,
    val detected: ShotType,
    val intent: ShotType?,
    val effective: ShotType,
    val intentMismatch: Boolean,
)

/**
 * Automatic strategy selection (D2: no shot-type picker is required).
 *
 * Intent override policy (`composition-engine.md` §3, §4.4): a one-step disagreement uses the intent,
 * because people frame slightly looser than they say; a two-step disagreement keeps the detected type and
 * reports the mismatch instead of silently reframing the world.
 */
object FramingStrategySelector {
    fun select(detected: ShotType, intent: ShotType?): StrategySelection {
        if (detected == ShotType.UNKNOWN) {
            val strategy = intent?.let(FramingStrategyTable::forShotType)
            return StrategySelection(strategy, detected, intent, intent ?: ShotType.UNKNOWN, intentMismatch = false)
        }
        if (intent == null || intent == ShotType.UNKNOWN) {
            return StrategySelection(FramingStrategyTable.forShotType(detected), detected, intent, detected, false)
        }
        val distance = ShotTypeEstimator.stepDistance(detected, intent)
        return if (distance <= 1) {
            StrategySelection(FramingStrategyTable.forShotType(intent), detected, intent, intent, false)
        } else {
            StrategySelection(FramingStrategyTable.forShotType(detected), detected, intent, detected, true)
        }
    }
}
