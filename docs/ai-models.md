# AI Models — candidates, evaluation and selection

Scope: everything that could plausibly run **on-device, offline, at zero marginal cost** for this app.
Every candidate is evaluated with the same 18-field format (from the project brief). Facts that could not
be verified are marked **`UNKNOWN`** — they are never invented.

Licensing is summarised here for engineering decisions but the normative document is
`model-licenses.md`.

**Phase 2 adoption checkpoint (2026-10-06):** exact version-1 pose lite/full and face bundle URLs,
SHA256 hashes and model-card licensing are recorded in `model-licenses.md` §8. Models are packaged
as local assets; no runtime download. Stock Tasks SDK adoption fails privacy/runtime checks; the
official unmodified Core source build with default dummy logging and unchanged Vision artifact is
integrated after emulator SDK/adapter tests (`phase-2-sdk-audit.md`). Physical gates remain pending.
Actual model asset total is 18,934,540 bytes (both lite and full plus face). This exceeds the earlier
approximate <15 MB asset hypothesis; both pose variants support tier selection/degradation. No
validated storage/performance requirement is inferred from that estimate. No future-phase assets ship.


> **Tier policy (owner decision 2026-10-03):** MEDIUM is the primary MVP target; LOW must degrade
> gracefully (`performance-strategy.md` §5) and HIGH may enable the additional analysis listed here
> (scene tags, optional aesthetic model) — none of which is part of the MVP. LOW/HIGH numbers are
> `NOT_MEASURED` until those device classes are actually available.
---

## 0. Selection criteria

1. **Apache-2.0 / MIT** code *and* weights, or a licence that unambiguously permits redistribution in a
   closed-source APK (see `model-licenses.md`).
2. Runs on mid-range Android from ~2019 (Snapdragon 6xx/7xx class) at a usable rate.
3. Fits a total on-device budget: **≤ 120 MB of assets** for the MVP, ≤ 350 MB with optional modules.
4. Produces structured, calibrated-enough outputs (landmarks with confidence, not prose).
5. Privacy: no network, no telemetry in the model runtime.
6. Maintained: official repository, recent releases, real device benchmarks.

---

## 1. Body pose

### 1.1 MediaPipe Pose Landmarker (BlazePose GHUM 3D) — **USE (v1 primary)**

| Field | Value |
| --- | --- |
| Purpose | 33 body landmarks + world landmarks (+ optional segmentation mask) for framing, pose matching, corrections |
| Official source | `ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker` (Google AI Edge); model card: *Model Card BlazePose GHUM 3D* |
| Architecture | MobileNetV2-like CNN detector + landmark regressor; GHUM 3D body model fitted for world coordinates |
| License | **Apache-2.0** (MediaPipe code; model card states "Licensed under Apache License, Version 2.0") |
| Commercial-use implications | Permitted, including closed-source redistribution; include the Apache notice |
| Model size | lite 3 MB / full 6 MB / heavy 26 MB (model card figures; float16 task bundles ≈ 5.8 MB / 9.4 MB / larger) |
| Quantized variants | float16 bundles officially; no official int8 |
| Expected RAM | ~40–90 MB working set depending on variant + delegate (the runtime, not just weights) |
| Expected compute | detector 224×224 + landmarker 256×256 per inference |
| Android compatibility | MediaPipe Tasks Android, minSdk 24; CPU or GPU delegate (GPU requires OpenGL ES 3.1+) |
| CPU support | yes (XNNPACK) |
| GPU support | yes (OpenGL ES 3.1+; **disabled on the Android emulator**) |
| NPU | not via MediaPipe Tasks today; LiteRT NPU paths exist but are out of scope for v1 |
| Runtime options | MediaPipe Tasks (recommended), or raw TFLite/LiteRT of the same `.task` bundles |
| Expected performance class | Model card (Pixel 3, historical): lite ≈ 44 FPS CPU / 49 FPS GPU; full ≈ 18 CPU / 40 GPU; heavy ≈ 4 CPU / 19 GPU. **Treat as an upper bound for 2018 hardware; re-measure on target devices.** |
| Advantages | richest landmark set (33 incl. ears/feet/hands), visibility + presence per landmark, world coordinates for orientation/foreshortening, purpose-built for mobile, easy integration with CameraX |
| Disadvantages | **single person only** (model card lists multiple people as out of scope), degradation beyond ~4 m, head must be visible, z is not metric, jitter present in tough conditions |
| Integration complexity | Low (Tasks API + a bitmap/ImageProcessing pipeline) |
| Privacy | fully on-device; MediaPipe Tasks does not send input to Google |
| Recommended phase | Phase 2 (lite model first), Phase 10 (choose per tier) |
| **Recommendation** | **USE** — `pose_landmarker_lite` as the default on `LOW`, `_full` on `MEDIUM`/`HIGH`, `_heavy` never by default |

