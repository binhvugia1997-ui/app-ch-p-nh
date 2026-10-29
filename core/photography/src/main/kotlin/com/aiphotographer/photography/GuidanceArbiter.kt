package com.aiphotographer.photography

/** One instruction, already carrying a message id and rule provenance. Contains no text. */
data class GuidanceInstruction(
    val id: String,
    val action: SemanticAction,
    val messageId: String,
    val params: Map<String, Float> = emptyMap(),
    val actor: Actor,
    val direction: Direction?,
    val magnitude: Float,
    val priority: Int,
    val severity: Severity,
    val confidence: Float,
    val source: InstructionSource,
    val ruleIds: List<RuleId>,
    val persistenceFrames: Int,
    val firstSeenMs: Long,
)

data class GuidanceCandidate(val instruction: GuidanceInstruction, val score: Float)

data class GuidanceState(
    val primary: GuidanceInstruction?,
    val secondary: GuidanceInstruction?,
    val neutral: NeutralReason?,
) {
    companion object {
        val neutralOnly = GuidanceState(null, null, null)
        fun neutral(reason: NeutralReason) = GuidanceState(null, null, reason)
    }
}

data class GuidanceConfig(
    val minDwellMs: Long = Thresholds.MIN_DWELL_MS,
    val cooldownMs: Long = Thresholds.COOLDOWN_MS,
    val repeatCooldownMs: Long = Thresholds.REPEAT_COOLDOWN_MS,
    val flipGuardMs: Long = Thresholds.FLIP_GUARD_MS,
    val minScore: Float = Thresholds.MIN_SCORE,
    val confidenceFloor: Float = Thresholds.CONFIDENCE_FLOOR,
    val secondaryRatio: Float = Thresholds.SECONDARY_SCORE_RATIO,
)

/**
 * Arbitration and temporal stability (`guidance-engine.md` §5, §6.2–§6.3).
 *
 * The arbiter owns exactly four decisions: at most one primary instruction, an optional secondary from a
 * different actor, when an instruction may be replaced (dwell), and when a just-resolved instruction stays
 * quiet (cooldown / repeat cooldown / flip guard). It never invents wording and never scores photos.
 */
class GuidanceArbiter(private val config: GuidanceConfig = GuidanceConfig()) {
    private data class Active(val instruction: GuidanceInstruction, val score: Float, val sinceMs: Long)

    private var active: Active? = null
    private var lastResolvedAction: SemanticAction? = null
    private var lastResolvedAt = -1L
    private val resolvedAt = mutableMapOf<RuleId, Long>()
    private val repeatedAt = mutableMapOf<String, Long>()

    fun reset() {
        active = null
        lastResolvedAction = null
        lastResolvedAt = -1L
        resolvedAt.clear()
        repeatedAt.clear()
    }

    fun update(candidates: List<GuidanceCandidate>, nowMs: Long): GuidanceState {
        val eligible = mergeComposite(candidates)
            .filter { it.score >= config.minScore && it.instruction.confidence >= config.confidenceFloor }
            .filter { !inCooldown(it.instruction, nowMs) }
            .sortedByDescending { it.score }

        val current = active
        val currentCandidate = current?.let { shown ->
            eligible.firstOrNull { it.instruction.messageId == shown.instruction.messageId }
        }

        // Rule 3 of guidance-engine §5.2: keep the current primary while its dwell time has not elapsed.
        var chosen: GuidanceCandidate? = when {
            current == null -> eligible.firstOrNull()
            currentCandidate == null -> eligible.firstOrNull()
            nowMs - current.sinceMs < config.minDwellMs -> currentCandidate
            else -> eligible.firstOrNull()?.takeIf { it.score > currentCandidate.score } ?: currentCandidate
        }

        // Rule: never reverse an instruction within the flip guard, unless it is BLOCKING.
        if (chosen != null && chosen.instruction.severity != Severity.BLOCKING && isFlipBlocked(chosen, nowMs)) {
            chosen = currentCandidate
        }

        // Bookkeeping: a cause that vanished goes quiet for cooldownMs / repeatCooldownMs.
        if (current != null && currentCandidate == null) resolve(current, nowMs)
        if (current != null && chosen != null && currentCandidate != null &&
            chosen.instruction.messageId != current.instruction.messageId
        ) {
            resolve(current, nowMs)
        }
        active = chosen?.let { candidate ->
            val since = if (current != null && current.instruction.messageId == candidate.instruction.messageId) {
                current.sinceMs
            } else {
                nowMs
            }
            Active(candidate.instruction, candidate.score, since)
        }
        val secondary = chosen?.let { first ->
            eligible.firstOrNull {
                it.instruction.actor != first.instruction.actor &&
                    it.instruction.messageId != first.instruction.messageId &&
                    it.score >= first.score * config.secondaryRatio
            }?.instruction
        }
        return GuidanceState(chosen?.instruction, secondary, null)
    }

