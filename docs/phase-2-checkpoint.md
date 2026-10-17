# Phase 2 shutdown checkpoint

Date: 2026-10-17 (Asia/Bangkok)

## Repository state at checkpoint creation

- Repository: `binhvugia1997-ui/app-ch-p-nh`
- Worktree: `I:\AI-Photographer\.tools\phase2-review`
- Branch: `codex/phase-2-pose-face-detection`
- Verified source HEAD before this checkpoint commit:
  `8089d0e32ae2e27cc78b091e9bd0ceda64f3a7cc`
- Pull request: #3, Draft; Phase 2 is not accepted or merged.
- Raw physical evidence remains local and ignored under
  `device-evidence/phase2/physical/`. Do not delete, overwrite or commit it.

The commit containing this file is the saved shutdown checkpoint. After
resuming, use `git rev-parse HEAD` and compare it with the remote branch before
doing any work.

## Completed Phase 2 work

- CameraX preview, analysis and image capture are integrated with the local
  MediaPipe Pose and Face perception pipeline.
- Pose skeleton and Face landmarks are visible together in the main camera
  preview on the Samsung SM-S918B.
- Preview center-crop projection, overlay transforms and front-camera
  mirroring logic have automated coverage.
- Camera switching, capture persistence/error recovery, permission handling,
  background/resume cleanup and ImageProxy lifecycle have implementation and
  host validation coverage.
- The perception scheduler now prevents latency alone from forcing the final
  Pose throttling levels. Busy pressure and thermal pressure can still reduce
  work as designed.
- YUV conversion reuses chroma work across adjacent pixels without changing
  reference output.
- Display rotation changes refresh the CameraX binding and overlay state;
  capture fallback observer ownership and synchronous capture failure cleanup
  are fixed.
- Hidden debug diagnostics no longer poll `/proc` on the main thread.
- The Phase 2 collector now records the resumed foreground activity and stops
  with `INCOMPLETE` if AI Photographer loses foreground or foreground
  telemetry cannot be established.

## Latest automated validation

At source commit `39a730c0d6ebf814c600643357b37790bb46bf04`, the complete
host validation set passed:

- Gradle `test`.
- Debug, profile and release APK builds.
- App debug/profile/release lint and camera debug lint.
- Specification validation with 0 errors and 0 warnings.

At source commit `8089d0e32ae2e27cc78b091e9bd0ceda64f3a7cc`, after adding
the foreground-loss guard:

- 36/36 Phase 2 host-tool unit tests passed.
- Specification validation passed with 0 errors and 0 warnings.
- No production Android code changed, so Gradle builds were not repeated.

## Latest physical evidence

Device: Samsung SM-S918B, Android 16 / API 36, debug build.

The last valid combined Pose/Face evidence before the optimization measured a
59.748-second counter window in rear portrait, requested 480p 4:3:

- Pose: 451/451 detections, 7.548 Hz.
- Face: 112 completions, 52 detections/valid-478 outputs.
- Face reacquisitions: 14; pipeline errors: 0.
- Thermal status remained NORMAL (0); SKIN was 35.3-38.7 C.
- Pose and Face overlays were visible in sampled preview images.

These measurements do not establish overlay accuracy, strict Face continuity,
the documented performance targets or sustained thermal acceptance.

The optimized APK at source commit `39a730c0d6ebf814c600643357b37790bb46bf04`
was installed after successful camera-off checks. Its attempted 60-second run
was interrupted when AI Photographer lost foreground between the 21.890 s and
33.453 s samples. The Samsung app screen and then Facebook were visible; the
AI Photographer PID remained alive. No application crash or ANR was
established, and project automation contained no HOME, tap, swipe or Facebook
launch command. The exact external initiator was not recorded. Only 26.886 s
of Phase 2 counters were available, so its 7.662 Hz partial Pose rate is not a
valid before/after result. There was no automatic retry.

## Known issues and limitations

- The optimized APK still needs one uninterrupted, valid performance
  measurement. No FPS or thermal improvement may be claimed yet.
- The previous foreground transition is established, but its exact human,
  external-control or system initiator cannot be recovered from existing
  evidence.
- Quantitative overlay alignment and crop projection remain to be accepted on
  a real device across rear/front cameras and portrait/landscape orientations.
- Front-camera mirroring, camera switching, image capture/saved-image handling,
  background/resume and permission/failure recovery still require physical
  acceptance.
- Face continuity through controlled head turns and subject loss/reacquisition
  remains open.
- Combined guidance latency, real 480p/720p comparison and the authorized
  sustained thermal gate remain open.
- Phase 2 owner acceptance remains pending. Do not merge PR #3 or begin Phase 3.

## Thermal and physical-test safety requirements

- Start every physical run with a fresh camera-off preflight.
- Require Android thermal status NORMAL (0) throughout the preflight and run.
- Observe thermal state for 30 seconds before camera launch and establish three
  consecutive camera-idle samples at two-second intervals.
- Preserve the existing rapid-rise stop: at least +2 C within 30 seconds.
- Stop on the existing thermal guard or unavailable camera/foreground
  telemetry. Do not weaken thresholds or safety/lifecycle cleanup.
- Require a new explicit owner `Ready` after a passing preflight and before
  every camera launch.
- Keep the device untouched and AI Photographer in foreground during the
  measurement. The collector now stops at sampled foreground loss.
- Run at most the one specifically authorized collection; never retry
  automatically.
- A 300-second thermal soak requires separate explicit authorization.

## Exact next task

Prepare one uninterrupted rear-camera portrait, requested 480p 4:3,
60-second Pose/Face performance measurement of the optimized APK. Do not launch
the camera during preparation. First verify Git/APK/device identity and perform
the fresh camera-off thermal and camera-idle preflight. Only after it passes,
ask the owner for a new `Ready`. Run exactly once, review foreground telemetry
and all raw evidence, and report measured results without claiming improvement
unless the counter window is valid.

## Resume instructions

1. Open `I:\AI-Photographer\.tools\phase2-review`; do not use the repository's
   other worktree.
2. Read `AGENTS.md`, this checkpoint and the latest entries in
   `docs/phase-status.md`.
3. Run `git status --short --branch`, `git log -1 --format=%H` and
   `git ls-remote origin refs/heads/codex/phase-2-pose-face-detection`.
4. Confirm the branch is `codex/phase-2-pose-face-detection`, local and remote
   HEAD match the saved checkpoint commit, and preserve any unexpected local
   changes instead of resetting or cleaning them.
5. Confirm ignored evidence directories still exist. Do not add raw device
   evidence to Git.
6. Continue only the exact next task above. Perform no camera launch until the
   fresh preflight passes and the owner supplies a new `Ready`.
7. Keep PR #3 Draft and unmerged. Record the next work session in
   `docs/phase-status.md`.
