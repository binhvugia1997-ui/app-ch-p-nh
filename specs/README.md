# `specs/` — machine-readable specifications

These files exist so that Codex Local does not have to hand-port numbers, IDs and geometry out of prose.
The Markdown documents in `docs/` remain normative for **meaning**; the JSON here is normative for
**identifiers, numbers and shapes**.

| Path | What it is | Normative for |
| --- | --- | --- |
| `schemas/frame-analysis.schema.json` | the canonical per-frame snapshot passed to all engines | the perception → analysis contract |
| `schemas/pose-template.schema.json` | normalized pose template format | `docs/pose-system.md` §3 |
| `schemas/rule.schema.json` | one rule database entry | `docs/photography-rules.md` §1 |
| `schemas/rule-set.schema.json` | a versioned collection of rule entries | `docs/photography-rules.md` §12 |
| `rules/mvp-rules.json` | the rule set as data: every rule specified to implementation depth, with its `implementationPriority` and `phase` (currently Phases 4–6) | rule IDs, classes, severities, phases + threshold status |
| `schemas/rule-result.schema.json` | what a rule emits at runtime | `docs/composition-engine.md` §2.1 |
| `schemas/guidance-instruction.schema.json` | what the guidance engine emits | `docs/guidance-engine.md` §1 |
| `poses/*.json` | seed pose templates | `docs/pose-system.md` + `docs/pose-taxonomy.md` |

## Rules for editing

1. **Schema first.** If a field is needed, add it to the schema (with a description) before using it in code.
2. **IDs are permanent.** Renaming a rule ID or message ID is a breaking change: update the code, the
   docs, `strings.xml`/`values-vi/strings.xml`, and the tests in the same commit.
3. **No invented numbers.** Every `thresholds[]` entry in `rules/mvp-rules.json` carries a `status`
   (`SETTLED` / `CALIBRATION_REQUIRED` / `DEVICE_TUNED`) and, where applicable, a `source`. A
   `CALIBRATION_REQUIRED` threshold may not drive a user-visible hard error (enforced by a unit test).
4. **Seed templates are honest.** Every template authored before Phase 6 carries
   `provenance.status = "SEED_UNVALIDATED"`. The coordinates are engineering seeds, not validated art
   direction, and must be reviewed and calibrated against real captures before being shown to users.
5. **Units.** Distances in template space are torso-length units; angles are degrees; ratios are 0..1
   unless noted; image coordinates are normalized `0..1` with `y` down.
6. **Rule-file scope.** `rules/mvp-rules.json` holds every rule that is specified to implementation depth,
   with its `implementationPriority` and `phase`. A rule that exists only as prose in `docs/` is not in the
   JSON yet; add its entry when its phase begins and keep the docs and the file in sync in one commit.