    private fun resolve(resolved: Active, nowMs: Long) {
        lastResolvedAction = resolved.instruction.action
        lastResolvedAt = nowMs
        resolved.instruction.ruleIds.forEach { resolvedAt[it] = nowMs }
        repeatedAt[resolved.instruction.messageId] = nowMs
    }

    private fun inCooldown(instruction: GuidanceInstruction, nowMs: Long): Boolean {
        if (instruction.severity == Severity.BLOCKING) return false
        val ruleQuiet = instruction.ruleIds.any { rule -> resolvedAt[rule]?.let { nowMs - it < config.cooldownMs } == true }
        val repeatQuiet = repeatedAt[instruction.messageId]?.let { nowMs - it < config.repeatCooldownMs } == true
        return ruleQuiet || repeatQuiet
    }

    /**
     * "Never move left immediately after move right." Compared against the instruction on screen and the one
     * just resolved, because a vanished cause resolves immediately and would otherwise dodge the guard.
     */
    private fun isFlipBlocked(candidate: GuidanceCandidate, nowMs: Long): Boolean {
        val opposite = candidate.instruction.action.opposite() ?: return false
        val shown = active?.takeIf { nowMs - it.sinceMs < config.flipGuardMs }?.instruction?.action
        if (shown != null && opposite == shown) return true
        val resolvedRecently = lastResolvedAction != null && lastResolvedAt >= 0 && nowMs - lastResolvedAt < config.flipGuardMs
        return resolvedRecently && opposite == lastResolvedAction
    }

    /**
     * `MOVE_CLOSER` + `INCREASE_HEADROOM` is one physical fix ("come closer and lower the camera") and must
     * be one sentence (`guidance-engine.md` §5.4).
     */
    private fun mergeComposite(candidates: List<GuidanceCandidate>): List<GuidanceCandidate> {
        val closer = candidates.firstOrNull { it.instruction.action == SemanticAction.MOVE_CLOSER } ?: return candidates
        val headroom = candidates.firstOrNull { it.instruction.action == SemanticAction.INCREASE_HEADROOM } ?: return candidates
        val merged = GuidanceInstruction(
            id = SemanticAction.COMPOSITE.instructionId,
            action = SemanticAction.COMPOSITE,
            messageId = SemanticAction.COMPOSITE.messageKey,
            actor = Actor.PHOTOGRAPHER,
            direction = Direction.CLOSER,
            magnitude = maxOf(closer.instruction.magnitude, headroom.instruction.magnitude),
            priority = maxOf(closer.instruction.priority, headroom.instruction.priority),
            severity = if (closer.instruction.severity.weight >= headroom.instruction.severity.weight) {
                closer.instruction.severity
            } else {
                headroom.instruction.severity
            },
            confidence = minOf(closer.instruction.confidence, headroom.instruction.confidence),
            source = InstructionSource.COMPOSITION,
            ruleIds = (closer.instruction.ruleIds + headroom.instruction.ruleIds).distinct(),
            persistenceFrames = maxOf(closer.instruction.persistenceFrames, headroom.instruction.persistenceFrames),
            firstSeenMs = minOf(closer.instruction.firstSeenMs, headroom.instruction.firstSeenMs),
        )
        val mergedScore = maxOf(closer.score, headroom.score) * 1.05f
        return candidates.filter {
            it.instruction.action != SemanticAction.MOVE_CLOSER && it.instruction.action != SemanticAction.INCREASE_HEADROOM
        } + GuidanceCandidate(merged, mergedScore)
    }
}
