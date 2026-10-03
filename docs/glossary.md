# Glossary — shared terminology (EN / VI)

Used to keep documentation, code identifiers, message IDs and Vietnamese translations consistent.
Vietnamese terms are **proposed** UI wording; a native-speaker copy review happens in Phase 11.

---

## Photography terms

| Term (EN) | Meaning in this project | Vietnamese (proposed) |
| --- | --- | --- |
| Headroom | Space between the top of the subject's head and the top frame edge (normalized by frame height) | Khoảng trống trên đầu |
| Lead room / look room | Space in front of the subject in the direction of gaze or motion | Khoảng trống phía trước / hướng nhìn |
| Eye line | Vertical position of the subjects' eyes in the frame | Đường mắt |
| Shot type | Headshot / close portrait / half body / three-quarter / full body | Kiểu khung hình (cận mặt / cận người / nửa người / ba phần tư / toàn thân) |
| Framing | Where the subject sits inside the frame | Bố cục khung hình |
| Composition | The arrangement of elements in the frame | Bố cục |
| Rule of thirds | 3×3 grid convention (we do **not** score compliance) | Quy tắc một phần ba |
| Golden ratio / spiral | Alternative proportion convention (not used) | Tỷ lệ vàng |
| Negative space | Empty area that balances the subject | Khoảng trống / không gian âm |
| Fill the frame | Framing so the subject occupies most of the frame | Lấp đầy khung hình |
| Subject–background separation | Keeping the subject visually distinct from what is behind | Tách chủ thể khỏi nền |
| Tangent / merger | Two visual elements touching confusingly (e.g. a pole "growing" from a head) | Đường chạm / sự dính hình |
| Clutter | Visually busy background | Nền rối |
| Click-through / distraction | Anything pulling attention away from the subject | Chi tiết gây nhiễu |
| Backlighting | Light source behind the subject | Ngược sáng |
| Clipping | Pixels pushed to pure black/white, losing detail | Cháy sáng / mất chi tiết vùng tối |
| Contrast (spread) | Difference between the brighter and darker parts of the image | Độ tương phản |
| Hard light / soft light | Small vs large (apparent) light source; measured by shadow edge gradient | Sáng gắt / sáng mềm |
| Camera height | Height of the camera relative to the subject | Chiều cao máy ảnh |
| Camera angle | Direction the camera looks from (below/at/above eye level) | Góc máy |
| Camera roll / Dutch tilt | Rotation of the camera around the lens axis | Nghiêng máy |
| Perspective (distance-driven) | How near/far parts render relative to each other, determined by camera position | Phối cảnh (theo khoảng cách) |
| Foreshortening | A limb pointing at the camera appears shortened | Co ngắn phối cảnh |
| Depth of field | Range of distances in acceptable focus | Vùng nét |
| Weight shift | Bearing body weight on one leg | Dồn trọng tâm |
| Contrapposto | Hip and shoulder lines diverging due to weight shift | Tư thế contrapposto |
| S-curve / C-curve | Body silhouette curve | Đường cong cơ thể |
| Locked joint | A fully straight elbow/knee | Khớp bị "khóa" (duỗi thẳng) |
| Square shoulders | Shoulders parallel to the image plane | Vai vuông góc với máy |
| Pose template | Our normalized-geometry definition of a pose | Mẫu dáng (pose) |
| Pose guide | The dashed overlay showing the target pose | Khung hướng dẫn dáng |
| Match score | Similarity between the detected pose and a template | Điểm khớp dáng |
| Readiness | The state where all required criteria are met | Trạng thái sẵn sàng |
| Auto-capture | Automatic shutter release when ready | Chụp tự động |

---

## Technical terms

