# Scene Understanding — deferred module design

Status: **`LATER` (Phase 7+). Nothing in this document is MVP.** It exists so that the architecture does not
have to be redesigned later, and so that the decision is made with evidence instead of enthusiasm.

---

## 1. What scene understanding would change (and what it would not)

Scene context is only useful if it changes a *decision the app already makes*:

| Decision | Would scene context help? | How |
| --- | --- | --- |
| Which pose templates to offer | yes | outdoor street vs indoor studio vs nature vs architecture → different template rankings |
| Recommended camera height | somewhat | environment with a railing/steps suggests different height than open field |
| Background separation advice | yes | "busy background" vs "clean background" changes whether we ask the photographer to move |
| Lighting advice | yes (already partially) | backlit window indoors vs open sun outdoors |
| Subject framing distance | somewhat | tight space vs open space limits where the photographer can stand |
| Aesthetic scoring | **no** | We do not do aesthetic scoring anyway (`photography-errors.md` §4) |

It must **never**: gate capture, override measurement-based rules, or be presented as a fact about the
user's intent.

---

## 2. Candidate approaches (evaluated)

### 2.1 Zero-shot tag classification with a licence-clean image-text model — **preferred**

* Model: SigLIP / SigLIP-2 image encoder (Apache-2.0) converted to a mobile runtime, or an equivalent
  Apache/MIT image encoder (`ai-models.md` §Scene).
* Method: precompute text embeddings for a **curated, small tag vocabulary** at build time; at runtime run
  only the image encoder and a dot product. No text encoder on device.
* Advantages: one small model covers many tags; no training data needed; taxonomy can be changed without
  retraining (rebuild the prompt embedding table); licence-clean.
* Disadvantages: model size (~30–90 MB depending on variant and quantization), latency (10–60 ms),
  calibration of the similarity → confidence mapping, possible cultural/semantic drift in prompts.

### 2.2 Small supervised classifier fine-tuned on a curated taxonomy — **fallback**

* e.g. MobileNetV3/V4 or EfficientNet-Lite backbone (Apache-2.0 weights), fine-tuned offline on a small
  dataset we assemble ourselves (or a permissively licensed one), exported to TFLite.
* Advantages: small (~5–15 MB), fast, high precision for a *fixed* taxonomy.
* Disadvantages: needs training data and a training pipeline; taxonomy changes require retraining;
  dataset licensing must be checked (`model-licenses.md`).

### 2.3 Places365 classifier — **not recommended**

* Standard scene taxonomy (365 classes) but the commonly available checkpoints are heavyweight
  (VGG16 ≈ 500 MB, ResNet50 ≈ 100 MB) and the small variants are not well maintained; the weights carry
  CC-BY attribution requirements; and the taxonomy is *not* aligned with photography decisions.
* Verdict: `REJECT` for v1; `TEST` only if a MobileNet-sized Places365 checkpoint with verified licensing
  appears (see `ai-models.md`).

### 2.4 Small VLM describing the scene — **`EXPERIMENTAL`, not for the MVP**

* SmolVLM2 256M/500M class models (Apache-2.0) could plausibly run on a flagship at 1–3 s per frame.
* Why not: the latency is 100× the scene-classifier budget, the output is unstructured text that must be
  parsed deterministically anyway, and hallucination risk in a directive app is unacceptable.
* Possible future use: an *offline, optional* "photo ideas" feature that never affects live guidance.

### 2.5 On-device LLM — **`EXPERIMENTAL`, no**

* Only justified for phrasing guidance in natural language. But our guidance is a curated, localized,
  testable message catalogue; an LLM would add latency, battery cost, nondeterminism and licence
  obligations (Gemma Terms) for a *worse* result. Rejected for the core loop (`architecture.md` ADR-004).

---

## 3. Proposed tag taxonomy (photography-decision-oriented, not encyclopaedic)

| Axis | Tags | Decisions affected |
| --- | --- | --- |
| Setting | `indoor`, `outdoor` | lighting advice wording, pose difficulty |
| Environment | `urban_street`, `architecture`, `park_greenery`, `nature_open`, `water`, `home_interior`, `cafe_restaurant`, `studio_plain`, `wall_close` | template ranking, background advice |
| Space | `tight_space`, `open_space` | distance/zoom advice feasibility |
| Light quality (coarse) | `soft_even`, `harsh_directional`, `backlit`, `low_light` | lighting guidance, camera-position advice |
| Background | `clean_background`, `busy_background`, `bright_background`, `dark_background`, `horizon_visible` | separation advice, bright-blob hints |

Rules for the taxonomy:

1. Maximum ~25 tags. More tags → lower precision per tag and more UI complexity.
2. Each tag must map to at least one concrete decision, otherwise it is removed.
3. Tags must be *visible* to the user as an editable suggestion ("Có vẻ bạn đang ở ngoài trời?"), because
   the model will be wrong sometimes and the user must be able to correct it cheaply.
4. No tags about people (age, gender, expression, ethnicity, attractiveness) — forbidden by ADR-011 and by
   basic decency.

---

## 4. Integration design (for when the phase arrives)

```
SENSOR → luma/RGB downscale (e.g. 224×224, 0.5–2 Hz, on scene change)
       → SceneSource (interface already defined in :perception:api)
       → SceneReport { tags: List<ScoredTag>, confidence, modelVersion }
       → FrameAnalysis.scene
       → used ONLY by:
            • PoseRecommendationRanker (Phase 6)  — ranks templates
            • GuidanceNarrator (optional)         — chooses wording variant
            • LightingEngine                      — corroborates backlight/hard-light hints
       → exposed in the UI as an overridable chip
```

Guarantees:

* Scene reports never gate a rule; they only *reorder suggestions* or *change wording*.
* Scene inference runs at ≤ 2 Hz and is the first thing dropped when the device overheats
  (`performance-strategy.md` §degradation order).
* Scene tags are never persisted.

---

## 5. Risks

| Risk | Mitigation |
| --- | --- |
| Wrong tag → wrong suggestion | suggestions ranked, never forced; user override; only "soft" decisions depend on it |
| Model licence trap (many CLIP-family weights are research-only — see `model-licenses.md`) | only Apache-2.0/MIT weights; licence gate in ADR-013 |
| Model size pushing the APK over a size budget | quantized encoder; download-on-first-use is *not* allowed by the offline-first policy, so it must fit in the APK |
| Bias in scene classification (e.g. non-Western interiors) | keep tags coarse and non-judgmental; user-overridable; no decision is *derived* from a single tag |
| Latency/thermal cost | 0.5–2 Hz, first to be degraded, off by default on `LOW` tier |

---

## 6. Exit criteria for starting this module

Start Phase 7 scene work only when:

1. Phases 1–5 are accepted on real devices with measured performance.
2. A licence-clean encoder (Apache-2.0/MIT) is verified to run within the budget on the target `LOW`
   device tier (or is gated to `MEDIUM`+).
3. The template library is large enough that ranking templates is actually valuable (≥ 12 templates).
4. The user-facing tag-correction UI is specified.

Until then: **no scene model, no scene tags, no claims about the scene.**
