# Model & Asset Licences — the redistribution gate

**This document is a gate, not a reference.** A model, dataset or SDK may ship in the APK only if its row
here says `SHIP: OK`. Anything else is `TEST` (dev builds only), `REVIEW` (needs verification), or
`REJECT`.

> Not legal advice. It is an engineering checklist written so that the project does not accidentally
> publish an APK it cannot legally distribute. When a row says `REVIEW`, the answer is "do not ship until
> the licence text has actually been read".

---

## 1. The three-layer distinction (the most important thing in this document)

| Layer | Example | What it governs |
| --- | --- | --- |
| **Code licence** | MediaPipe framework: Apache-2.0 | using/modifying the library |
| **Weight licence** | BlazePose `.task` bundles: Apache-2.0 · MobileCLIP weights: Apple ASCL/AMLR · CLIP-IQA: NTU S-Lab (non-commercial) | **distributing the model file inside the APK** |
| **Data licence** | AVA/COCO/Places365 images | training **and** redistributing derivatives, depending on terms |

A permissive code licence tells you **nothing** about the weights. "Open source" ≠ "free to ship".
"Free to use" ≠ "free to redistribute". "Research use" ≠ commercial use.

---

## 2. Asset licence register

Legend — **SHIP**: `OK` / `TEST` (dev-only) / `REVIEW` / `REJECT`.

### 2.1 Frameworks and runtimes

| Asset | Code licence | SHIP | Obligations / notes |
| --- | --- | --- | --- |
| MediaPipe (framework + Tasks SDK) | Apache-2.0 | **OK** | include Apache-2.0 text + NOTICE; keep the licence file in `third_party/` notices |
| LiteRT / TensorFlow Lite | Apache-2.0 | **OK** | same |
| ONNX Runtime Mobile | MIT | **OK** | include MIT text (Phase 8, if used) |
| CameraX / AndroidX / Jetpack Compose | Apache-2.0 | **OK** | standard |
| Kotlin stdlib / coroutines | Apache-2.0 | **OK** | standard |
| NNAPI (platform API) | OS API | **n/a** | deprecated in Android 15 — do not build on it |

### 2.2 Perception models

| Asset | Code licence | Weight licence | Commercial use | SHIP | Notes |
| --- | --- | --- | --- | --- | --- |
| **MediaPipe Pose Landmarker** (`pose_landmarker_lite/full/heavy.task`) | Apache-2.0 | **Apache-2.0** (model card: "Licensed under Apache License, Version 2.0") | yes | **OK** | attribution + licence text |
| **MediaPipe Face Landmarker** | Apache-2.0 | Apache-2.0 | yes | **OK** | same |
| **MediaPipe Hand Landmarker** | Apache-2.0 | Apache-2.0 | yes | **OK** | same |
| **MediaPipe image segmenter / selfie segmenter** (if used for masks) | Apache-2.0 | Apache-2.0 | yes | **OK** | verify per-model card at adoption time |
| **MoveNet** (TF Hub) | Apache-2.0 (repo) | Apache-2.0 per model card | yes | **REVIEW before ship** | confirm the exact checkpoint page's licence field at download time |
| **RTMPose / MMPose** | Apache-2.0 | Apache-2.0 (incl. Qualcomm AI Hub listing) | yes | **OK (Phase 8)** | verify the specific checkpoint; RTMW whole-body checkpoints are trained on COCO-WholeBody (annotation licence CC BY 4.0) |
| **Ultralytics YOLOv8/v11-pose** | **AGPL-3.0** | AGPL-3.0 | only if the whole app is AGPL, or via a paid enterprise licence | **REJECT** | AGPL reaches the app that links it; public discussions confirm internal commercial use also needs a licence |
| **ViTPose** | Apache-2.0 | Apache-2.0 | yes | **REJECT for phones** | size, not licence |
| **OpenPose** | licence varies by component (some non-commercial research terms) | — | risky | **REJECT** | not needed |

### 2.3 Scene / vision-language encoders