**Model-card constraints that shape the whole product (from the official model card):**

* out of scope: *multiple people in an image*, **people further than ~4 m (14 ft)**, head not visible,
  metric-accurate depth, any surveillance/identity recognition;
* in-service limitation: *"tracks only one person on scene if multiple present"*;
* the model expects a single person centred, cropped with ~25 % margin around the body square;
* tolerance: ~10 % shift/scale, ~8° roll — **we must not expect accuracy beyond that**;
* accuracy (PDJ, tracking mode) per region: lite ≈ 84–89, full ≈ 90–93, heavy ≈ 93–95.

### 1.2 MoveNet (Lightning / Thunder) — **TEST (fallback / alternative)**

| Field | Value |
| --- | --- |
| Purpose | Fast single-person 17-keypoint pose |
| Source | TensorFlow Hub / TF.js pose-detection (`movenet`) |
| Architecture | CenterNet-style heatmap regressor |
| License | Apache-2.0 (TF Hub model card) — **verify the exact checkpoint's page before shipping** |
| Model size | Lightning ~2.9 MB (int8 TFLite), Thunder ~6.3 MB (fp16 TFLite) |
| Expected performance | Official claims "30+ FPS on most modern phones" (Lightning, 192×192; Thunder 256×256); TF.js WebGL benchmarks show Pixel 5 ≈ 34 FPS Lightning |
| Android compatibility | TFLite/LiteRT or MediaPipe-style wrapper; **no first-party Android task API**, so preprocessing/postprocessing must be written |
| Advantages | very small/fast, mature, well documented, licence-clean |
| Disadvantages | 17 keypoints (no separate ears/feet detail, no world landmarks), no visibility/presence model per keypoint (only scores), multipose requires the TF.js "multipose" variant which is not practical on Android |
| Integration complexity | Medium (own TFLite pipeline + letterboxing + cropping) |
| Privacy | on-device |
| Recommended phase | only if BlazePose fails a device/perf gate (Phase 10 contingency) |
| **Recommendation** | **TEST** as contingency; do not dual-source in the MVP |

### 1.3 RTMPose (s/m) — **TEST for multi-person (Phase 8)**

| Field | Value |
| --- | --- |
| Purpose | Top-down multi-person 2D pose (COCO 17 or whole-body 133 with RTMW) |
| Source | OpenMMLab MMPose `projects/rtmpose`; Qualcomm AI Hub hosts RTMPose-Body2d |
| Architecture | RTMDet detector + CSPNeXt backbone with SimCC head |
| License | **Apache-2.0** (code and model weights; Qualcomm AI Hub lists the model as Apache-2.0) |
| Model size | RTMPose-Body2d: 68.5 MB float, **18.2 MB w8a16**, 17.9 M parameters |
| Expected performance | Paper: RTMPose-s 72.2 % AP at 70+ FPS on Snapdragon 865; RTMPose-m 75.8 % AP, 90+ FPS on desktop CPU, 35+ FPS on Snapdragon 865 |
| Android compatibility | via ONNX Runtime Mobile or ncnn/MMDeploy (not MediaPipe Tasks) |
| Advantages | genuinely multi-person, strong accuracy, permissive licence, quantized variants |
| Disadvantages | two-stage pipeline (detector + per-person pose) is heavier than BlazePose; requires a second runtime (ONNX Runtime) in the app; more integration work |
| Privacy | on-device |
| Recommended phase | **Phase 8 (multi-person)**, evaluated against "person detector + BlazePose per ROI" |
| **Recommendation** | **TEST in Phase 8**; the leading candidate to replace BlazePose when multi-person becomes a requirement |

