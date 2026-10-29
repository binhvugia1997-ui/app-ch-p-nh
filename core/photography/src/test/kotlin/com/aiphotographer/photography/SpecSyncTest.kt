package com.aiphotographer.photography

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-drift gate between the engine and `specs/`.
 *
 * The rule file is normative for ids, phases, severities and message keys (AGENTS.md rule 13). These tests
 * read the JSON as text on purpose: `:core:photography` has no JSON parser and adding one just for tests
 * would be a new dependency with no runtime consumer.
 */
class SpecSyncTest {

    private fun repoFile(relative: String): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("could not locate $relative from ${System.getProperty("user.dir")}")
    }

    private val rulesText = repoFile("specs/rules/mvp-rules.json").readText()
    private val messagesText = repoFile("specs/i18n/messages.json").readText()
    private val instructionSchema = repoFile("specs/schemas/guidance-instruction.schema.json").readText()

    private fun ruleBlock(id: String): String {
        val start = rulesText.indexOf("\"ruleId\": \"$id\"")
        assertTrue("$id is missing from specs/rules/mvp-rules.json", start >= 0)
        val next = rulesText.indexOf("\"ruleId\": \"", start + 10)
        return rulesText.substring(start, if (next > 0) next else rulesText.length)
    }

    @Test fun everyImplementedRuleExistsInTheRuleFileInPhase5() {
        for (rule in RuleId.entries) {
            val block = ruleBlock(rule.id)
            assertTrue("${rule.id} must stay in phase 5", block.contains("\"phase\": 5"))
            assertTrue("${rule.id} must keep its FRAMING category", block.contains("\"category\": \"FRAMING\""))
        }
    }

    @Test fun theGateThresholdsStillMatchTheRuleFile() {
        val block = ruleBlock(RuleId.FRAME_SUBJECT_PRESENT.id)
        assertTrue("V_min must be 0.55 in the rule file", block.contains("\"value\": 0.55"))
        assertTrue("the exit value must be 0.6 in the rule file", block.contains("\"exitValue\": 0.6"))
        assertTrue("N_absent must be 15 in the rule file", block.contains("\"value\": 15"))
        assertTrue(kotlin.math.abs(Thresholds.V_MIN - 0.55f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.V_MIN_EXIT - 0.60f) < 1e-6)
        assertTrue(Thresholds.N_ABSENT == 15)
    }

    @Test fun everyEmittedMessageKeyExistsInTheCatalog() {
        for (action in SemanticAction.entries) {
            assertTrue(
                "${action.name} emits ${action.messageKey}, which is not a catalog key",
                messagesText.contains("\"${action.messageKey}\""),
            )
        }
    }

    @Test fun everyInstructionIdExistsInTheSchemaEnum() {
        for (action in SemanticAction.entries) {
            assertTrue(
                "${action.name} emits instruction id ${action.instructionId}, which is not in the schema enum",
                instructionSchema.contains("\"${action.instructionId}\""),
            )
        }
    }

    @Test fun everyExplanationKeyIsRegisteredForItsRule() {
        for (rule in RuleId.entries) {
            assertTrue(
                "${rule.id} must map to ${rule.whyKey} in the catalog explanations",
                messagesText.contains("\"${rule.id}\": \"${rule.whyKey}\""),
            )
        }
    }

    @Test fun theBandLabelsAreCatalogKeysAndNotInstructions() {
        for (band in Band.entries) {
            assertTrue("${band.labelKey} is missing", messagesText.contains("\"${band.labelKey}\""))
            assertTrue("${band.labelKey} must stay a label.* key", band.labelKey.startsWith("label."))
        }
    }

    @Test fun theStrategyBandsDoNotExceedTheRuleFileShotTypeLadder() {
        // composition-engine.md §4 publishes the ladder; the rule file publishes the size bands. Guard the
        // two values that appear in both so a change in one place cannot silently desynchronise the other.
        val tooSmall = ruleBlock(RuleId.FRAME_SUBJECT_TOO_SMALL.id)
        assertTrue(tooSmall.contains("\"value\": 0.75"))
        assertTrue(tooSmall.contains("\"exitValue\": 0.79"))
        val tooLarge = ruleBlock(RuleId.FRAME_SUBJECT_TOO_LARGE.id)
        assertTrue(tooLarge.contains("\"value\": 0.98"))
        assertTrue(tooLarge.contains("\"exitValue\": 0.94"))
        assertTrue(kotlin.math.abs(Thresholds.FULL_BODY_SIZE.low - 0.75f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.FULL_BODY_SIZE.clearLow - 0.79f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.FULL_BODY_SIZE.high - 0.98f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.FULL_BODY_SIZE.clearHigh - 0.94f) < 1e-6)
    }

    @Test fun theHeadroomBandsMatchTheRuleFile() {
        val excessive = ruleBlock(RuleId.FRAME_HEADROOM_EXCESSIVE.id)
        assertTrue(excessive.contains("\"value\": 0.14"))
        assertTrue(excessive.contains("\"exitValue\": 0.11"))
        val insufficient = ruleBlock(RuleId.FRAME_HEADROOM_INSUFFICIENT.id)
        assertTrue(insufficient.contains("\"value\": 0.04"))
        assertTrue(insufficient.contains("\"exitValue\": 0.055"))
        assertTrue(kotlin.math.abs(Thresholds.HEADSHOT_HEADROOM.high - 0.14f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.HEADSHOT_HEADROOM.clearHigh - 0.11f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.HEADSHOT_HEADROOM.low - 0.04f) < 1e-6)
        assertTrue(kotlin.math.abs(Thresholds.HEADSHOT_HEADROOM.clearLow - 0.055f) < 1e-6)
    }

    @Test fun theRuleResultContractIsSatisfiable() {
        val produced = FramingRules.cameraTilt(latched = false, persistence = 0, roll = 4.0f)
        assertNotNull(produced.measurement)
        assertTrue(produced.evaluated)
    }
}
