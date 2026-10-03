# Research Sources

Consolidated bibliography for Phase 0, with what each source is used for and how much weight it carries.

Source-quality legend:

* **[PRIMARY]** peer-reviewed paper, or official documentation/model card from the vendor
* **[OFFICIAL]** official project/company documentation (not peer-reviewed, but authoritative for its own product)
* **[CRAFT]** credible photography education (practitioner consensus; treated as a convention, not a law)
* **[WEAK]** aggregator/SEO content, or secondary sources whose citations could not be traced — used only as a
  pointer, never as the basis of a numeric threshold

---

## Composition research

| Source | What it supports | Weight |
| --- | --- | --- |
| Amirshahi, Hayn-Leichsenring, Denzler, Redies — *Evaluating the Rule of Thirds in Photographs and Paintings*, Art & Perception 2(1–2), 2014 (brill.com; summary on ResearchGate) | Computed rule-of-thirds measures correlate only weakly (ρ ≈ 0.31–0.47) with subjective ROT ratings and **not at all** with aesthetic ratings; a large set of high-quality photographs and paintings shows ROT values as low as non-ROT images. Basis for our decision **not to score ROT compliance** | **[PRIMARY]** |
| Hoh, Zhang, Dodgson — *Rule-of-Thirds or Centered? A study in preference in photo composition*, SIGGRAPH Asia 2023 Posters (Semantic Scholar PDF) | Participants overwhelmingly preferred a **centered** single object over a ROT-placed one → basis for "centering is not an error" | **[PRIMARY]** |
| *Does the composition of landscape photographs affect visual preferences?* Journal of Environmental Psychology (ScienceDirect S0272494414000085) | ROT/Golden-Section placement of landscape elements **does** affect preferences, and the effect depends on the valence of the element → composition guidance must be context-dependent, not universal | **[PRIMARY]** |
| *Applying the Rule of Thirds in Photography Composition* (ResearchGate, 2025) | Practitioner-side argument for ROT; cited here because it is the kind of claim we deliberately declined to encode (it asserts "eye-tracking confirms", without traceable study details) | **[WEAK]** — recorded as an example of an untraceable claim |

**Conclusion carried into the design:** composition is context-dependent. Only *extreme*, *measurable*
problems are treated as errors; everything else is advisory, and "rule of thirds non-compliance" is an
explicit non-rule (`photography-errors.md` §4).

---

## Headroom, framing and cropping