### 1.4 ViTPose / OpenPose / YOLO-pose

| Model | Verdict | Reason |
| --- | --- | --- |
| ViTPose | **REJECT for real-time** | transformer-based, too heavy for mid-range phones; fine for offline labelling tooling |
| OpenPose | **REJECT** | heavyweight, aging, not mobile-friendly |
| Ultralytics YOLOv8/YOLO11-pose | **REJECT** | AGPL-3.0 (or a paid enterprise licence) — incompatible with a closed-source APK; see `model-licenses.md` |

---

## 2. Face

### 2.1 MediaPipe Face Landmarker — **USE (v1 primary)**

| Field | Value |
| --- | --- |
| Purpose | 478 face landmarks, head pose (facial transformation matrix), iris (gaze), optional 52 blendshapes |
| Source | `ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker` |
| Architecture | BlazeFace-like detector (192×192) + FaceMesh-V2 (256×256) + optional blendshape model |
| License | Apache-2.0 |
| Commercial use | permitted |
| Model size | task bundle a few MB (detector + mesh, float16) |
| Expected RAM | ~30–60 MB |
| Android compatibility | MediaPipe Tasks, minSdk 24, CPU/GPU |
| Expected performance class | Vendor-documented face-mesh inference ~3.6 ms (detector) + ~11.4 ms (mesh) on **GPU** for a Pixel 9-class device, but real apps have measured 30–70 ms total including pipeline overhead; treat as `CALIBRATION_REQUIRED` per device |
| Advantages | head pose matrix + iris landmarks come free (we need head yaw/pitch/roll and gaze for `COMP_LEAD_ROOM`, chin tilt, and headshot templates); numFaces=1 enables built-in smoothing |
| Disadvantages | per-frame cost; blendshapes cost extra (we do **not** need them in MVP); multiple faces need numFaces>1 and lose smoothing |
| Integration complexity | Low |
| Privacy | on-device |
| Recommended phase | Phase 2 |
| **Recommendation** | **USE** (landmarks + transformation matrix; **blendshapes OFF** in MVP) |

### 2.2 Alternatives considered

| Model | Verdict | Reason |
| --- | --- | --- |
| ML Kit Face Detection (on-device) | **OPTIONAL / fallback** | free and on-device, but a closed-source Google Play Services dependency, no 478-point mesh, and it adds a second face stack; only worth it if Face Landmarker fails a performance gate |
| BlazeFace standalone | **OPTIONAL** | useful as a cheap "is there a face" gate if we ever need to decouple detection from meshing |
| MediaPipe Face Mesh (legacy Solutions API) | **REJECT** | superseded by Tasks API; legacy solutions are in maintenance |
| 3DDFA / DECA / EMOCA | **REJECT** | research-oriented, heavy, licence-varied, and we do not need full 3D face reconstruction |

---

## 3. Hands (deferred)

### 3.1 MediaPipe Hand Landmarker — **OPTIONAL (Phase 5+)**

| Field | Value |
| --- | --- |
| Purpose | 21 hand landmarks ×2 hands; needed for hand-related posing guidance (`POSE_WRIST_ANGLE_EXTREME`, fingers relaxed, hand away from face) |
| Source | `ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker` |
| Architecture | BlazePalm detector (192×192) + hand landmark model (224×224), tracking-first |
| License | Apache-2.0 |
| Model size | a few MB (float16 bundle) |
| Expected performance | Official task benchmark: Pixel 6 CPU 17.12 ms / GPU 12.27 ms |
| Advantages | high-fidelity hands; tracking means the detector runs rarely |
| Disadvantages | cost stacks on top of pose + face; BlazePose already gives coarse hand proxies (index/pinky/thumb + wrist) which are enough for **wrist angle** and *some* finger checks |
| Recommended phase | hands only when the finger-level corrections are actually specified (`NEXT`); MVP uses BlazePose's wrist/hand proxies |
| **Recommendation** | **OPTIONAL** — on-demand only (triggered when the active template involves hands near the face/torso) |

---

