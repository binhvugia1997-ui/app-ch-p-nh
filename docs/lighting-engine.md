# Lighting Engine — what can honestly be inferred from a camera frame

Module: `:core:light` (pure; input = a small luma grid + a subject/face mask).
Feeds `LIGHT_*` rules into `:core:photography` → guidance → readiness.

---

## 1. The honesty clause (read this first)

A phone camera frame is **not** a light meter and **not** a colorimeter:

* The image has already been through the ISP: auto-exposure, auto-white-balance, tone mapping,
  often local tone mapping / HDR, noise reduction and sharpening.
* "Brightness" in the file is a *display-referred* value, not scene luminance; the same scene at a
  different AE target produces different values.
* Some devices apply aggressive face-aware AE or "beauty" processing that hides exactly the problems
  we are trying to measure.
* JPEG/preview pipelines clip highlights sooner than a RAW capture would.

Therefore the engine emits only:

* **relative** statements (subject vs background, left vs right of the face),
* **clipping fractions** (a pixel at the top code value is clipped regardless of exposure policy),
* **flatness/contrast** proxies (a spread measure, not absolute contrast ratio),
* and never "your exposure is 1/3 stop off" or "colour temperature is 5200 K".

Everything in this document with a number is `CALIBRATION_REQUIRED` unless marked otherwise.

---

## 2. Inputs

| Input | Source | Cost | Notes |
| --- | --- | --- | --- |
| luma grid | `ImageProxy` Y plane, downsampled to 64×64 (or 32×32 on LOW tier) | ~0.2 ms | Use Y (already luma); never convert the full frame to RGB for statistics |
| subject mask | convex hull of visible pose landmarks (+ face ellipse when available) | microseconds | Coarse by design; a person-shaped hull is enough for subject-vs-background |
| background ring | frame minus a dilated subject hull | microseconds | Excludes the subject so the background statistic is meaningful |
| face region | ellipse from face landmarks (eye line + chin + cheeks) or a fraction of the head bbox | microseconds | Only when a face is detected |
| device state | `CapabilityReport` (tier), `DeviceState` (thermal), AE/AWB hints if available | — | Used to gate confidence |

Implementation notes:

* Downsampling must **average** (box filter) rather than subsample; subsampling creates fake clipping
  spikes and makes the histogram noisy.
* Work in normalized code values `[0,1]` after dividing by the bit depth (usually 255).
* Do **not** apply an extra gamma or linearization: it does not make the values physical, and it would
  invalidate the clipping statistic.

---

## 3. Measurements

Let `p` be a pixel of the grid, `L(p)` its normalized luma, `S` the subject mask, `B` the background ring,
`F` the face region.

| Metric | Formula | Interpretation |
| --- | --- | --- |
| `medianLuma(region)` | median over region | robust central tendency (median, not mean: specular highlights and dark eyes would skew a mean) |
| `clipHigh` | `|{p : L(p) ≥ 0.98}| / |P|` (`0.98` ≈ 250/255) | blown highlights |
| `clipLow` | `|{p : L(p) ≤ 0.02}| / |P|` | crushed shadows |
| `faceClipHigh` | same, restricted to `F` | blown skin = unrecoverable |
| `contrastSpread` | `p95(L) − p05(L)` over `S` or the whole frame | flat vs punchy |
| `backlightDelta` | `medianLuma(B) − medianLuma(S)` | > 0 means the background is brighter than the subject |
| `faceBgDelta` | `medianLuma(B) − medianLuma(F)` | the practical version of backlight for portraits |
| `faceUnevenness` | `medianLuma(F_left) / max(ε, medianLuma(F_right))` | L/R lighting asymmetry (cosmetic, `NEXT`) |
| `shadowEdgeWidth` | gradient width across a cast-shadow boundary (umbra→penumbra transition), ratio `penumbraWidth / umbraHeight` (literature method) | light hardness — see §5, lab-constrained |
| `brightBlobBehindHead` | largest connected component of `L > 0.85` overlapping the head bbox perimeter | the cheap "distracting bright background" hint (`FRAME_BRIGHT_BLOB_BEHIND_HEAD`) |

Robustness rules:

* All region statistics require a **minimum region size** (e.g. `|F| ≥ 24` grid cells, `|S| ≥ 40`) —
  otherwise abstain.
* Percentiles computed on a sorted 64×64 grid (4096 values) are trivially cheap; keep a reusable buffer.
* Statistics are computed at **2 Hz** (lighting does not change at 30 Hz) with EMA `α = 0.3`, and
  recomputed immediately when the AE/exposure value reported by CameraX changes by more than a small
  delta (AE transitions must not be smoothed away).

---

## 4. Rules and their policies

