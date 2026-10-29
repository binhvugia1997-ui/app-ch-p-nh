package com.aiphotographer.app.framing

import com.aiphotographer.model.ShotType

/**
 * UI-facing state for Smart Framing. Every string field is an **identifier**, never localized text: the UI
 * resolves `messageId` and `labelKey` through `specs/i18n/messages.json` / `strings.xml`.
 *
 * Coordinates are normalized (0..1) in upright, unmirrored ANALYSIS space. Projection to the preview is the
 * UI's job (`Coordinates.analysisToPreview` with `mirrored = frame.isMirrored`) — see
 * `docs/ui-integration-contract.md` §8.
 */
data class RectUi(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class GuidanceUi(
    val instructionId: String,
    val messageId: String,
    val actor: String,
    val direction: String?,
    val magnitude: Float,
    val severity: String,
    val confidence: Float,
    val ruleIds: List<String>,
)

/** Display-only quality band. `labelKey` is one of `label.band.good|acceptable|needs_work`. */
data class SummaryUi(
    val bandId: String,
    val bandLabelKey: String,
    val shotType: String,
    val subjectHeightFraction: Float,
    val headroomRatio: Float?,
    val eyeLineFromTop: Float?,
    val subjectCentreOffset: Float,
    val cameraRollDegrees: Float?,
)

data class ReadinessUi(
    val state: String,
    val reasons: List<String>,
    val compositionIncluded: Boolean,
    /** Always false: no readiness result may lock the capture button (D3). */
    val blocksManualCapture: Boolean = false,
)

data class SmartFramingUiState(
    val enabled: Boolean = SmartFramingSettings.DEFAULT_ENABLED,
    val shotIntent: ShotType? = null,
    /** Null while abstaining: no strategy means no band, so no target box may be drawn. */
    val strategyId: String? = null,
    val targetBox: RectUi? = null,
    val subjectBox: RectUi? = null,
    val primary: GuidanceUi? = null,
    val secondary: GuidanceUi? = null,
    val summary: SummaryUi? = null,
    val readiness: ReadinessUi = ReadinessUi("NOT_READY", emptyList(), compositionIncluded = false),
    /** `NO_SUBJECT`, `STALE`, `DEGRADED`, `SHOT_TYPE_UNKNOWN`, `LOW_CONFIDENCE`, `DISABLED`, or null. */
    val neutralReason: String? = null,
    /** `guidance.why.*` key for the "why?" sheet, taken from the primary instruction's first rule id. */
    val explanationKey: String? = null,
    /** The closed overlay list of design §6.1 may be drawn only when this is true. */
    val overlayVisible: Boolean = false,
)
