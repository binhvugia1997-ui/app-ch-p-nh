# Phase 1 local build and physical-device verification

Phase 1 is authorized; acceptance requires the owner and physical-device evidence. Phase 2 is not started.
No physical phone was connected during the initial implementation session. Physical verification subsequently
started on SM-S918B; completed checks, measured windows and pending steps are recorded in `phase-status.md`.

## Toolchain and local validation

Installed: Android Studio JBR 25.0.3, SDK platform 37.0 revision 2 (`PreviewSdkInt=0`), build tools 36.0.0,
Gradle 9.6.0 and AGP 9.4.1. Gradle supports JDK 25; the compilation bytecode target is Java 17.
AGP's built-in Android Kotlin is 2.2.10; JVM Kotlin and the Compose compiler plugin match it.
CameraX 1.6.2 is the stable release. Compose BOM 2026.09.00, Activity 1.13.0, Lifecycle 2.10.0 and
coroutines 1.10.2 are pinned. Dependencies were resolved and compiled, rather than taken from an old sample.
See [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera), and
[Compose BOM](https://developer.android.com/develop/ui/compose/bom).

The root lint configuration excludes only version-update advisories (`AndroidGradlePluginVersion`,
`GradleDependency`, `NewerVersionAvailable`). Versions are deliberately pinned to the verified toolchain;
Kotlin/Compose compiler must stay aligned with AGP. Other lint warnings are errors. No portrait-lock
suppression or orientation flag remains. API 29 profiling and API 31 backup
attributes are annotated with their platform levels; older Android ignores them. Language bundle splitting
is disabled so Vietnamese resources remain available offline. English fallback resources are included.
Large-screen Android can override orientation requests; the camera/geometry remains landscape-capable.

From PowerShell at the repository root:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
# local.properties is untracked. Escape the drive colon: sdk.dir=C\:/Users/anelb/AppData/Local/Android/Sdk
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleProfile test :app:lintDebug :feature:camera:lintDebug :app:assembleDebugAndroidTest
python3 specs/validation/validate_specs.py
```

If Python is unavailable, install Python + jsonschema or use a workspace-local uv runner:
`uv run --no-project --python 3.13 --with jsonschema python specs/validation/validate_specs.py`.
The implementation session used this equivalent because the system python3 command was only a Store alias.

Outputs (generated, not committed):

- `app/build/outputs/apk/debug/app-debug.apk`: signed debug, HUD + marker + resolution controls.
- `app/build/outputs/apk/release/app-release-unsigned.apk`: optimized, unsigned production artifact.
- `app/build/outputs/apk/profile/app-profile.apk`: release settings/R8/resources, debug-key signed for
  local profiling; no developer UI. Not a distribution signing configuration.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: instrumented tests.

## Connect and record the reference phone

Enable Developer options and USB debugging, connect a data-capable USB cable, and accept the RSA prompt
on the phone. Use one physical phone; emulator results do not satisfy camera or performance gates.

```powershell
$adbPath = 'C:/Users/anelb/AppData/Local/Android/Sdk/platform-tools/adb.exe'
& $adbPath devices -l
& $adbPath shell getprop ro.product.model
& $adbPath shell getprop ro.build.version.release
& $adbPath shell getprop ro.build.fingerprint
& $adbPath install -r app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:connectedDebugAndroidTest
```

Record the exact model, Android version/build, build type, and whether it is the MEDIUM reference device.
The instrumented tests exercise the actual crosshair renderer in eight synthetic camera/orientation/aspect
combinations and verify ImageProxy closure on success, cadence skip and failure. They cannot prove that a
camera's physical crop/mirror matches its advertised metadata; that is the manual test below.

## Functional camera and coordinate checks

1. Deny camera access, deny again, open settings, grant, revoke, return to app: clear explanation,
   no crash, no permission loop. Check background/foreground, screen off/on, and activity recreation.
2. On back and front cameras, confirm preview + analysis + manual JPEG capture. Record the capability
   query result, actual bind outcome and fallback attempts from `Phase1Capability` logs/HUD.
   A reduced-resolution full session still records the original query result and the successful attempt.
3. Enable system Auto rotate and rotate normally in any build. No app orientation flag is required.
   The obsolete `allowLandscape` extra/preference is ignored. Start normally:

   ```powershell
   & $adbPath shell am force-stop com.aiphotographer.app
   & $adbPath shell am start -n com.aiphotographer.app/.MainActivity
   ```

   All variants follow system orientation. Default camera controls are designed for portrait first;
   landscape uses the real Android configuration and a landscape-shaped viewport.
   Rotate the phone normally. Use the 4:3 / 16:9 debug button. For each camera × orientation × aspect,
   put a non-person target at upright unmirrored ANALYSIS coordinate (0.25, 0.5); the cyan marker must
   align with it. On front preview it is at mirrored spatial x. Save screenshots locally, not photographs
   of people in Git. Include a known edge target to test crop, not only the centre.
4. Capture in portrait and landscape while analysis is active. Inspect JPEG EXIF orientation and displayed
   image, expected exposure, and storage destination. API 29+: MediaStore `Pictures/AI Photographer`;
   API 24–28 or MediaStore failure: app-private `files/Pictures/AI Photographer`. Private images are not
   gallery-visible. No storage permission or upload occurs. Inspect debug private files with `adb shell
   run-as com.aiphotographer.app ls 'files/Pictures/AI Photographer'`.
5. If a phone lacks three-use-case support, verify the recorded lower-resolution attempt, analysis-only
   serial capture with analysis restored after save/error, then capture-only if needed. This fallback
   sequence is implemented but remains hardware-unverified when the connected device supports everything.
6. Run 10 minutes, switch cameras/resolutions, background/resume, and check logcat for crash/ANR.
   Test airplane mode. Auto-capture has no implementation in this phase; all shutter actions are manual.

## Baseline measurement (physical phone only)

Install the optimized profile build (same application ID/debug signing key as debug), grant camera once,
then measure cold launch without including a permission dialog. Do not compare debug HUD overhead with
optimized release/profile numbers. The RGB micro-benchmark flag defaults OFF and is only instrumentation.

```powershell
New-Item -ItemType Directory -Force device-evidence | Out-Null
& $adbPath install -r app/build/outputs/apk/profile/app-profile.apk
& $adbPath shell am start -n com.aiphotographer.app/.MainActivity
# Grant permission on the phone before measuring.
& $adbPath logcat -c
& $adbPath shell am force-stop com.aiphotographer.app
& $adbPath shell am start -W -n com.aiphotographer.app/.MainActivity --ez benchmarkRgb true
& $adbPath push tools/phase1-perfetto.pbtxt /data/local/tmp/phase1-perfetto.pbtxt
& $adbPath shell perfetto --txt -c /data/local/tmp/phase1-perfetto.pbtxt -o /data/misc/perfetto-traces/phase1-480p.pftrace
& $adbPath pull /data/misc/perfetto-traces/phase1-480p.pftrace device-evidence/phase1-480p.pftrace
& $adbPath logcat -d -v monotonic Phase1Baseline:I Phase1Capability:I Phase1ColdStart:I Phase1Capture:I '*:S' > device-evidence/480p-log.txt
& $adbPath shell dumpsys meminfo com.aiphotographer.app > device-evidence/480p-memory.txt
& $adbPath shell dumpsys thermalservice > device-evidence/480p-thermal.txt
```

On the tested Samsung API 36 build, Perfetto could not read `/data/local/tmp/phase1-perfetto.pbtxt`.
Use `/data/misc/perfetto-configs/phase1-perfetto.pbtxt` for push/config instead. The 64 MiB ring buffer
overwrote part of the 720p window; an ignored local evidence config with `size_kb: 262144` retained
the full window. Check trace bounds and `traced_buf_chunks_overwritten` before accepting a 60 s result.
Keep the phone unlocked and ensure any third-party lock-screen activity is disabled for the controlled
run; SM-S918B traces showed a Hanzii lock-screen activity interrupting the 480p recording. Record initial
thermal status before starting; the first session was already thermally constrained, so a cooled repeat
is required to establish controlled conditions. Do not change unrelated settings automatically.

Repeat with `--ez analysis720p true --ez benchmarkRgb true`, a fresh force-stop/start and a distinct
720p trace/log name. Record the *actual* buffer and crop dimensions; requested resolution is a preference,
not a guaranteed hardware configuration. A crop may differ from the nominal 480p/720p buffer.
On older Android without Perfetto/FrameTimeline, record that limitation and use Android Studio profiling
and camera/render traces available on the reference device; do not fabricate FrameTimeline results.

Open each trace in a local Perfetto UI/Android Studio. Select a common 60 s steady-state window. Report
sample count and p50/p95 for `phase1_luma`, `phase1_yuv_rgb_rotation` and `phase1_router`. Router duration
includes selected stage work; do not sum it again with those stages. RGB conversion is the reusable CPU
ARGB + rotation implementation, sampled at 1 Hz; luma uses the 64×64 grid at 2 Hz. Later perception sources
are disabled scheduler slots. Logs contain rolling nearest-rank histograms (at most 4096 samples) and
cumulative rates since session start/reset; Perfetto determines the measurement window.

HUD preview FPS counts camera capture callbacks, **not displayed frames**. Validate displayed preview
through SurfaceFlinger FrameTimeline/render traces and record both values/methods separately. FrameRouter
skip ratio is intentional luma cadence skips / delivered analysis frames; CameraX's internal drops are
UNKNOWN because KEEP_ONLY_LATEST does not expose its discarded frame count. A 2 Hz luma stage on a
30 Hz delivery stream intentionally skips most frames. Do not classify that as overloaded inference.

`Phase1ColdStart` logs launch → PreviewView STREAMING, an availability proxy. Pair it with `am start -W`
and the first actual displayed preview frame in the trace for the brief's cold-start baseline. Permission
dialog time and camera-switch time are not cold start. RSS is `/proc/self/status` VmRSS in debug; get
process RSS/high-water mark from Perfetto/process stats and dumpsys for optimized profiling. PSS is not RSS.

For SM-S918B cold traces, the first camera-buffer presentation was identified locally: find the first
RenderThread `acquireBuffer` child named `SurfaceTexture-*`, follow parents to `DrawFrames <token>`,
join that surface frame token to the app's actual FrameTimeline frame, and subtract the
`launching: com.aiphotographer.app` start from presentation `ts+dur`. The app has one preview TextureView;
exclude splash layers and dropped frames. Record the matched token and timestamps so this can be audited.
The measured results and local `cold.sql` evidence path are in `phase-status.md`.

Thermal is unavailable before API 29; report UNKNOWN rather than NONE. Record battery and thermal progression
across the 10-minute session. GL eligibility is not a runtime delegate probe; no model exists to benchmark.

Put measured tables, raw-stage sample counts, screenshots/trace locations, fallback outcome, and any
missed targets in `docs/phase-status.md`. Keep all untested tiers/targets `NOT_MEASURED`. Only then request
GitHub Agent review and owner Phase 1 acceptance; do not start Phase 2.

## Final corrected-state physical review checkpoint — 2026-10-06

Physical verification is finished on Samsung SM-S918B, Android 16, build
S918BXXSAFZH3. Phase 1 is ready for evidence review, not owner-accepted. Phase 2 has
not started. Historical measurements above/in the status log remain historical;
the final corrected-build results are the authoritative review evidence.

All eight camera/orientation/aspect direct-edge combinations were physically observed
**qualitatively** using a stationary readable non-person target, bracketed screenshots
and saved JPEGs. Observed crop, edge correspondence, orientation and front preview
mirroring were visually inspected. **Numerical reprojection accuracy remains unmeasured**;
no numerical error, pixel tolerance pass or independent analysis-space feature coordinate
is claimed. Review must assess this manual evidence against the crosshair acceptance gate.

| Camera | Orientation | Aspect | Saved JPEG MediaStore ID |
| --- | --- | --- | --- |
| Rear | Portrait | 4:3 | 14769 |
| Rear | Portrait | 16:9 | 14764 |
| Front | Portrait | 4:3 | 14774 |
| Front | Portrait | 16:9 | 14783 |
| Rear | Landscape | 4:3 | 14802 |
| Rear | Landscape | 16:9 | 14810 |
| Front | Landscape | 4:3 | 14796 |
| Front | Landscape | 16:9 | 14790 |

Optimized baselines use profile APK SHA256
F5ED7C351BFF7A49724D037C938553EC348EB262D982D58F3C7CD73CAAAEE9A5,
rear portrait 4:3, RGB conversion/rotation benchmark enabled. Full retained windows:
480p **60.003053 s**, 720p **60.002265 s**, no reported trace errors/overwritten chunks.
First presented camera texture: **1074.894896 ms / 883.574063 ms**. Both runs remained
thermal NONE (0), with airplane mode ON. Actual buffer/crop dimensions, exact per-stage
sample counts/p50/p95, presented frame rates, callback rates, RSS, skip accounting and
auditable cold timestamps live in the canonical table in
[phase-status.md — Final corrected-build optimized baselines](phase-status.md#final-corrected-build-optimized-baselines-2026-10-06).
Do not round the slightly sub-30 Hz presented rates into a numeric 30 FPS target pass.
CameraX internal dropped-frame counts are UNKNOWN; compositor presentation does not
prove a unique camera image on every frame. Router durations already include stage work.

Final debug soak uses APK SHA256
90C33FC6861EF20BE5274CBD47047D4AC293BDCBE90B173D044E3A5C303BDAA9:
**600.0004430999979 s**, completed=true, all four camera/resolution/background-resume
actions passed. Thermal start/all samples/end NONE (0); airplane mode remained ON.
No collected camera/analyzer/AndroidRuntime errors, empty crash buffer, no ANR since boot.
Battery 89 to 89 percent, 34.8 to 36.7 C, USB powered: not a battery-drain measurement.
Exact RSS/PSS snapshots and limitations are recorded in
[phase-status.md — Final corrected-build ten-minute soak](phase-status.md#final-corrected-build-ten-minute-soak-2026-10-06).
Unsupported-hardware fallbacks, other phones/tiers, runtime model delegates, numerical
coordinate error and long-term memory/battery behaviour remain unverified/unmeasured.

Raw captures, screenshots, XML, traces, event JSON and local verification helpers remain
ignored and preserved in `device-evidence/samsung-sm-s918b/`; they are excluded from the
public checkpoint. Keep this directory on this machine. The Git checkpoint contains
review documentation and production/tests already implemented, not private raw evidence.
Final local validation results are recorded in the status document after the checks finish.
GitHub Agent review and explicit human owner acceptance remain outstanding; no acceptance
is inferred from successful physical testing or validation. No push or merge is authorized.