| Rule | Condition (proposal, all `CALIBRATION_REQUIRED`) | Severity | Action |
| --- | --- | --- | --- |
| `LIGHT_FACE_UNDEREXPOSED` | `medianLuma(F) < 0.22` **or** `faceBgDelta > 0.18` | IMPORTANT | `MOVE_TO_BETTER_LIGHT`, `TURN_ON_LIGHT`, `MOVE_OUT_OF_BACKLIGHT`, `TURN_SUBJECT_TOWARD_LIGHT` |
| `LIGHT_FACE_OVEREXPOSED` | `faceClipHigh > 0.02` | IMPORTANT | `MOVE_TO_BETTER_LIGHT`, `MOVE_OUT_OF_BACKLIGHT`, `TURN_SUBJECT_TOWARD_LIGHT` (registry: `specs/i18n/messages.json`) |
| `LIGHT_HIGHLIGHT_CLIPPING` | `clipHigh > 0.05` (frame-wide) | IMPORTANT when `> 0.05`, NUDGE above `0.02` | `REFRAIN_FROM_BRIGHT_BACKGROUND` (framing hint) |
| `LIGHT_SHADOW_CRUSHING` | `clipLow > 0.20` | NUDGE | informational wording; silhouettes are legitimate |
| `LIGHT_LOW_CONTRAST` | `contrastSpread < 0.25` | NUDGE | `MOVE_TO_BETTER_LIGHT` (softly; flat light is often intentional and flattering) |
| `LIGHT_BACKLIT_SUBJECT` | `faceBgDelta > 0.18` and `clipHigh > 0.01` in `B` | IMPORTANT | `MOVE_OUT_OF_BACKLIGHT` (rendered as "change your camera position" when the subject cannot move relative to the light), `TURN_SUBJECT_TOWARD_LIGHT`, `MOVE_TO_BETTER_LIGHT` |
| `LIGHT_UNEVEN_FACE` | `faceUnevenness > 2.0` | INFO (dev) / NUDGE later | `TURN_SUBJECT_TOWARD_LIGHT` (prose-only rule, `photography-rules.md` §11) |
| `LIGHT_HARD_LIGHT` | `shadowEdgeWidth` below a threshold on a detected cast shadow | NUDGE | `MOVE_TO_SHADE` |
| `FRAME_BRIGHT_BLOB_BEHIND_HEAD` | blob area > `0.5 ×` head bbox area | NUDGE | `REFRAME` / `CHANGE_ANGLE` |

Interaction with other engines:

* A `BLOCKING`-level lighting failure (e.g. `faceClipHigh > 0.15` or `medianLuma(S) < 0.08`) suppresses all
  framing/pose/composition guidance for the duration (guidance-engine §4 priority table).
* Lighting rules **never** propose exposure changes to the camera in the MVP (`architecture.md` ADR-010).

---

## 5. What is deliberately deferred

### 5.1 Light hardness (soft vs hard) — `LATER`

There is real literature: light harshness can be quantified by segmenting a cast shadow into its umbra and
penumbra and computing a ratio (penumbra width / umbra height), with a more recent automated variant.
That work is done under **controlled conditions with paired shadowed/unshadowed images and controlled
geometry** — exactly what a casual outdoor photo does *not* provide. In the wild we would need to:

* find a cast shadow (unknown if one exists, possibly outside the frame),
* know the surface geometry (unknown),
* separate shadow from material colour changes (hard).

Verdict: implement as `EXPERIMENTAL` only, gated on a heuristic confidence ("a large, smooth-gradient
shadow region was found"), and never shown as a hard error. Do not promise "your light is hard" in MVP.

### 5.2 Colour cast — `EXPERIMENTAL`

Grey-world deviation is trivially computable but the ISP's AWB has already removed most of the cast
(and sometimes added one). Without scene reference, a true cast is indistinguishable from coloured
environment (autumn foliage, neon signs). Keep as developer telemetry only.

### 5.3 Skin-tone exposure targets — `REJECT (MVP)`

Trying to place skin in a "correct" zone requires per-device and per-subject calibration and raises
ethnicity/bias issues in a way that benefits no user. We use **relative** comparisons only.

---

## 6. Failure cases and abstention

| Situation | Behaviour |
| --- | --- |
| Night mode / long exposure with digital gain | high noise → `LIGHT_*` confidence capped at 0.4; guidance suppressed |
| AE ramping (rapid change in exposure) | freeze statistics, set `LightReport.unstable = true` (a status flag, not a rule ID), abstain for ~300 ms |
| HDR / HDR+ processing | clipping statistics are unreliable in the shadows; shadow rules abstain |
| Face partially out of frame or occluded | face-region rules abstain; only frame-wide rules run |
| Strong coloured light source in frame | clipping rule may fire; the wording must not claim "your photo is overexposed" — only "bright areas are blown" |
| Subject wearing high-contrast clothing | subject-region statistics are polluted → prefer the face region when available |
| Camera torch on (front flash) | detect via `Camera2` capture-state if available; suppress lighting advice (the user has already chosen) |

---

## 7. Performance

* Y-plane-only analysis at 64×64 → `O(4096)` per pass at 2 Hz: negligible (< 0.1 % of a frame budget).
* No allocations: three reusable `FloatArray(4096)` buffers (frame, subject mask, scratch) and a
  fixed-size histogram.
* Runs on the frame-router thread; must never allocate a `Bitmap`.

---

## 8. Acceptance criteria (Phase 7, but rules land earlier)

1. Unit tests over synthetic luma grids: known clipping fraction → exact rule outcome.
2. A fixture set (locally captured, consented, never committed to the repo) covering: backlit portrait,
   flat shade, harsh midday, night interior, blown window behind subject. The engine's verdicts are
   recorded as expected outputs, and calibration values are fitted from this set.
3. The engine abstains (never guesses) when the face region is too small or AE is unstable.
4. No user-facing statement ever claims absolute exposure or colour accuracy (string audit in review).