| Term (EN) | Meaning in this project | Vietnamese (proposed) |
| --- | --- | --- |
| Landmark | One detected body/face point with x, y, z, visibility, presence | Điểm mốc |
| Visibility | Probability the point is in frame and not occluded | Độ nhìn thấy |
| Presence | Probability the point is inside the frame at all | Độ hiện diện |
| Usability `U(l)` | Our per-landmark trust value: `g = min(visibility, presence)`, a hard floor at 0.5, then `U = (g − 0.5)/0.5`. Normative definition: `pose-system.md` §4.1.1. No sigmoid | Độ dùng được của điểm mốc |
| Subject | One detected person (`trackId`, landmarks, bbox, face, shot type, stability) | Chủ thể |
| Analysis frame | The upright, unmirrored camera frame the engines operate on | Khung phân tích |
| Preview space | What the user sees (upright, aspect-filled, mirrored for the front camera) | Không gian hiển thị |
| Subject space | Hip-centered, torso-normalized pose coordinates | Không gian chủ thể |
| FrameAnalysis | Immutable per-frame snapshot passed to all engines | Ảnh chụp trạng thái khung hình |
| RuleResult | One rule's verdict with measurement, severity, confidence, action | Kết quả luật |
| GuidanceInstruction | A single instruction with actor, direction, magnitude, priority | Chỉ dẫn |
| Actor | Who must act: photographer or subject | Người thực hiện |
| Dwell time | Minimum time an instruction stays on screen | Thời gian giữ chỉ dẫn |
| Hysteresis | Using different enter/exit thresholds to avoid oscillation | Trễ (chống dao động) |
| One Euro filter | Adaptive low-pass filter used for landmark smoothing | Bộ lọc One Euro |
| Abstain | A rule deliberately emits nothing because its inputs are untrustworthy | Không kết luận |
| Calibration required | A threshold with no verified value yet; must not be user-visible | Cần hiệu chỉnh |
| Mirror contract | `mirrorAllowed: true` = score both handednesses and keep the better; `false` = the mirrored pose is never evaluated and a `_m1` twin must be authored instead. No mirror penalty exists | Quy tắc gương |
| `maxAsymmetry` | How handed a template is (largest `|x_left + x_right|`, torso units); > 0.30 forbids `mirrorAllowed: true` | Độ bất đối xứng |
| `normalizationReference` | The body span a template's scale is recovered from: torso length → shoulder width → inter-ocular | Mốc chuẩn hóa tỉ lệ |
| Explanations map | Catalog map from rule id → the "why?" message key (`guidance.why.*`) | Bản đồ giải thích |
| `[REQ] / [TGT] / [GATE] / [DEV]` | Performance number classes: product requirement, unmeasured target, measured acceptance threshold, device-tuned constant (`performance-strategy.md` §0) | Phân loại số liệu hiệu năng |
| Reference device | The developer's own physical device; the mandatory measurement target | Thiết bị tham chiếu |
| Tier (`LOW`/`MEDIUM`/`HIGH`) | Device capability class driving cadence and model variant | Phân hạng thiết bị |
| Degradation ladder | The ordered list of features to disable when the device struggles | Thang giảm tải |
| Message ID | Stable localization key emitted by engines (`guidance.pose.…`) | Mã thông điệp |

---

## Product modes

| Term | Meaning |
| --- | --- |
| `PHOTOGRAPHER_MODE` | Another person holds the phone; both camera and subject instructions are actionable |
| `SELF_MODE` | The phone is on a tripod/stand, or the subject holds it; camera instructions must be re-expressed as subject actions (or suppressed) |
| `HYBRID_UNKNOWN` | Mode not chosen yet; the engine behaves as `PHOTOGRAPHER_MODE` until the user changes it (owner decision: photographer mode is the MVP default) |

---

## Naming rules for code and message IDs

* Rule IDs: `UPPER_SNAKE` with the category prefix (`FRAME_`, `COMP_`, `POSE_`, `LIGHT_`, `READY_`, `INFO_`).
* Instruction IDs: `UPPER_SNAKE` verb-first (`MOVE_CAMERA_LEFT`, `RAISE_LEFT_ARM`).
* Message IDs: dotted lowercase, namespaced by domain and channel, with the **variant as the final
  segment** (`guidance.frame.reduce_headroom`, `guidance.pose.arm_far_from_torso.left`,
  `guidance.why.pose_feet_merging`, `ui.state.detecting`). The registry is
  `specs/i18n/messages.json`; a key that is not registered fails validation.
* Correction codes: `UPPER_SNAKE`, body-part-first (`LEFT_ARM_TOO_CLOSE_TO_TORSO`).
* Vietnamese strings never appear in `core:*` modules; the only text in the engine layer is a `messageId`
  plus numeric parameters.