| Asset | Code licence | Weight licence | Commercial use | SHIP | Notes |
| --- | --- | --- | --- | --- | --- |
| **SigLIP / SigLIP-2** (Google) | Apache-2.0 | Apache-2.0 | yes | **OK (Phase 7)** | verify per-checkpoint at adoption; conversion to TFLite is our work |
| **MobileCLIP** (Apple) | MIT | **Apple ASCL/AMLR ("apple-amlr"/"apple-ascl" tags on HuggingFace; the weights file's own licence text must be read)** | likely research-oriented — **unverified** | **REJECT until verified** | the perfect example of "MIT code ≠ free weights" |
| **CLIP ViT-B/32** (OpenAI) | MIT | MIT (per HuggingFace model card) | yes | **REVIEW** | verify at adoption; not preferred anyway (size) |
| **DINOv2** (Meta) | Apache-2.0 | Apache-2.0 (relicensed from CC-BY-NC after community pressure — historical NC releases exist) | yes | **OK** | check the specific checkpoint version |
| **Places365 CNNs** (CSAIL) | MIT (repo) | **CC-BY** (attribution) per the project README; some community re-ports are GPL-3.0 | yes with attribution | **REJECT for v1** | size (VGG16 ≈ 500 MB) + maintenance; a future small port must carry CC-BY attribution |
| **ImageNet-pretrained MobileNetV3/V4, EfficientNet-Lite** | Apache-2.0 (torchvision/timm weights vary by checkpoint) | usually Apache-2.0 | yes | **OK for TEST** | attribute; verify per checkpoint |

### 2.4 Aesthetic / quality assessment

| Asset | Licence | SHIP | Notes |
| --- | --- | --- | --- |
| **NIMA** (idealo/image-quality-assessment, MobileNet on AVA) | Apache-2.0 (repo + released weights) | **OK (optional feature)** | AVA's *images* are Flickr-copyright; the released weights are Apache-2.0 — train locally if we ever fine-tune |
| **CLIP-IQA / CLIP-IQA+** | **NTU S-Lab License 1.0** (non-commercial) | **REJECT** | do not ship, do not put in a demo APK either |
| **IQA-PyTorch toolbox (TOPIQ, MUSIQ, DBCNN, …)** | **PolyForm Noncommercial 1.0.0** + NTU S-Lab for components | **REJECT** | dev-time experimentation only |
| **Q-Align / Q-ReAlign** | model licences follow their VLM backbones (often custom) | **REJECT** | size + licence |
| **BRISQUE / NIQE** | classic algorithms, implementable from the papers | **OK (if implemented ourselves)** | technical-quality telemetry only; do not present as an aesthetic score |

### 2.5 Large models (optional, experimental)

| Asset | Weight licence | Commercial | Redistribution obligations | SHIP |
| --- | --- | --- | --- | --- |
| **SmolVLM2** (256M/500M/2.2B) | Apache-2.0 | yes | include licence + NOTICE | **OK (experimental only)** |
| **Gemma 3 270M** | Gemma Terms of Use | yes | must ship the required notice ("Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms"), must pass through the Prohibited Use Policy, must mark modifications | **REVIEW (optional)** — Gemma is not OSI-open; the notice obligation is easy to get wrong |
| **Qwen2-VL / Qwen3** | Apache-2.0 (Qwen3) / Tongyi Qianwen (Qwen2-VL) | varies | varies | **REJECT for v1** |
| **moondream2** | `UNKNOWN` (changed across releases) | unverified | — | **REJECT until verified** |

### 2.6 Datasets (only relevant if we ever train)

| Dataset | Images | Annotations | Notes for us |
| --- | --- | --- | --- |
| **AVA** (aesthetics) | Flickr copyright | ratings | we do not redistribute images; fine for local experimentation |
| **COCO / COCO-WholeBody** | Flickr terms | annotations CC BY 4.0 | local experimentation; attribute if we redistribute anything derived |
| **Places365** | image owners' copyright | CC-BY for the model weights per project README | same |
| **Our own captures** | ours | ours | the only dataset we may ever ship templates from; **consent required**, never uploaded |

---

## 3. Redistribution checklist (before any release build)

- [ ] `third_party/notices/` contains the full licence text of every shipped model and library.
- [ ] An in-app **"Giấy phép / Licences"** screen lists third-party components with attribution
      (AndroidX licence tooling can help generate this).
- [ ] Apache-2.0 components: include `NOTICE` if the upstream ships one; mark modified files.
- [ ] CC-BY components (if any): attribute the creator and the source, and state the licence.
- [ ] Gemma Terms (if ever shipped): include the required notice text verbatim.
- [ ] **No** non-commercial (NTU S-Lab, PolyForm NC) or AGPL (Ultralytics) component in any build that
      leaves the developer's machine — including test APKs handed to other people.
- [ ] Play Console **Data safety** form: camera data processed on-device, not collected, not shared;
      no `INTERNET` permission in the MVP manifest makes this trivially verifiable.
- [ ] No analytics/crash SDK that transmits data (MVP policy).
- [ ] Model provenance recorded in `docs/ai-models.md` with the exact checkpoint URL and date.

---

## 4. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| L1 | Someone ships MobileCLIP weights (research licence) inside the APK | medium | high (legal + forced removal) | this document; ADR-013; PR checklist |
| L2 | An aesthetic model with a non-commercial licence sneaks in via a demo | medium | medium | explicit REJECT rows for CLIP-IQA/TOPIQ; "dev-only" is not a synonym for "safe to share" |
| L3 | Ultralytics code copied in for a person detector in Phase 8 | medium | high (AGPL contaminates the app) | documented alternative: RTMDet/RTMPose (Apache-2.0) or MediaPipe's own person detector |
| L4 | Attribution missing for Apache-2.0 components | low | low | licence screen + automated NOTICE generation in CI |
| L5 | Gemma notice obligation missed if an LLM experiment becomes a feature | low | medium | REVIEW status; feature is experimental and off by default |
| L6 | Training data licence misread if we ever fine-tune | low | medium | dataset table above; prefer our own consented captures |
| L7 | Model updates change licences silently | low | medium | record the exact checkpoint URL + date + licence text hash in this file whenever a model is added |

---

## 5. Open licence questions (`UNKNOWN` — must be resolved before the relevant phase)

1. **MobileCLIP weight terms** — read `LICENSE_weights_data` / `LICENSE_MODELS` in `apple/ml-mobileclip`
   (my attempt to fetch the raw file returned 404; the HuggingFace card carries the non-standard
   `apple-amlr` / `apple-ascl` tags). Until read: `REJECT`.
2. **MoveNet checkpoint licence** — confirm the specific TF Hub model page's licence field.
3. **CLIP ViT-B/32 checkpoint licence** — verify the HuggingFace model card version used.
4. **Places365 small-model ports** — several community ports are GPL-3.0; if we ever want places-like
   tags, prefer retraining a MobileNet on a licence-clean dataset or using SigLIP zero-shot instead.
5. **moondream2** — determine which releases are Apache-2.0 and which are not (if we ever want a VLM).
6. **Repository licence for our own code** — **RESOLVED by owner decision (2026-10-03): the repository is
   public and no open-source licence is added for now.** Consequences the agents must respect:
   * public visibility is **not** a licence — without a `LICENSE` file the default is "all rights reserved";
   * do not add a licence automatically, and do not describe the project as open source anywhere;
   * the third-party licence audit continues unchanged (this file), and the *absence* of a licence for our
     own code must not be confused with the licences of the models we ship;
   * re-opening the decision is the owner's call, at which point this file and `README.md` get updated
     together.

---

## 6. Policy statement (put in the repo, keep it true)

> AI Photographer ships only models whose **weights** may be redistributed in a closed-source application,
> keeps the required attribution, performs **all** inference on the user's device, and does not include any
> component whose terms forbid commercial use.