## 4. Scene understanding (Phase 7, deferred — see `scene-understanding.md`)

| Model | Licence | Size | Verdict |
| --- | --- | --- | --- |
| **SigLIP / SigLIP-2 image encoder** (Google) | **Apache-2.0** (code and weights) | base ≈ 90 M params fp16 ≈ 180 MB → int8 ≈ 45–90 MB (too big for the MVP budget; smaller variants exist) | **TEST (Phase 7)** — preferred zero-shot tag classifier |
| **MobileCLIP** (Apple) | code MIT, **weights under Apple's AMLR/ASCL terms (not a standard permissive licence; verify)** — the weights are widely described as research-oriented | S0 ≈ 40 MB, S1/S2 larger | **REJECT for shipping** until the weight licence is verified as commercial-redistribution-friendly; remember code licence ≠ weight licence |
| **CLIP ViT-B/32** (OpenAI) | code MIT; HF model card lists MIT for the checkpoint (verify before use) | ≈ 150 MB fp32 / 40 MB int8 | **TEST** — heavier than SigLIP at similar quality; not preferred |
| **MobileNetV3/V4 / EfficientNet-Lite ImageNet** | Apache-2.0 (weights) | 4–20 MB | **USE as a weak baseline** (e.g. indoor/outdoor-ish coarse "place" signal) — but ImageNet classes are a poor fit for photography decisions |
| **Places365 checkpoints** (CSAIL) | weights **CC-BY** (attribution) per the project README; the popular small variants are GPL-3.0 in some ports | VGG16 ≈ 500 MB, ResNet50 ≈ 100 MB | **REJECT for v1** — size/licence/maintenance |
| **DINOv2** (Meta) | Apache-2.0 | base ≈ 86 M params | **OPTIONAL** — strong features, but needs a trained head for tags; no benefit over SigLIP for our purposes |

---

## 5. Aesthetic assessment (optional, later)