| Source | What it supports | Weight |
| --- | --- | --- |
| Scott Kelby, *The Natural Light Portrait Book* (O'Reilly, chapter 75) | "Too much space above the head" is one of the most common portrait mistakes; eyes belong in the top third | **[CRAFT]** |
| photographyicon.com — *Headroom and Lead Room* | Definitions of headroom/lead room; "eyes ~ one third from the top"; more headroom in environmental portraits, less in tight head-and-shoulders | **[CRAFT]** |
| Grokipedia — *Headroom (photographic framing)* | Aggregates the common practitioner numbers (headshot headroom ≈ 1/8–1/4 of frame height; eyes on the upper third) | **[WEAK]** — used only to confirm that the 1/8–1/4 figure is a craft convention, hence `CALIBRATION_REQUIRED` |
| imagen-ai.com — *How to do image cropping for headshot photographers* | Eyes-first cropping practice; "too much headroom makes the subject look small"; joint/shoulder crop mistakes | **[CRAFT]** |
| 3dartist.substack.com — *The Art of Tangents*; diyphotography.net — *Four composition mistakes*; willkempartschool.com — *tangents* | Tangents/mergers ("polish the subject's head"), the classic background error; note that the diyphotography comment thread correctly argues some background lines *add* depth → context-dependent | **[CRAFT]** |

**Conclusion carried into the design:** headroom thresholds are craft conventions → marked
`CALIBRATION_REQUIRED`, advisory in MVP, calibrated on local captures in Phase 5.

---

## Camera angle, perspective and focal length

| Source | What it supports | Weight |
| --- | --- | --- |
| PetaPixel — *Lenses Don't Cause Perspective Distortion and "Lens Compression"* (2021) | Perspective distortion is a function of **camera position/distance**, not focal length; demonstrated with matched framing across focal lengths | **[CRAFT]** (but geometrically sound and consistent with first principles) |
| Digital Camera World — *Compression of perspective is a LIE* | Same conclusion: distance causes compression, not the lens | **[CRAFT]** |
| The many angle-effect articles (lensviewing.com et al.) claiming specific studies (e.g. "Schubert & Fielder 2016", "Kosslyn 2013") | Popular claims that low angle = power, high angle = vulnerability | **[WEAK]** — the citations are frequently untraceable or misattributed. The directional *tendency* is plausible and widely repeated, so it is recorded as `P_CONTEXT`, but **no numeric angle rule is derived from it** |

**Conclusion carried into the design:** the app never gives "use 85 mm" advice and never says "low angle is
better". It reasons about **distance, apparent subject size, camera height relative to the subject's eyes**
and (measurably) **camera roll** — and camera angle remains a user choice of intent
(`photography-rules.md` §9).

---

## Posing craft

| Source | What it supports | Weight |
| --- | --- | --- |
| Behind the Shutter — *5 Go-To Poses for Full Body Portraits* | Weight shift, S-curve, elongation, hands framing the face, relaxed fingers, avoid showing the back of the hand | **[CRAFT]** |
| Pathedits — *Portrait Poses: How to Position Your Subject* | 45° body turn, weight on one leg, slight forward lean, "if it bends, bend it slightly", eye-direction variation, seated rules | **[CRAFT]** |
| Kelly McPhail Photography — *How to Pose: 7 Simple Ways* | Ears forward/chin forward, weight on the back leg, arm–torso distance (explicitly: arms pressed to the torso flatten and widen the arm), 45° body angle | **[CRAFT]** |
| SleekLens — *Portrait Poses to Avoid* | Arm-against-body, toy-soldier stance, low angle + subject looking down, group mistakes (wide gaps, equal head heights, stiff arms) | **[CRAFT]** |
| Debbie Photos — *Flattering Portrait Poses for Every Body Type* | 30–45° shoulder turn, arm–body gap, purposeful hands, relaxed fingers | **[CRAFT]** |
| headshotphoto.io / skyryedesign / graphicexpertsindia / mikeglatzerphotos | Additional consensus on: keeping joints slightly bent, posture ("think of a thread pulling the head up"), hands with purpose, avoid pointing limbs at the lens | **[CRAFT]** (mixed quality; used only where multiple sources agree) |

**Conclusion carried into the design:** a small number of these conventions are strong enough to become
*measured* rules (arm–torso gap, stance, weight shift, locked joints) but each is explicitly classified as
`CONTEXT_DEPENDENT` unless it is pure geometry, and none is phrased as "wrong"
(`photography-errors.md` §2.3).

---

## Pose estimation models and accuracy

| Source | What it supports | Weight |
| --- | --- | --- |
| Google AI Edge — *Pose landmark detection guide* (developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker) + Android guide | 33 landmarks, lite/full/heavy bundles, detector 224×224 + landmarker 256×256, config options, world landmarks, segmentation option | **[OFFICIAL]** |
| *Model Card: MediaPipe BlazePose GHUM 3D* (mediapipe-assets PDF) | Model sizes (3/6/26 MB), benchmark FPS on Pixel 3 (lite 44 CPU / 49 GPU; full 18 / 40; heavy 4 / 19), 33×5 output incl. visibility and presence, **z is not metric**, tolerance 10 % shift/scale and 8° roll, **OUT-OF-SCOPE: multiple people, > ~4 m, head not visible, metric depth, surveillance/identity**, PDJ by region (lite 84–89, full 90–93, heavy 93–95), Apache-2.0 | **[PRIMARY/OFFICIAL]** — the single most consequential source of Phase 0 |
| Google AI Edge — *Face landmark detection guide* | 478 landmarks, 52 blendshapes, facial transformation matrix, num_faces smoothing caveat, detector/mesh input sizes | **[OFFICIAL]** |
| Google AI Edge — *Hand landmark detection guide* + *On-Device Real-Time Hand Tracking with MediaPipe* | 21 hand landmarks, palm detector + landmark model, Pixel 6 benchmark 17.12 ms CPU / 12.27 ms GPU | **[OFFICIAL]** |
| Google AI Edge — *Holistic landmarker* | 553 landmarks combined — considered and not chosen (we control cadence per task instead) | **[OFFICIAL]** |
| *Validating Single-Camera Pose Estimation Against Multi-Camera Motion Capture* (PMC) | Overall joint RMSE ≈ 6.1 cm; joint-angle RMSE ≈ 8.5–11° for knee/hip flexion, ~6° trunk → **basis for the 15–20° minimum tolerance floor** in angle-based rules | **[PRIMARY]** |
| RTMPose paper/repo (OpenMMLab) + Qualcomm AI Hub listing | RTMPose-s 72.2 % AP @ 70+ FPS on Snapdragon 865; RTMPose-m 75.8 % AP; Apache-2.0; 18.2 MB w8a16 quantized variant | **[PRIMARY/OFFICIAL]** |
| TensorFlow — *MoveNet* (TF Hub tutorial + TF.js benchmarks) | 17 keypoints, Lightning 192×192 / Thunder 256×256, 30+ FPS claim, Pixel 5 ≈ 34 FPS Lightning (WebGL) | **[OFFICIAL]** |
| MediaPipe issue #5872 | Real-app Face Landmarker latency 30–70 ms on a Pixel 9 Pro despite ~15 ms model benchmark → pipeline overhead is real and must be measured | **[OFFICIAL]** (bug report, high signal) |

---

## Temporal filtering / jitter

| Source | What it supports | Weight |
| --- | --- | --- |
| SmoothNet (ECCV 2022) + N-euro Predictor (ACM IMWUT 2023) | Fixed low-pass filters trade jitter against lag; the One Euro filter adapts its cutoff to speed and is the standard causal choice for interactive landmark smoothing (with known lag) | **[PRIMARY]** |
| freemocap_rt (causal One Euro / Kalman comparison for MediaPipe poses) | One Euro as the practical default for MediaPipe landmarks; Kalman for gap filling | **[OFFICIAL]** (experimental repo, but directly on point) |

---

## Lighting and exposure

| Source | What it supports | Weight |
| --- | --- | --- |
| *Shadow Segmentation With Image Thresholding for Describing the Harshness of Light Sources* (IEEE TIP 2024) and *Quantifying Light Harshness: Method Automation…* (PMC, 2026) | Umbra/penumbra ratio as a quantitative light-hardness measure; explicitly requires **controlled conditions with paired shadowed/unshadowed images** → why we defer hard-light detection in the wild | **[PRIMARY]** |
| *Auto-Exposure Algorithm Based on Luminance Histogram and Region Segmentation* (Trans. Tech. Publications) | Region-segmented histogram analysis (subject vs background) for backlit/over-lit scene classification — the same principle our luma-grid subject/background split uses | **[PRIMARY]** |
| Histogram/clipping tool documentation (scanly.co, pixample, imagen-ai) | Clipping percentages and "well-exposed histogram" heuristics, incl. the "> 5 % highlight clipping is problematic / > 20 % shadow clipping is excessive" figures | **[WEAK]** — recorded as unverified conventions; our thresholds stay `CALIBRATION_REQUIRED` |
| *Light Direction and Color Estimation from Single Image with Deep Regression* (arXiv 2009.08941) and *DeepLight* | Light direction can be regressed from a single image, but with strong assumptions; not needed for our six basic rules | **[PRIMARY]** (context for deferral) |

---

## Android platform, runtime and performance

| Source | What it supports | Weight |
| --- | --- | --- |
| Android Developers — *Neural Networks API* (developer.android.com/ndk/guides/neuralnetworks) | **NNAPI deprecated in Android 15**; migrate to the LiteRT GPU runtime | **[OFFICIAL]** |
| LiteRT issue #6501 (google-ai-edge/LiteRT) + *LiteRT vs TensorFlow Lite* cheat sheet | Play Services runtime exposes GPU/XNNPACK only, NPU support limited; `com.google.ai.edge.litert:litert` 2.x with `CompiledModel`; minSdk 23 | **[OFFICIAL]** |
| Google AI Edge — *Setup guide for Android* and *GPU Support* | MediaPipe Tasks requires Android SDK 24+; GPU delegate requires **OpenGL ES 3.1+**; emulator may not work | **[OFFICIAL]** |
| Android Developers — *CameraX configuration options* | ImageAnalysis default target resolution 640×480; aspect-ratio/resolution negotiation behaviour; guaranteed-configuration constraints with ImageCapture | **[OFFICIAL]** |
| CameraX developer group threads | Real-device evidence that a MAXIMUM-size YUV ImageCapture stream forces ImageAnalysis down to 640×480 on FULL-level devices → the 3-use-case session needs a fallback plan | **[OFFICIAL]** |
| *Digital Video Stabilization and Rolling Shutter Correction using Gyroscopes* (Stanford) | MEMS gyroscope measurements are a cheap, robust way to characterise camera motion — basis for the IMU-based stability/tilt signals | **[PRIMARY]** |

---

## Model licensing

| Source | What it supports | Weight |
| --- | --- | --- |
| MediaPipe BlazePose GHUM 3D model card (`LICENSED UNDER Apache License, Version 2.0`) | Pose/face/hand model redistribution is permitted | **[OFFICIAL]** |
| HuggingFace `apple/MobileCLIP-S1` model card (`license: apple-amlr`, `license_name: apple-ascl`) and `apple/ml-mobileclip` `LICENSE` (MIT for code; weights covered by `LICENSE_MODELS`/`LICENSE_DATA`, which returned 404 on direct fetch) | **Code MIT ≠ weights permissive** — the canonical example used in `model-licenses.md`; status `REJECT until verified` | **[OFFICIAL]** |
| `IceClear/CLIP-IQA` (NTU S-Lab License 1.0) and `chaofengc/IQA-PyTorch` (PolyForm Noncommercial 1.0.0) | Two popular quality/aesthetic toolboxes are **non-commercial** → rejected for shipping | **[OFFICIAL]** |
| Ultralytics licence discussions (#1260, #7440) and third-party summaries | AGPL-3.0 requires open-sourcing the whole app or an enterprise licence (community-reported quotes: ~$5k/year, unverified) → **YOLO rejected** | **[OFFICIAL]** (maintainer statements) |
| `idealo/image-quality-assessment` (Apache-2.0) | NIMA weights are redistributable — the only mainstream aesthetic model we can ship | **[OFFICIAL]** |
| CSAIL `places365` README (CC-BY for pretrained models) + `GKalliatakis/Keras-VGG16-places365` (warns that its ported weights are GPL-3.0) | Scene-classification licensing is a trap; also too large for mobile | **[OFFICIAL]** |
| HuggingFace `HuggingFaceTB/SmolVLM2-*` (Apache-2.0) and Ollama's `Gemma Terms of Use` text | Experimental VLM/LLM options and their redistribution obligations | **[OFFICIAL]** |
| Roboflow model-licence pages (SigLIP Apache-2.0, CLIP MIT, DINOv2 Apache-2.0, YOLOv8 AGPL-3.0) | Cross-checks for the encoder alternatives | **[WEAK/OFFICIAL-adjacent]** — the underlying HuggingFace/GitHub cards are preferred where they exist |

---

## Sources deliberately NOT used as evidence

The following appeared prominently in search results and were **rejected as sources of thresholds**:

* SEO aggregators (e.g. `lensviewing.com`) that invent citations ("a study by Smith & Lee, 2019") — the
  citations could not be traced to any real publication. Their directional claims are recorded as folklore,
  not as numbers.
* `theneuralbase.com` — presented a MediaPipe "resolution vs accuracy benchmark" with explicitly
  hypothetical numbers. Useful only as a hypothesis generator; our Phase 2 experiment exists because of it.
* Recipe/AI-content sites claiming ROT is "scientifically proven because of eye-tracking".

Recording these matters: a future agent searching the same topics will meet the same low-quality results,
and this list prevents them from being promoted into thresholds.
