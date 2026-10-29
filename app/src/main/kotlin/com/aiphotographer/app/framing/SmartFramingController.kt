package com.aiphotographer.app.framing

import android.util.Log
import com.aiphotographer.model.FrameAnalysis
import com.aiphotographer.model.ShotType
import com.aiphotographer.perception.PerceptionPipeline
import com.aiphotographer.photography.EngineConfig
import com.aiphotographer.photography.GuidanceInstruction
import com.aiphotographer.photography.SmartFramingOutput
import com.aiphotographer.photography.SmartFramingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridges the perception pipeline to the pure Smart Framing engine and exposes one immutable
 * `StateFlow` for the UI (design §2.3, option A).
 *
 * Owns: the enable flag, the optional shot intent, and the engine session. Does **not** draw anything,
 * does not own the camera, and never blocks capture.
 *
 * Threading: the engine runs on `Dispatchers.Default` at the perception cadence (pose batches, ~8–15 Hz as
 * limited by `PerceptionSchedule`); nothing is scheduled here, no new timer or cadence is introduced.
 */
class SmartFramingController(
    private val perception: PerceptionPipeline?,
    private val settings: SmartFramingSettings,
    scope: CoroutineScope,
    engineConfig: EngineConfig = EngineConfig(),
) : AutoCloseable {
    private val session = SmartFramingSession(engineConfig, startEnabled = settings.enabled)
    private val mutableState = MutableStateFlow(
        SmartFramingUiState(
            enabled = settings.enabled,
            neutralReason = if (settings.enabled) null else "DISABLED",
            overlayVisible = false,
        ),
    )
    val state: StateFlow<SmartFramingUiState> = mutableState.asStateFlow()

    private var intent: ShotType? = null
    private val job: Job?

    init {
        val pipeline = perception
        job = if (pipeline == null) {
            null
        } else {
            scope.launch(Dispatchers.Default) {
                pipeline.state.collect { perceptionState ->
                    val snapshot = perceptionState.snapshot ?: return@collect
                    val analysis = FrameAnalysis(
                        timestampMs = snapshot.timestampMs,
                        frame = snapshot.frame,
                        device = snapshot.device,
                        subjects = snapshot.subjects,
                        quality = snapshot.quality,
                    )
                    try {
                        publish(session.update(analysis, intent))
                    } catch (error: RuntimeException) {
                        // The engine is pure and should not throw; never let a bad frame kill the pipeline.
                        Log.e(TAG, "Smart framing update failed", error)
                    }
                }
            }
        }
    }

    /** Immediate, no restart required. Off also clears the overlay state in the same emission (D3). */
    fun setEnabled(enabled: Boolean) {
        if (enabled == session.enabled) return
        settings.enabled = enabled
        session.setEnabled(enabled)
        mutableState.value = SmartFramingUiState(
            enabled = enabled,
            shotIntent = intent,
            neutralReason = if (enabled) null else "DISABLED",
            overlayVisible = false,
        )
    }

    val isEnabled: Boolean get() = session.enabled

    /** Optional override (`FULL_BODY` / `HALF_BODY` / `HEADSHOT`). Not required for the feature to work. */
    fun setShotIntent(value: ShotType?) {
        if (value == intent) return
        intent = value
        session.reset()
    }

    /** Call on camera switch, aspect/resolution change and lifecycle STOP→START (design §5.2 rule 5). */
    fun resetLayer() = session.reset()

    override fun close() {
        job?.cancel()
    }

    private fun publish(output: SmartFramingOutput) {
        val summary = output.summary
        val target = output.target
        mutableState.value = SmartFramingUiState(
            enabled = output.enabled,
            shotIntent = intent,
            strategyId = output.strategyId?.name,
            targetBox = target?.box?.let { RectUi(it.left, it.top, it.right, it.bottom) },
            subjectBox = output.subjectBox?.let { RectUi(it.left, it.top, it.right, it.bottom) },
            primary = output.primary?.toUi(),
            secondary = output.secondary?.toUi(),
            summary = summary?.let {
                SummaryUi(
                    bandId = it.overallBand.name,
                    bandLabelKey = it.overallBand.labelKey,
                    shotType = it.shotType.name,
                    subjectHeightFraction = it.subjectHeightFraction,
                    headroomRatio = it.headroomRatio,
                    eyeLineFromTop = it.eyeLineFromTop,
                    subjectCentreOffset = it.subjectCentreOffset,
                    cameraRollDegrees = it.cameraRollDegrees,
                )
            },
            readiness = ReadinessUi(
                state = output.readiness.state.name,
                reasons = output.readiness.reasons,
                compositionIncluded = output.readiness.compositionIncluded,
                blocksManualCapture = output.readiness.blocksManualCapture,
            ),
            neutralReason = output.neutral?.name,
            explanationKey = output.primary?.let { first -> first.ruleIds.firstOrNull()?.whyKey },
            overlayVisible = output.enabled && output.strategyId != null && target != null && output.neutral == null,
        )
    }

    private fun GuidanceInstruction.toUi() = GuidanceUi(
        instructionId = id,
        messageId = messageId,
        actor = actor.name,
        direction = direction?.name,
        magnitude = magnitude,
        severity = severity.name,
        confidence = confidence,
        ruleIds = ruleIds.map { it.id },
    )

    private companion object {
        const val TAG = "SmartFraming"
    }
}