| Model | Licence | Verdict |
| --- | --- | --- |
| **NIMA** (Talebi & Milanfar) — community implementation `idealo/image-quality-assessment`, MobileNet backbone trained on AVA | **Apache-2.0** (repo and weights; the AVA dataset's underlying images remain Flickr-copyright — the *weights* are released Apache-2.0) | **TEST (optional, `EXPERIMENTAL` feature)** — the only mainstream aesthetic model with a clean licence; MobileNet variant ≈ 15 MB; must be presented as a heuristic, never as a verdict, and never used for readiness |
| **CLIP-IQA / CLIP-IQA+** | **NTU S-Lab License 1.0 — non-commercial** | **REJECT** |
| **TOPIQ, MUSIQ, DBCNN and the rest of IQA-PyTorch** | **PolyForm Noncommercial 1.0.0** | **REJECT** |
| **Q-Align / Q-ReAlign** | large VLM backbones (0.8 B–9 B) | **REJECT** (size + licence + latency) |
| **MUSIQ / NIQE / BRISQUE** | classic no-reference metrics; BRISQUE/NIQE are simple and implementable from the paper — but they measure *technical* quality (noise/blur), not aesthetics | **OPTIONAL** as cheap blur/noise telemetry, not as a score for the user |

> **Product stance:** no numeric "photo beauty" score in MVP. If NIMA is added later, it lives behind an
> explicit "gợi ý" (suggestions) surface and never blocks anything.

---

## 6. Vision-language models (optional, experimental only)

| Model | Licence | Size | On-device feasibility | Verdict |
| --- | --- | --- | --- | --- |
| **SmolVLM2-256M / 500M** (HuggingFace) | **Apache-2.0** | 256 M / 500 M params; 500 M video inference reported at ~1.8 GB GPU RAM (fp) | plausible on flagship with int4/quantization, 1–3 s per frame | **EXPERIMENTAL** — not for live guidance |
| **SmolVLM2-2.2B** | Apache-2.0 | 2.2 B; ~5.2 GB GPU RAM for video | not realistic on mid-range | **EXPERIMENTAL / REJECT for phones** |
| **moondream2** | `UNKNOWN` — licence has changed across releases | ~1.9 B | marginal | **REJECT until licence and size are verified** |
| **Qwen2-VL / Qwen2.5-VL (2B/3B)** | Tongyi Qianwen custom licence (usage restrictions; requires review) | 2–3 B | marginal even quantized | **REJECT for v1** |
| **PaliGemma / Gemma-3 vision** | Gemma Terms of Use (permissive-ish but with use restrictions and notice obligations) | 3 B+ | too heavy | **REJECT for v1** |

---

## 7. Small language models (optional, experimental only)

| Model | Licence | Size | Verdict |
| --- | --- | --- | --- |
| **Gemma 3 270M** (Google, 2025) | Gemma Terms of Use — commercial use allowed, with a required NOTICE and prohibited-use policy; not OSI-open | int4 ≈ 125 MB | **OPTIONAL / EXPERIMENTAL** — only if natural-language phrasing is ever needed; and note our guidance is a curated message catalogue, so it is *not* needed |
| **Qwen3-0.6B / SmolLM3-3B** | Apache-2.0 | 0.6 B / 3 B | **OPTIONAL** — same verdict, no current use case |
| Runtime (if ever used) | **LiteRT-LM** (successor to the deprecated MediaPipe LLM Inference API) or llama.cpp | — | **NOTE for implementers:** do not build on the MediaPipe LLM Inference API; it is maintenance-only |

---

## 8. Runtimes and delegates (facts that shape the build)

* **MediaPipe Tasks Android** requires **minSdk 24**; the GPU delegate requires **OpenGL ES 3.1+** and is
  **not available on the Android emulator** → always test on physical devices.
* **NNAPI is deprecated (Android 15).** Do not build on it. Use the LiteRT GPU delegate / `CompiledModel`
  accelerator API, or MediaPipe's own delegate selection.
* Running **two runtimes** in one app (e.g. MediaPipe + ONNX Runtime Mobile) is possible but doubles the
  native surface, the APK size and the ABI matrix — only justified when MediaPipe cannot do the job
  (i.e. Phase 8 multi-person with RTMPose).
* Prefer **float16** TFLite for GPUs and **int8** for CPU-only devices (test both; quantization can hurt
  landmark precision, which directly affects pose scoring).

---

## 9. Decision summary

| Need | Choose | Phase | Alternative if blocked |
| --- | --- | --- | --- |
| Body pose (1 person) | MediaPipe Pose Landmarker (lite → full) | 2 | MoveNet Lightning + own TFLite pipeline |
| Face landmarks + head pose + gaze | MediaPipe Face Landmarker | 2 | ML Kit Face Detection (reduced: no mesh) |
| Hands | MediaPipe Hand Landmarker (on demand) | 5+ | BlazePose hand proxies only |
| Scene tags | SigLIP image encoder + curated prompts | 7 | small supervised classifier on MobileNetV3 |
| Aesthetic | — (none in MVP) | 9+ | NIMA (Apache-2.0) as an explicitly-labelled heuristic |
| VLM | — (none) | experimental | SmolVLM2-500M on flagship only |
| LLM | — (none) | experimental | Gemma 3 270M via LiteRT-LM |
| Multi-person | RTMPose-s/m **or** person detector + per-person BlazePose ROI | 8 | — |

**Total MVP asset budget:** pose (lite 3 MB or full 6 MB) + face (≈ 5 MB) + templates (≈ 1 MB) ≈
**< 15 MB**, leaving plenty of room on `LOW`-tier devices.

---

## 10. What must be verified on device before these become claims

1. Pose landmarker latency/FPS on a `LOW`-tier device (CPU and GPU), in portrait.
2. Face landmarker latency and its effect on the combined pipeline.
3. Thermal behaviour over a 10-minute continuous session.
4. Pose quality for a **full-body subject at 3–4 m** (the model card's edge case) at 640×480 versus 1280×720
   analysis resolution — this is the single most important quality question of Phase 2.
5. Whether the GPU delegate is actually used and actually faster on the target devices.
6. Memory high-water mark with pose + face + preview + capture bound simultaneously.
7. Accuracy of the head-top estimator (`FRAME_HEADROOM_*`) and the arm–torso gap measurement against a
   human-labelled set of local captures.

Every number above is a hypothesis until item-by-item results are recorded in `docs/phase-status.md`.
