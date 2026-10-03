#!/usr/bin/env python3
"""Cross-file validator for the AI Photographer specifications.

Individual JSON Schema validation is not enough: the specs reference each other
(rule -> instruction id -> message key -> text, rule <-> rule conflicts, docs -> rule ids,
templates -> landmark ids/components/mirror contract). This script validates those links.

Usage:
    python3 specs/validation/validate_specs.py [--repo-root PATH] [--quiet]

Exit code 0 = every check passed. Exit code 1 = at least one ERROR.
`WARN` lines never fail the run; they are things a reviewer should look at.

Requires: `pip install jsonschema` (JSON Schema 2020-12). If jsonschema is missing the
structural checks are skipped, the semantic checks still run.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ERRORS: list[str] = []
WARNINGS: list[str] = []


def error(msg: str) -> None:
    ERRORS.append(msg)


def warn(msg: str) -> None:
    WARNINGS.append(msg)


def load(path: Path):
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)


# --------------------------------------------------------------------------------------
# frozen expectations (audited in Phase 0; changing them is a deliberate review decision)
# --------------------------------------------------------------------------------------

# Priority order for pose templates. A rule's implementationPriority is derived from this:
# MVP phases 4-5, NEXT phase 6, etc. Keep in sync with docs/roadmap.md.
RULES_ALLOWED_PHASES = {
    "FRAME_": (5, 5),
    "COMP_": (5, 6),
    "POSE_": (4, 6),
    "LIGHT_": (5, 5),
    "READY_": (5, 5),
}
MVP_BLOCKING_SET = {
    "FRAME_SUBJECT_PRESENT",   # no subject: nothing else can be judged
    "READY_SUBJECT_STABLE",    # blocking for auto-capture; warns for manual capture
    "READY_CAMERA_STABLE",     # blocking for auto-capture; warns for manual capture
}
PREFIX_CATEGORY = {
    "FRAME_": "FRAMING",
    "COMP_": "COMPOSITION",
    "POSE_": "POSE",
    "LIGHT_": "LIGHTING",
    "READY_": "READINESS",
}
BLAZEPOSE_LANDMARKS = {
    "nose", "left_eye_inner", "left_eye", "left_eye_outer", "right_eye_inner", "right_eye",
    "right_eye_outer", "left_ear", "right_ear", "mouth_left", "mouth_right", "left_shoulder",
    "right_shoulder", "left_elbow", "right_elbow", "left_wrist", "right_wrist", "left_pinky",
    "right_pinky", "left_index", "right_index", "left_thumb", "right_thumb", "left_hip",
    "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle", "left_heel",
    "right_heel", "left_foot_index", "right_foot_index",
}
COMPONENTS = {"HEAD", "TORSO", "LEFT_ARM", "RIGHT_ARM", "LEFT_LEG", "RIGHT_LEG"}
RULE_ID_RE = re.compile(r"\b((?:FRAME|COMP|POSE|LIGHT|READY)_[A-Z0-9_]+)\b")

# Threshold statuses that are allowed to be unproven, with how many of them exist today.
# Raising these numbers requires a review note in docs/phase-status.md.
FROZEN_CALIBRATION_THRESHOLDS = 53


def check_jsonschema(root: Path) -> None:
    try:
        from jsonschema import Draft202012Validator as Validator
        from referencing import Registry, Resource
        from referencing.jsonschema import DRAFT202012
    except Exception:  # pragma: no cover
        warn("jsonschema/referencing not installed: structural schema checks skipped")
        return

    schemas = {}
    for path in sorted((root / "specs/schemas").glob("*.json")):
        try:
            schemas[path.name] = load(path)
        except Exception as exc:
            error(f"schema {path.name} is not valid JSON: {exc}")

    registry = Registry()
    for name, schema in schemas.items():
        resource = Resource.from_contents(schema, default_specification=DRAFT202012)
        registry = registry.with_resource(schema["$id"], resource)
        registry = registry.with_resource(name, resource)

    for name, schema in schemas.items():
        try:
            Validator.check_schema(schema)
        except Exception as exc:
            error(f"schema {name} is not a valid JSON Schema: {exc}")

    def validate(instance_path: Path, schema_name: str, node=None) -> None:
        instance = load(instance_path) if node is None else node
        validator = Validator(schemas[schema_name], registry=registry)
        for err in validator.iter_errors(instance):
            location = "/".join(str(p) for p in err.path) or "<root>"
            error(f"{instance_path.relative_to(root)} [{location}]: {err.message[:220]}")

    for template in sorted((root / "specs/poses").glob("*.json")):
        validate(template, "pose-template.schema.json")

    rules_doc = load(root / "specs/rules/mvp-rules.json")
    validate(root / "specs/rules/mvp-rules.json", "rule-set.schema.json")
    rule_validator = Validator(schemas["rule.schema.json"], registry=registry)
    for rule in rules_doc["rules"]:
        for err in rule_validator.iter_errors(rule):
            error(f"rule {rule.get('ruleId')}: {err.message[:220]}")

    validate(root / "specs/i18n/messages.json", "message-catalog.schema.json")


def check_guidance_chain(root: Path) -> None:
    """rule.suggestedActions -> instruction id -> message key -> text."""
    instruction_schema = load(root / "specs/schemas/guidance-instruction.schema.json")
    action_ids = set(instruction_schema["properties"]["id"]["enum"])
    catalog = load(root / "specs/i18n/messages.json")
    catalog_actions = catalog["actions"]
    messages = catalog["messages"]

    unknown_action_ids = set(catalog_actions) - action_ids
    if unknown_action_ids:
        error(f"messages.json declares actions that are not GuidanceInstruction ids: {sorted(unknown_action_ids)}")
    unlisted_ids = action_ids - set(catalog_actions)
    if unlisted_ids:
        error(f"GuidanceInstruction ids missing from the message catalog: {sorted(unlisted_ids)}")

    used_keys: set[str] = set()
    for action, entry in catalog_actions.items():
        for key in entry["messageKeys"]:
            used_keys.add(key)
            if key not in messages:
                error(f"action {action} references message key '{key}' with no text in the catalog")

    rules_doc = load(root / "specs/rules/mvp-rules.json")
    explanations = catalog.get("explanations", {})
    rule_ids = {r["ruleId"] for r in rules_doc["rules"]}
    for rid in rule_ids - set(explanations):
        error(f"rule {rid} has no entry in the catalog's explanations map (the 'why?' sheet would be empty)")
    for rid, key in explanations.items():
        if key not in messages:
            error(f"explanation for {rid} points at message key '{key}' with no text")
        used_keys.add(key)

    orphan_keys = set(messages) - used_keys
    if orphan_keys:
        warn(f"message keys defined but not reachable from any action: {sorted(orphan_keys)}")

    rules_doc = load(root / "specs/rules/mvp-rules.json")
    for rule in rules_doc["rules"]:
        if "allowedActions" not in rule:
            warn(f"rule {rule['ruleId']} has no allowedActions list")
        allowed = set(rule.get("allowedActions", []))
        suggested = set(rule.get("suggestedActions", []))
        unknown_allowed = allowed - action_ids
        if unknown_allowed:
            error(f"rule {rule['ruleId']}: allowedActions not valid instruction ids: {sorted(unknown_allowed)}")
        outside = suggested - allowed
        if outside:
            error(f"rule {rule['ruleId']}: suggestedActions outside allowedActions: {sorted(outside)}")
        # a rule may offer fixes for both actors (the arbiter shows at most one per actor), but its
        # PRIMARY action must be executable by the actor the rule belongs to.
        if rule.get("actor") in ("PHOTOGRAPHER", "SUBJECT") and rule.get("suggestedActions"):
            primary = rule["suggestedActions"][0]
            primary_actor = catalog_actions.get(primary, {}).get("actor")
            if primary_actor and primary_actor != rule["actor"]:
                error(
                    f"rule {rule['ruleId']} is a {rule['actor']} rule but its primary action {primary} is a "
                    f"{primary_actor} action"
                )
            for action in rule.get("suggestedActions", []):
                if action not in allowed:
                    error(f"rule {rule['ruleId']}: suggested action {action} is not in allowedActions")


def check_rules(root: Path) -> None:
    doc = load(root / "specs/rules/mvp-rules.json")
    rules = doc["rules"]
    ids = [r["ruleId"] for r in rules]

    duplicates = {i for i in ids if ids.count(i) > 1}
    if duplicates:
        error(f"duplicate rule ids: {sorted(duplicates)}")

    known = set(ids)
    for rule in rules:
        rid = rule["ruleId"]
        for prefix, expected in PREFIX_CATEGORY.items():
            if rid.startswith(prefix) and rule["category"] != expected:
                error(f"rule {rid}: category {rule['category']} does not match prefix {prefix}")
        low, high = next((v for p, v in RULES_ALLOWED_PHASES.items() if rid.startswith(p)), (0, 12))
        if not low <= rule["phase"] <= high:
            error(f"rule {rid}: phase {rule['phase']} outside the allowed range {low}-{high} for its family")
        if (rule["severity"] == "BLOCKING") != bool(rule["blockingAllowed"]):
            error(f"rule {rid}: severity BLOCKING and blockingAllowed disagree")
        for other in rule.get("conflictingRules", []):
            if not RULE_ID_RE.fullmatch(other):
                if RULE_ID_RE.search(other):
                    error(f"rule {rid}: conflictingRules entry '{other}' looks like a rule id but is not one")
                continue
            if other not in known:
                error(f"rule {rid}: conflictingRules references unknown rule {other}")
        # hysteresis must be on the non-firing side of the trigger
        for threshold in rule.get("thresholds", []):
            value, exit_value, direction = threshold.get("value"), threshold.get("exitValue"), threshold.get("firesWhen")
            if not isinstance(value, (int, float)) or not isinstance(exit_value, (int, float)):
                continue
            if direction == "below" and not exit_value > value:
                error(f"rule {rid} threshold {threshold['name']}: exitValue {exit_value} must be > trigger value {value} (firesWhen=below)")
            if direction == "above" and not exit_value < value:
                error(f"rule {rid} threshold {threshold['name']}: exitValue {exit_value} must be < trigger value {value} (firesWhen=above)")

    # symmetry of declared conflicts
    for rule in rules:
        for other in rule.get("conflictingRules", []):
            if other in known:
                reverse = next(r for r in rules if r["ruleId"] == other)
                if rule["ruleId"] not in reverse.get("conflictingRules", []):
                    warn(f"conflict declared one-way: {rule['ruleId']} -> {other}")

    # frozen policy snapshots
    blocking = {r["ruleId"] for r in rules if r["blockingAllowed"]}
    if blocking != MVP_BLOCKING_SET:
        error(f"blocking rule set changed: {sorted(blocking)} != frozen {sorted(MVP_BLOCKING_SET)}")

    calibration = sum(
        1 for r in rules for t in r.get("thresholds", []) if t.get("status") == "CALIBRATION_REQUIRED"
    )
    if calibration != FROZEN_CALIBRATION_THRESHOLDS:
        warn(
            f"CALIBRATION_REQUIRED threshold count is {calibration}, frozen snapshot said "
            f"{FROZEN_CALIBRATION_THRESHOLDS}: update docs/phase-status.md"
        )


def check_pose_templates(root: Path) -> None:
    templates = {p.stem: load(p) for p in sorted((root / "specs/poses").glob("*.json"))}
    for path in sorted((root / "specs/poses").glob("*.json")):
        template = templates[path.stem]
        tid = template["id"]
        if path.stem != tid:
            error(f"{path.name}: file name does not match template id {tid}")
        if template["provenance"]["status"] != "SEED_UNVALIDATED":
            warn(f"{tid}: provenance is {template['provenance']['status']} although it was authored in Phase 0")

        landmark_ids = {l["id"] for l in template["landmarks"]}
        for landmark in template["landmarks"]:
            if landmark["id"] not in BLAZEPOSE_LANDMARKS:
                error(f"{tid}: landmark id '{landmark['id']}' is not a BlazePose id")
            if landmark.get("component") and landmark["component"] not in COMPONENTS:
                error(f"{tid}: landmark {landmark['id']} has unknown component {landmark['component']}")

        for vector in template.get("relativeVectors", []):
            for key in ("from", "to"):
                if vector[key] not in landmark_ids and not vector[key].endswith(("_elbow", "_knee")):
                    error(f"{tid}: relativeVector {key}='{vector[key]}' is not a landmark in this template")

        # scale reference must exist (docs/pose-system.md §4.3)
        has_hips = {"left_hip", "right_hip"} <= landmark_ids
        has_shoulders = {"left_shoulder", "right_shoulder"} <= landmark_ids
        has_eyes = {"left_eye", "right_eye"} <= landmark_ids
        if not (has_hips and has_shoulders) and not has_shoulders and not has_eyes:
            error(f"{tid}: no usable normalization reference (needs hips+shoulders, shoulders, or eyes)")

        preferred = template.get("normalizationReference", {}).get("preferred")
        if preferred == "TORSO_LENGTH" and not (has_hips and has_shoulders):
            error(f"{tid}: declares TORSO_LENGTH normalization but does not specify both hips and both shoulders")
        if preferred == "SHOULDER_WIDTH" and not has_shoulders:
            error(f"{tid}: declares SHOULDER_WIDTH normalization but does not specify both shoulders")
        if preferred == "INTEROCULAR" and not has_eyes:
            error(f"{tid}: declares INTEROCULAR normalization but does not specify both eyes")

        required = [l["id"] for l in template["landmarks"] if l.get("required")]
        if not required:
            error(f"{tid}: no landmark is marked required")
        if has_hips and has_shoulders and not {"left_hip", "right_hip"} <= set(required):
            warn(f"{tid}: hips are specified but not required; the matcher may normalize on an unusable span")

        # mirror contract (docs/pose-system.md §4.4)
        if template["mirrorAllowed"] is False:
            if not template.get("intentFlags", {}).get("suppressSymmetryWarning"):
                error(
                    f"{tid}: mirrorAllowed=false requires intentFlags.suppressSymmetryWarning=true "
                    "(an asymmetric pose must not also be told it is asymmetric)"
                )
        if template["mirrorAllowed"] is True and template.get("maxAsymmetry") is not None:
            if template["maxAsymmetry"] > 0.30:
                error(
                    f"{tid}: mirrorAllowed=true but maxAsymmetry={template['maxAsymmetry']} > 0.30: a handed pose "
                    "must use mirrorAllowed=false plus an explicit mirrored variant, or be re-authored symmetrically"
                )
        if template["mirrorAllowed"] is False and not template.get("mirroredVariantId"):
            warn(
                f"{tid}: handed pose (mirrorAllowed=false) without a mirroredVariantId: the mirrored pose will "
                "simply not be accepted — add the twin or confirm this is deliberate"
            )
        twin_id = template.get("mirroredVariantId")
        if twin_id:
            if twin_id not in templates:
                error(f"{tid}: mirroredVariantId '{twin_id}' has no template file in specs/poses/")
            else:
                twin = templates[twin_id]
                if twin.get("mirroredVariantId") != tid:
                    error(f"{tid}: mirrored variant {twin_id} does not point back (mirroredVariantId={twin.get('mirroredVariantId')})")
                base_landmarks = {l["id"]: l for l in template["landmarks"]}
                twin_landmarks = {l["id"]: l for l in twin["landmarks"]}
                if set(base_landmarks) != set(twin_landmarks):
                    error(f"{tid}/{twin_id}: mirrored twins must specify the same landmark set")
                for landmark_id, landmark in base_landmarks.items():
                    other = twin_landmarks.get(landmark_id)
                    if not other:
                        continue
                    if abs(other["x"] + landmark["x"]) > 0.01 or abs(other["y"] - landmark["y"]) > 0.01:
                        error(f"{tid}/{twin_id}: landmark {landmark_id} is not an exact x-mirror "
                              f"({landmark['x']}, {landmark['y']}) vs ({other['x']}, {other['y']})")


def _prose_only_rule_ids(rules_md: str) -> set[str]:
    line = next((l for l in rules_md.splitlines() if "specified in prose" in l), "")
    return set(RULE_ID_RE.findall(line))


def _is_known(token: str, known: set[str]) -> bool:
    """A token is known if it is a rule id, or a family wildcard such as FRAME_HEADROOM_."""
    if token in known:
        return True
    if token.endswith("_"):
        return any(candidate.startswith(token) for candidate in known)
    return False


def check_docs_reference_rules(root: Path) -> None:
    """Every rule id that appears in docs/ must exist in the rule file, unless it is explicitly
    listed as prose-only in docs/photography-rules.md §11 (the 'specified in prose' row)."""
    rule_ids = {r["ruleId"] for r in load(root / "specs/rules/mvp-rules.json")["rules"]}
    rules_md = (root / "docs/photography-rules.md").read_text(encoding="utf-8")

    prose_only = _prose_only_rule_ids(rules_md)
    if not prose_only:
        error("could not find the prose-only rule list in docs/photography-rules.md §11")

    known = rule_ids | prose_only
    for path in sorted((root / "docs").rglob("*.md")):
        text = path.read_text(encoding="utf-8")
        for found in sorted(set(RULE_ID_RE.findall(text))):
            if not _is_known(found, known):
                error(f"{path.relative_to(root)} references rule id {found} which is not in the rule file nor in the prose-only list")
        # detect shorthand ids of the form POSE_SOMETHING that are actually prose names
        for short in sorted(set(re.findall(r"\bPOSE_[A-Z_]+\b", text))):
            if _is_known(short, known):
                continue
            if any(canonical.startswith(short) and canonical != short for canonical in known):
                error(f"{path.relative_to(root)} uses shorthand rule id {short}; use the canonical id")


def check_cross_references(root: Path) -> None:
    """Cheap structural checks over the docs (file links, message keys)."""
    docs = {p.as_posix(): p.read_text(encoding="utf-8") for p in sorted((root / "docs").rglob("*.md"))}
    docs[(root / "README.md").as_posix()] = (root / "README.md").read_text(encoding="utf-8")
    docs[(root / "AGENTS.md").as_posix()] = (root / "AGENTS.md").read_text(encoding="utf-8")

    catalog_keys = set(load(root / "specs/i18n/messages.json")["messages"])
    for name, text in docs.items():
        for key in sorted(set(re.findall(r"guidance\.[a-z0-9_]+(?:\.[a-z0-9_]+)+", text))):
            if key in catalog_keys:
                continue
            if any(real.startswith(key + ".") for real in catalog_keys):
                continue  # a group prefix such as `guidance.pose.…`, not a key
            error(f"{name}: references message key {key} which is not in specs/i18n/messages.json")

    for name, text in docs.items():
        for target in sorted(set(re.findall(r"`(specs/[A-Za-z0-9_./*-]+)`", text))):
            if "*" in target or "..." in target:
                continue  # wildcard / placeholder in prose, not a concrete path
            if not (root / target).exists():
                error(f"{name}: references {target} which does not exist")


def check_rule_docs_sync(root: Path) -> None:
    """The rule tables in docs/ and the rule file must agree on ids (the JSON is normative)."""
    rule_ids = {r["ruleId"] for r in load(root / "specs/rules/mvp-rules.json")["rules"]}
    prose_only = _prose_only_rule_ids((root / "docs/photography-rules.md").read_text(encoding="utf-8"))
    known = rule_ids | prose_only
    errors_md = (root / "docs/photography-errors.md").read_text(encoding="utf-8")
    missing = [f for f in sorted(set(RULE_ID_RE.findall(errors_md))) if not _is_known(f, known)]
    if missing:
        warn(f"docs/photography-errors.md mentions rule ids absent from the rule file: {missing}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=None)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    root = Path(args.repo_root).resolve() if args.repo_root else Path(__file__).resolve().parents[2]
    if not (root / "specs").is_dir():
        print(f"not a repository root: {root}", file=sys.stderr)
        return 1

    check_jsonschema(root)
    check_guidance_chain(root)
    check_rules(root)
    check_pose_templates(root)
    check_docs_reference_rules(root)
    check_cross_references(root)
    check_rule_docs_sync(root)

    if not args.quiet or ERRORS:
        for message in ERRORS:
            print(f"ERROR  {message}")
        for message in WARNINGS:
            print(f"WARN   {message}")

    print(f"\n{len(ERRORS)} error(s), {len(WARNINGS)} warning(s)")
    if not ERRORS:
        print("specs: OK (schemas, guidance chain, rules, templates, cross-references)")
    return 1 if ERRORS else 0


if __name__ == "__main__":
    raise SystemExit(main())
