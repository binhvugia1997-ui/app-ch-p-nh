# AI Smart Framing — thiết kế tính năng (documentation only)

> **Trạng thái: THIẾT KẾ — ĐÃ CHỐT 8 QUYẾT ĐỊNH (D1–D8), ĐÃ XỬ LÝ C1–C5 VÀ R2 (29/10/2026) / CHƯA ĐƯỢC CẤP PHÉP TRIỂN KHAI TÍNH NĂNG.**
> Tài liệu này là bản thiết kế, không phải đặc tả chuẩn (normative), không phải uỷ quyền phase.
> Tám quyết định D1–D8 ở §0.4 là **đã duyệt**; C1–C5 ở §11 và R2 đã **đóng** (xem cột trạng thái và §12.1),
> trong đó **chỉ C1 và C5 là thay đổi mã/spec trên đĩa** — C1 là mã Phase 2, C5 là khoá chuỗi trong spec;
> D8 là thay đổi tài liệu (hành vi readiness). R5 **không** dẫn tới commit nào: xem §14.
> Mọi câu hỏi còn lại ở §10.2 và các mục **còn mở** của §10.3 vẫn là **chưa duyệt**.
> Việc duyệt thiết kế không mở Phase 3, không mở Phase 5, không thay đổi trạng thái PR #3.
> Khi có xung đột, các nguồn sau **thắng**: `docs/roadmap.md` (ranh giới phase),
> `docs/composition-engine.md` + `docs/guidance-engine.md` (hành vi engine),
> `specs/rules/mvp-rules.json` (rule id, severity, phase, threshold status),
> `specs/i18n/messages.json` (instruction id + text vi/en), `specs/schemas/*` (hợp đồng dữ liệu).
> Ngôn ngữ: tiếng Việt theo yêu cầu của chủ dự án; mọi định danh kỹ thuật (rule id, action id,
> tên file, tên kiểu dữ liệu) giữ nguyên tiếng Anh. Có thể dịch song song sang tiếng Anh sau khi
> chủ dự án duyệt (xem Q1 ở §10.2).

Ngày: 2026-10-29 · Người viết: agent kế nhiệm (sau audit bàn giao) · Nhánh tài liệu: `codex/epose-inspired-plan-docs`
Bản sửa: rev 2 cùng ngày — chốt D1–D7, chia §10 thành đã đóng/còn mở, thêm §11 (mâu thuẫn có bằng chứng)
và §12 (nhiệm vụ nhỏ nhất tiếp theo). Không có dòng mã nào bị sửa.

---

## 0. Ràng buộc nền tảng và rà soát roadmap

### 0.1 Tính năng này thuộc phase nào

| Câu hỏi | Trả lời (theo tài liệu hiện có) |
| --- | --- |
| Lõi cảm nhận (Pose/Face) | **Phase 2** — đang `IMPLEMENTATION_COMPLETE / AUTOMATED_VALIDATION_COMPLETE / PENDING_PHYSICAL_VERIFICATION`, PR #3 **Draft**, chưa được chấp nhận |
| Vệt dáng nét đứt (dashed guide) | **Phase 3** — chưa bắt đầu |
| Chấm điểm khớp dáng + hiệu chỉnh tư thế | **Phase 4** — chưa bắt đầu |
| **Khung bố cục + hướng dẫn di chuyển máy + readiness** | **Phase 5** — đây chính là nơi AI Smart Framing thuộc về (`docs/roadmap.md` §Phase 5) |
| Thư viện dáng + gợi ý dáng | Phase 6 |
| Hiểu cảnh (scene tags, saliency) | **Phase 7+**, `LATER`, không thuộc MVP |
| Đa người | Phase 8 |

**Kết luận bắt buộc:** không được triển khai code Smart Framing trước khi Phase 2 được chủ dự án
chấp nhận (AGENTS.md điều 1–2). Tài liệu này **không** mở Phase 3, **không** thay đổi trạng thái PR #3.

### 0.2 Các bất biến không được vi phạm

1. **Local-first / offline**: không `INTERNET`, không API trả phí, không tải model lúc chạy.
2. **Không thêm model mới** cho MVP Smart Framing — chỉ hình học trên landmark sẵn có (§7).
3. **Guard nhiệt là bất khả xâm phạm**: preflight NORMAL-only, rapid-rise ≥ 2 °C/30 s, status ≥ 3.
   Tài liệu này không đề xuất thay đổi ngưỡng nào; phần host-runner nằm ngoài phạm vi tính năng.
4. **Engine thuần, không Android, không chuỗi hiển thị**: chỉ phát `RuleResult` / instruction id.
5. **Một người** (BlazePose out-of-scope multi-person). Smart Framing MVP là **chân dung một người**.
6. **Không điểm thẩm mỹ, không điểm rule-of-thirds, không "cân bằng thị giác"** — đã bị REJECT bằng
   bằng chứng (`docs/photography-rules.md` §9, `docs/composition-engine.md` §8).
7. **Không chọn "phong cách bố cục" bằng AI thẩm mỹ.** Việc "tự chọn chiến lược" trong tài liệu này
   chỉ là **chọn bộ dải mục tiêu đo được** theo loại khung hình (§4), không phải chấm điểm cái đẹp.

### 0.3 Mâu thuẫn phase `COMP_*` — ĐÃ PHÂN XỬ (D1)

`docs/roadmap.md` §Phase 5 ghi deliverable "framing set + **advisory composition set**",
nhưng `specs/rules/mvp-rules.json` gán `COMP_EYE_LINE_PLACEMENT` và `COMP_LEAD_ROOM` là
`NEXT` / **`phase: 6`**. Theo `docs/photography-rules.md` §12, **JSON là chuẩn cho id/phase**.

**Quyết định của chủ dự án (D1, 29/10/2026):** `COMP_*` **giữ nguyên phase đã đặc tả (phase 6)**,
không chuyển vào Phase 5. Hệ quả có hiệu lực:

1. MVP Smart Framing **chỉ** triển khai 10 rule `FRAME_*` (phase 5). `COMP_*` nằm ngoài phạm vi.
2. Không có `COMP_*` nào trong hộp mục tiêu, trong arbiter hay trong readiness của MVP.
3. Mọi câu "gợi ý mắt trên đường 1/3" **không** thuộc tính năng này cho tới Phase 6.
4. Từ nay **không cần** sửa JSON hay roadmap cho mâu thuẫn này — JSON đã đúng; chỉ còn một điểm
   cần lưu ý khi đọc `composition-engine.md` (§3.1 bước 9 và ví dụ §6 có nhắc `COMP_*`) — xem §11 C4.

### 0.4 Tám quyết định đã được chủ dự án phê duyệt (29/10/2026)

| ID | Nội dung quyết định | Hệ quả bắt buộc trong thiết kế |
| --- | --- | --- |
| **D1** | `COMP_*` giữ nguyên phase đặc tả (phase 6), **không** chuyển vào Phase 5 | MVP chỉ `FRAME_*`; §0.3, §3.1, §4.4, §11 C4 |
| **D2** | Smart Framing **tự động đề xuất** khung phù hợp **theo mặc định** khi tính năng được bật | Không bắt người dùng chọn chiến lược trước; chiến lược do engine tự chọn (§4.1); ý định người dùng chỉ là ghi đè tuỳ chọn (§4.4) |
| **D3** | Người dùng **phải tắt được** hướng dẫn; **không** coi sở thích bố cục chủ quan là lỗi khách quan | Công tắc chủ bật/tắt là hạng mục MVP bắt buộc (§3.1 #12, §3.3); phân loại đo-được/chủ-quan phải cứng trong engine và UI (§4.6) |
| **D4** | Bản **release được phép** hiển thị overlay Smart Framing **tối giản** khi được bật | Định nghĩa "tối giản" bằng danh sách đóng (§6.1); không cần chờ L6 mới được hiển thị, nhưng L6 vẫn là cổng để **tuyên bố** cải thiện |
| **D5** | Overlay release **không được** lộ landmark Pose/Face debug | Danh sách cấm tuyệt đối trong §6.1; kiểm tra hiện trạng Phase 2 phát hiện vi phạm tiềm ẩn (§11 C1) |
| **D6** | Tái dùng Pose/Face và kiến trúc guidance hiện có | Không model mới, không máy trạng thái thứ hai, không module mới (§1, §2, §5) |
| **D7** | Mọi ngân sách hiệu năng vẫn là **mục tiêu đề xuất** cho tới khi đo độc lập | Mọi số ở §7 mang nhãn `[TGT]`; không câu nào trong tài liệu được đọc như số đo (§7) |
| **D8** (đóng **R2**) | Khi Smart Framing **tắt**, tiêu chí bố cục **không được** góp vào readiness | Bảng tiêu chí mới ở `guidance-engine.md` §7.1.1; §3.3, §5.3, §10.3 R2 của tài liệu này |
| **R5** (đã rà soát, **không** commit) | Ba file tài liệu còn treo của Codex ở main worktree | **Không** commit: chúng là ảnh chụp cũ của lịch sử Phase 2 đã có đầy đủ trên nhánh Phase 2, một file trùng byte-đệt. Chi tiết và bằng chứng ở §14 |

Nguyên tắc suy ra (không phải quyết định mới, chỉ là hệ quả logic của D2+D3+D4+D5):

* **Bật** là mặc định của tính năng, nhưng **tắt** phải là một hành động rõ ràng của người dùng và có
  hiệu lực ngay, không cần khởi động lại, không mất ảnh đang chụp.
* Đã tắt thì **không còn** banner, hộp mục tiêu, nhãn band — tức là UI camera trở về đúng trạng thái
  trước tính năng. "Tắt" không có nghĩa là "vẫn vẽ nhưng nhạt hơn".
* Overlay release là **tối giản theo danh sách đóng** (§6.1): thêm bất kỳ thành phần nào ngoài danh sách
  đó vào bản release là thay đổi phạm vi và cần chủ dự án duyệt lại.

---

## 1. Thành phần tái sử dụng (đã có trong repo)

Không xây lại bất cứ thứ gì dưới đây.

| Năng lực | Vị trí thực tế | Dùng thế nào trong Smart Framing |
| --- | --- | --- |
| Pose 33 điểm + Face 478 điểm, GPU→CPU fallback, watchdog, recovery | `perception/mediapipe/MediaPipePipeline.kt`, `TaskSources.kt` | Nguồn landmark duy nhất. Không thêm suy luận nào. |
| Làm mượt landmark (One Euro) + lắp `Subject` (bbox, `visibleFraction`, `shotType`, `face`) | `perception/api/SubjectAssembler.kt`, `core/geometry/OneEuroFilter.kt` | Đầu vào trực tiếp của engine bố cục. `Subject.shotType` đã có sẵn. |
| Ước lượng loại khung hình | `core/photography/ShotTypeEstimator.kt` | Tái dùng; **có lỗ hổng**: hiện trả `UNKNOWN` cho headshot/close-portrait (xem §4.2). |
| Hợp đồng dữ liệu khung hình | `core/model/Frame.kt` (`FrameGeometry`, `DeviceState`, `AnalysisQuality`, `ImageSnapshot`, `LumaGrid`) | `FrameAnalysis` là input chuẩn của `CompositionEngine`. |
| Lưới luma 64×64 @ ~2 Hz | `FrameRouter.analyze` → `LumaGrid` | Đủ cho `FRAME_BRIGHT_BLOB_BEHIND_HEAD` (biến thể rẻ). Không cần thêm tần suất. |
| Góc nghiêng camera từ IMU | `feature/camera/DeviceMonitor.kt` (`roll` từ `TYPE_GRAVITY`/`ROTATION_VECTOR`) | `FRAME_CAMERA_TILT` + hiển thị đường chân trời. |
| Chiếu toạ độ ANALYSIS → PREVIEW (mirror + center-crop fill) | `core/geometry/Coordinates.kt` (`analysisToPreview`) | **Một hàm duy nhất** cho mọi hình vẽ overlay. |
| Overlay sản phẩm + test chiếu xạ | `app/src/main/.../PerceptionOverlay.kt` + `PerceptionOverlayProjectionTest.kt` *(chỉ có trên nhánh Phase 2, chưa vào `main`)* | Mở rộng thành overlay Smart Framing; tái dùng test khung. |
| Overlay chẩn đoán (debug) | `app/src/debug/.../DeveloperPanel.kt` | Giữ nguyên; thêm số đo bố cục vào panel, không thêm vào đường sản phẩm. |
| Rule + hợp đồng `RuleResult` / `CompositionSummary` | `docs/composition-engine.md` §2, `specs/schemas/` | Engine mới **phải** tuân thủ đúng kiểu dữ liệu này. |
| **10** quy tắc `FRAME_*` MVP (phase 5) | `specs/rules/mvp-rules.json` | Toàn bộ logic "lỗi bố cục đo được" đã được đặc tả — chỉ cần cài đặt. |
| Bộ điều phối một-chỉ-dẫn, dwell/cooldown, bảng hợp nhất xung đột | `docs/guidance-engine.md` §4–§5 | Dùng nguyên trạng; **không** viết arbiter thứ hai. |
| Máy trạng thái readiness | `docs/guidance-engine.md` §7 | Tái dùng; Smart Framing chỉ cấp criteria. |
| Thang suy giảm + cadence | `perception/api/PerceptionSchedule.kt` (0..6) | Bố cục chạy theo nhịp pose sẵn có, không có timer riêng. |
| Instruction id + text vi/en | `specs/i18n/messages.json` (51 action, 87 message, 30 explanation) | Không thêm chuỗi mới trong engine; nếu cần wording mới thì thêm vào catalog. |
| Validator chéo | `specs/validation/validate_specs.py` | Chạy sau mọi thay đổi `specs/` hoặc tham chiếu rule/message. |

**Không tái sử dụng (và lý do):** `DeveloperOverlay` bản release/profile là **no-op** — overlay sản phẩm
phải nằm ở `src/main` (như `PerceptionOverlay.kt`), không được "bật" bằng cách sửa bản debug.

---

## 2. Kiến trúc đề xuất

### 2.1 Sơ đồ luồng

```
CameraX ANALYSIS (không mirror, đã xoay đúng 1 lần)
        │
        ▼
MediaPipePipeline (Pose/Face)  ──► SubjectAssembler ──► Subject
        │                                              (bbox, visibleFraction, shotType, face)
        ▼
PerceptionSnapshot ──► FrameAnalysis (hợp đồng có sẵn)
        │
        ▼
┌──────────────────────────── :core:photography (thuần Kotlin) ────────────────────────────┐
│ CompositionEngine                                                                        │
│   1. ShotTypeEstimator (mở rộng dải bbox/face, CALIBRATION_REQUIRED)                      │
│   2. FramingStrategySelector  ──► FramingStrategy (bộ dải mục tiêu)                       │
│   3. FramingRules FRAME_*     ──► List<RuleResult>                                        │
│   4. FramingTargetTracker     ──► FramingTarget (hộp mục tiêu ổn định, đã làm mượt)       │
│   5. CompositionSummary (band hiển thị, không phải điểm)                                  │
└──────────────────────────────────────────────────────────────────────────────────────────┘
        │  RuleResult + FramingTarget + CompositionSummary
        ▼
GuidanceEngine (đã đặc tả)  ──► GuidanceState { primary, secondary, readiness, composition }
        │
        ▼
SmartFramingOverlay (:app, src/main)  ──► hình vẽ + banner 1 chỉ dẫn + sheet "vì sao?"
```

### 2.2 Ranh giới module

- Toàn bộ toán học mới nằm trong **`:core:photography`** (thuần, có test JVM). Không thêm Gradle module
  mới — AGENTS.md điều 14: module mới cần lý do ranh giới; ở đây không có lý do đó.
- `:app` chỉ *gọi* engine và *vẽ*. UI không tự tính toán bố cục (đúng hợp đồng `guidance-engine` §8).
- `:perception:*` và `:feature:camera` **không bị sửa** cho tính năng này. Nếu buộc phải sửa
  (ví dụ cần `roll` mới hơn), phải ghi ADR trong `docs/architecture.md` trước.
- Phụ thuộc mới duy nhất: `:app` → `:core:photography` (hiện `:app` chưa phụ thuộc trực tiếp;
  `:core:photography` đã được `:perception:api` dùng). Đây là thay đổi đồ thị module nhẹ, **cần một
  dòng ADR** khi triển khai.

### 2.3 Vị trí tính toán theo thời gian (quyết định thiết kế)

`FrameRouter` hiện chỉ công bố `ImageSnapshot` cho UI ở **2 Hz**, trong khi `perception.state` phát ra
ở nhịp pose (~7–15 Hz). Hướng dẫn bố cục cần nhịp pose để phản hồi kịp, nhưng overlay chỉ cần vẽ theo
khung hình hiển thị.

**Đề xuất (A — khuyến nghị):** `:app` thu một `StateFlow` từ `perception.state` (đúng nhịp pose), dựng
`FrameAnalysis` từ `PerceptionSnapshot`, chạy engine bố cục, rồi:
- cập nhật `FramingTarget` + `GuidanceState` ở nhịp pose (đã bị `PerceptionSchedule` giới hạn sẵn);
- đẩy state cho Compose vẽ ở nhịp khung hình hiển thị (Canvas đọc state mới nhất).

**Phương án B (không khuyến nghị):** tính trong `FrameRouter` → `:feature:camera` phải phụ thuộc
`:core:photography`, trộn trách nhiệm camera với phân tích. Chỉ dùng nếu A không đạt G-P4.

Cả hai đều **không** thêm luồng suy luận, **không** thêm cadence mới.

### 2.4 Kiểu dữ liệu mới (thuần, không chuỗi)

```kotlin
// :core:photography — chỉ là đề xuất, chưa được cài đặt
data class FramingStrategy(
    val id: StrategyId,                  // FULL_BODY_CENTERED, HALF_BODY_CENTERED, HEADSHOT_HEADROOM_FIRST, ...
    val shotType: ShotType,
    val sizeBand: ClosedFloatingPointRange<Float>,      // phần chiều cao khung hình
    val headroomBand: ClosedFloatingPointRange<Float>?,
    val centrePolicy: CentrePolicy,      // CENTERED_TOLERANT (mặc định) | USER_OFF_CENTER
    val marginPolicy: MarginPolicy,      // LIMBS_SAFE (bỏ qua khi shot type cắt chặt)
)

data class FramingTarget(
    val subjectBox: NormalizedRect,      // hộp mục tiêu đã làm mượt
    val anchor: TargetAnchor,            // HEAD_TOP / EYE_LINE / FEET
    val validForGeometry: FrameGeometry, // target bị vô hiệu khi đổi geometry/camera
    val updatedAtMs: Long,
)

data class SmartFramingState(
    val strategyId: StrategyId?,
    val target: FramingTarget?,
    val results: List<RuleResult>,
    val summary: CompositionSummary,
    val neutralReason: NeutralReason?,   // NO_SUBJECT / LOW_CONFIDENCE / DEGRADED / STALE
)
```

Tất cả là **dữ liệu thuần**; không có `String` hiển thị, không có `Context`.

---

## 3. Chức năng MVP (phạm vi chốt)

### 3.1 TRONG phạm vi

| # | Chức năng | Nguồn luật |
| --- | --- | --- |
| 1 | Cổng "có chủ thể" + trạng thái "không thấy người" | `FRAME_SUBJECT_PRESENT` |
| 2 | Tự chọn chiến lược khung theo loại khung hình phát hiện được | §4 (bảng dải mục tiêu) |
| 3 | Mục tiêu tỉ lệ chủ thể (quá nhỏ / quá lớn) | `FRAME_SUBJECT_TOO_SMALL` / `_TOO_LARGE` |
| 4 | Mục tiêu khoảng trống trên đầu | `FRAME_HEADROOM_EXCESSIVE` / `_INSUFFICIENT` |
| 5 | Lề mép khung + cắt qua khớp | `FRAME_EDGE_MARGIN`, `FRAME_JOINT_CROP` |
| 6 | Lệch tâm **cực đoan** (không phải rule-of-thirds) | `FRAME_SUBJECT_OFF_CENTER_EXTREME` |
| 7 | Cân bằng máy bằng IMU | `FRAME_CAMERA_TILT` |
| 8 | Vùng sáng chói sau đầu (biến thể luma rẻ) | `FRAME_BRIGHT_BLOB_BEHIND_HEAD` — *tuỳ chọn, xem Q9* |
| 9 | Overlay: hộp mục tiêu + hộp chủ thể + banner 1 chỉ dẫn + "vì sao?" — **không** landmark | `guidance-engine` §8, §6.1 |
| 10 | Đúng chiếu xạ front/rear × portrait/landscape × 4:3/16:9 | `Coordinates.analysisToPreview` |
| 11 | Readiness: tiêu chí bố cục ở mức *bắt buộc* / *khuyến nghị* | `guidance-engine` §7.1 |
| 12 | **Công tắc chủ bật/tắt hướng dẫn bố cục** (bắt buộc theo **D3**), có hiệu lực tức thời | §3.3, §5.3 |
| 13 | **Tự động chọn chiến lược theo mặc định** khi tính năng bật (theo **D2**) — không có bước "chọn kiểu chụp trước" | §4.1 |

Bổ sung bắt buộc từ D3: khi công tắc **tắt**, engine không phát chỉ dẫn bố cục và overlay không vẽ gì
(xem §3.3). Sở thích chủ quan (ví dụ "chủ thể nên lệch trái cho nghệ thuật") **không** được nâng thành
lỗi khách quan trong bất kỳ trạng thái nào.

### 3.2 NGOÀI phạm vi (và sẽ không lén thêm)

- Điểm rule-of-thirds, điểm thẩm mỹ, "cân bằng thị giác", golden ratio — **REJECT** vĩnh viễn.
- Saliency, leading lines, tiền cảnh, đối xứng, frame-in-frame — cần phân đoạn cảnh (Phase 7+).
- Scene tags / SigLIP / VLM / LLM — `LATER`/`EXPERIMENTAL`, không có trong MVP.
- Đa người, nhận dạng danh tính, tuổi/giới tính/cảm xúc — bị cấm.
- Thư viện dáng, gợi ý dáng (Phase 6), vệt dáng nét đứt (Phase 3) — không kéo về trước.
- Tự động zoom, tự động chụp mặc định — auto-capture chỉ sau Phase 5/9 và **OFF mặc định**.

### 3.3 Hành vi khi người dùng tắt hướng dẫn (D3)

| Khía cạnh | Khi bật (mặc định) | Khi tắt |
| --- | --- | --- |
| Chọn chiến lược | Tự động theo D2 | Không tính |
| Chỉ dẫn (`GuidanceState.primary`) | Có, tối đa 1 chính + 1 phụ khác actor | **Không có** chỉ dẫn bố cục nào |
| Hộp mục tiêu / hộp chủ thể / nhãn band | Vẽ (§6.1) | **Không vẽ** |
| Chi phí tính toán engine | ~0,2 ms/khung pose `[TGT]` | **0** — bỏ hẳn lời gọi engine, không tính rồi vứt |
| Readiness | Có tiêu chí bố cục theo §5 | **Loại hẳn** tiêu chí bố cục khỏi readiness (**D8/R2**, đã duyệt) — xem `guidance-engine.md` §7.1.1 |
| Chụp thủ công | Luôn khả dụng | Luôn khả dụng |
| Trạng thái nhiệt | Không đổi guard | Không đổi guard |

Yêu cầu kỹ thuật: công tắc là **một nguồn sự thật duy nhất** trong lớp preference; engine thuần **không**
biết tới công tắc (nó chỉ không được gọi). Vị trí đặt công tắc đã chốt ở **§3.4** (R1 đóng): ngay trên
màn hình camera + `SharedPreferences`, **không** mở `:feature:settings` cho một toggle.

**Readiness khi tắt (D8/R2, đã duyệt).** Tiêu chí bố cục bị **loại khỏi** phép tính readiness, không
phải "vẫn tính nhưng không hiện". Ranh giới chính xác nằm ở một chỗ duy nhất — bảng tiêu chí
`guidance-engine.md` **§7.1.1** — và điều đáng lưu ý nhất trong đó là: **hiện diện** của chủ thể vẫn
là tiêu chí bắt buộc (không có người thì không có gì để chụp), còn **lề mép** bị bỏ (đó là quyết định
bố cục). Tài liệu này không chép lại bảng đó để tránh hai nguồn sự thật.

Ba hệ quả bắt buộc khi tắt, lặp lại ở đây vì chúng dễ bị bỏ sót khi triển khai:

* `ReadinessState` phải được **tính lại từ đầu** sau khi đổi công tắc; không được thừa hưởng trạng thái
  `READY` đã sinh ra dưới thiết lập kia.
* `readinessReasons` **không** được chứa lý do bố cục (kể cả lý do cũ còn lưu): sheet "vì sao?" mà giải
  thích một tiêu chí đã bị gỡ là lỗi hiển thị.
* Không tiêu chí bố cục nào được **khoá nút chụp** ở bất kỳ chế độ nào.

### 3.4 Công tắc bật/tắt — thiết kế chốt (C2, không cần module mới)

**Đặt ở đâu.** Ngay trên màn hình camera, cùng hàng với các nút đã có (`switch_camera`, `capture`),
dạng `TextButton` nhãn ngắn (`Bố cục: Bật` / `Bố cục: Tắt`). Không có màn hình settings, không có luồng
onboarding. Lý do: `settings.gradle.kts` hiện chỉ include 7 module và **không có** `:feature:settings`;
`architecture.md` ghi module này vẫn deferred, và AGENTS.md điều 14 cấm tạo module chỉ vì "sẽ cần sau".
Một toggle không đủ biện minh cho một module.

**Lưu ở đâu.** `SharedPreferences` cục bộ của `:app`, một khoá boolean duy nhất
(`smart_framing_enabled_v1`, mặc định `true` theo **D2**). Không dùng DataStore, không thêm dependency;
không có dữ liệu nào rời thiết bị (bất biến local-first ở §0.2).

**Trạng thái khi khởi động.** Đọc preference một lần khi tạo `CameraSession`; giá trị này *không* phụ
thuộc camera facing hay aspect. Đổi camera không đổi công tắc; đổi công tắc **có** reset lớp bố cục
(§5.3).

**Vòng đời của một lần bật/tắt.**

| Sự kiện | Hành vi bắt buộc |
| --- | --- |
| Bật | Dựng `CompositionEngineState` mới (latch/hysteresis/EMA trống), chờ khung phân tích kế tiếp; **không** phát chỉ dẫn từ trạng thái cũ |
| Tắt | Ngừng gọi engine, bỏ target đang giữ, không vẽ gì ở khung kế tiếp (không để "hộp ma" một khung) |
| Tắt rồi bật trong < 1 s | Vẫn là lần bật mới: không khôi phục latch, không khôi phục target |
| Đổi camera / aspect / resolution / lifecycle STOP→START | Giữ nguyên công tắc, reset lớp bố cục (đã có ở §5.2 mục 5) |

**Chi phí khi tắt.** Không cấp phát, không đọc cảm biến cho bố cục, không thêm timer. Pipeline Pose/Face
vẫn chạy theo lịch riêng của Phase 2 (nó phục vụ cả pose guidance và chẩn đoán) — tắt Smart Framing
**không** được phép tắt perception, vì như vậy là thay đổi hành vi Phase 2 đã được đo.

**Điều công tắc này KHÔNG được làm.** Không đổi ngưỡng, không đổi guard nhiệt, không đổi cadence,
không tắt MediaPipe, không chặn nút chụp. Nó chỉ gate *lớp bố cục* (engine + overlay bố cục).

**Phạm vi triển khai.** Việc này thuộc Phase 5 (khi engine bố cục tồn tại). Trong tác vụ này **không**
có mã công tắc nào được viết; đây là thiết kế để Phase 5 làm đúng ngay lần đầu, không phải là một
module settings mới.

---

## 4. Logic chọn chiến lược bố cục

### 4.1 Nguyên tắc

"Chọn chiến lược" = **chọn một bộ dải mục tiêu đo được** phù hợp với loại khung hình đang có,
không phải chọn "phong cách đẹp". Đầu vào: `shotType` (phát hiện + ý định người dùng), `aspectRatio`,
`facing`, `roll`, chất lượng nguồn. Đầu ra: `FramingStrategy` (bảng dưới). Hoàn toàn tất định,
có test bảng.

Theo **D2**, không có bước "chọn kiểu chụp trước": khi tính năng bật, engine **tự** chọn chiến lược từ
`shotType` phát hiện được. Ý định người dùng (§4.4) chỉ là ghi đè tuỳ chọn, và **D3** cho phép tắt hẳn
thay vì phải chọn giữa các chế độ. Vì vậy đường mặc định phải đúng khi người dùng **không** động vào gì.

### 4.2 Lỗ hổng hiện có phải xử lý trước

`ShotTypeEstimator` hiện chỉ dựa trên landmark khớp (hip/knee/ankle) và **trả `UNKNOWN` cho headshot và
close portrait** (ghi chú trong mã: chưa có biên hiệu chỉnh). Do đó bước S1 **phải** bổ sung nhánh
`h_face`/`h_body` theo `composition-engine.md` §4 với các hằng `T_*` đều `CALIBRATION_REQUIRED`.

### 4.3 Bảng chọn (đề xuất, mọi ngưỡng là `CALIBRATION_REQUIRED`)

| ShotType | Dải tỉ lệ | Dải headroom | Neo | Ghi chú |
| --- | --- | --- | --- | --- |
| `FULL_BODY` | 0.75–0.98 (bbox) | 0.03–0.20 | head-top + feet | phải thấy cả bàn chân |
| `THREE_QUARTER` | 0.55–0.85 (bbox) | 0.05–0.25 | head-top | |
| `HALF_BODY` | 0.35–0.60 (bbox) | 0.05–0.25 | head-top | |
| `CLOSE_PORTRAIT` | 0.18–0.35 (**face**) | 0.04–0.18 | eye-line | cần Face |
| `HEADSHOT` | 0.28–0.45 (**face**) | 0.04–0.14 | eye-line | cần Face |
| `UNKNOWN` | — | — | — | **abstain**, hiện trạng thái trung tính |

Nguồn số: `docs/photography-rules.md` §5 (`FRAME_SUBJECT_TOO_SMALL/LARGE`, `FRAME_HEADROOM_*`),
`docs/composition-engine.md` §4. JSON là chuẩn cho id/severity/phase; các con số ở trên **không** được
chép vào code dưới dạng hằng rời — phải sinh từ `Thresholds` như §5 của composition-engine yêu cầu.

### 4.4 Ý định người dùng ghi đè (giới hạn)

- Người dùng **có thể** chọn ý định: `FULL_BODY` / `HALF_BODY` / `HEADSHOT` (đây là *shot intent*, không
  phải "phong cách").
- Lệch **1 bậc** giữa phát hiện và ý định → dùng ý định (người thường khung rộng hơn họ nói).
- Lệch **2 bậc** → báo "ý định không khớp khung hình", không tự đổi.
- `centrePolicy`: mặc định `CENTERED_TOLERANT` (chỉ báo lệch > 0.55). **Căn giữa là hợp lệ** —
  bằng chứng: Hoh/Zhang/Dodgson 2023 (người xem ưu tiên chủ thể căn giữa) và Amirshahi et al. 2014
  (ROT không tương quan với đánh giá thẩm mỹ).
- `USER_OFF_CENTER` chỉ được mở khi `COMP_*` vào phase — theo **D1** điều đó nghĩa là **Phase 6**,
  nên trong MVP giá trị này **không tồn tại**. Không có nhánh code "bật tạm" cho nó.
- Theo **D2**, người dùng **không** phải chọn ý định trước khi dùng: không có intent ⇒ engine vẫn tự
  chọn chiến lược từ `shotType` phát hiện được. Ý định chỉ là ghi đè tuỳ chọn, và theo **D3** người
  dùng luôn có thể tắt hẳn hướng dẫn thay vì phải chọn giữa các chế độ.

### 4.5 Sinh mục tiêu ổn định (position + scale)

Mục tiêu không phải "khung hình lý tưởng của AI", mà là **hộp dung sai** neo vào chủ thể:

1. Tính `h_body`, `h_face`, `headroomRatio`, `eyeLine`, `minEdgeMargin`, `centreOffset` từ landmark
   đã làm mượt (One Euro) — dùng lại `LandmarkFilter`.
2. Chọn dải theo §4.3; `centreOffset` chỉ so với ngưỡng cực đoan 0.55.
3. **Hộp mục tiêu** = hình chữ nhật suy ra từ dải: chiều cao mục tiêu = trung điểm dải; neo theo
   `anchor` (head-top / eye-line / feet) và canh giữa theo `centrePolicy`.
4. **Làm mượt mục tiêu**: EMA `α = 0.2` cho headroom và tỉ lệ (theo `guidance-engine` §6.1);
   hộp mục tiêu chỉ **đổi dải** khi vượt ngưỡng enter/exit (10 % bề rộng dải) và giữ tối thiểu
   `minDwellMs = 900` để không nhảy.
5. **Vô hiệu hoá**: đổi `FrameGeometry` (xoay máy, đổi camera, đổi aspect/resolution), mất chủ thể
   > 500 ms, hoặc `trackId` đổi → reset filter + target (đúng §6.1 "không làm mượt qua scene cut").
6. Không dùng "khung vàng" hay bất kỳ mục tiêu thẩm mỹ nào; hộp mục tiêu là **dung sai**, không phải
   điểm số.

### 4.6 Đo được vs chủ quan (bắt buộc phân biệt trong UI)

| Loại | Ví dụ | Cách xử lý trong Smart Framing |
| --- | --- | --- |
| **ĐO ĐƯỢC, khách quan** | chủ thể quá nhỏ/lớn, headroom ngoài dải, lề mép, cắt khớp, lệch tâm cực đoan, nghiêng máy | Được phát chỉ dẫn (severity theo JSON), có số đo kèm theo |
| **QUY ƯỚC NGHỀ (advisory)** | mắt gần đường 1/3 trên | Chỉ khi `COMP_*` vào phase; không bao giờ chặn |
| **CHỦ QUAN / CHƯA ĐO ĐƯỢC** | "bố cục đẹp", "cân bằng", "đường dẫn", "đối xứng" | Không phát; không có trong UI. **D3:** không bao giờ được trình bày như lỗi |
| **BỊ CẤM** | điểm thẩm mỹ, rule-of-thirds score, nhận xét cơ thể | Không tồn tại trong sản phẩm |

UI **phải** phân biệt bằng hình thức: chỉ dẫn đo được dùng banner + số đo; gợi ý quy ước (nếu có sau này)
dùng nhãn "gợi ý" và không bao giờ chặn.

---

## 5. Máy trạng thái hướng dẫn

Không viết máy trạng thái mới. Đây là **ánh xạ** trạng thái bố cục vào máy đã đặc tả
(`guidance-engine.md` §5–§7) và bổ sung một lớp "khung nhìn bố cục" cho UI:

```
        ┌──────────────┐   có subject, chất lượng tốt    ┌──────────────┐
        │  NO_SUBJECT  │ ───────────────────────────────► │   GUIDING    │
        │ (chờ người)  │ ◄─────────────────────────────── │ 1 chỉ dẫn    │
        └──────────────┘   mất subject > 500 ms           └──────┬───────┘
               ▲                                                  │ mọi tiêu chí
               │                                                  │ bắt buộc đạt
        ┌──────┴───────┐   chất lượng kém / stale / giảm cấp      ▼
        │   NEUTRAL    │ ◄─────────────────────────────── ┌──────────────┐
        │ "đang nhận   │                                  │   IN_BAND    │
        │  diện…"      │                                  │ (khen 600 ms)│
        └──────────────┘                                  └──────┬───────┘
                                                                 │ giữ ổn định
                                                                 ▼
                                                          ┌──────────────┐
                                                          │ READINESS    │
                                                          │ NOT_READY /  │
                                                          │ NEAR_READY / │
                                                          │ READY        │
                                                          └──────────────┘
```

### 5.1 Luật chuyển trạng thái (dùng nguyên tham số đã có)

| Cơ chế | Giá trị | Nguồn |
| --- | --- | --- |
| `minDwellMs` | 900 ms (`CALIBRATION_REQUIRED`) | guidance-engine §5.3 |
| `cooldownMs` sau khi giải quyết | 2 500 ms | nt |
| `repeatCooldownMs` | 4 000 ms | nt |
| flip guard | 1,5 s, trừ khi `BLOCKING` | nt |
| cửa sổ khen | 600 ms | nt |
| `MIN_SCORE` / confidence floor | 0,25 / 0,5 | guidance-engine §5.2 |
| ưu tiên nguồn | READINESS 100 · SYSTEM 90 · LIGHTING 80 · **COMPOSITION framing 70** · POSE 60 · COMPOSITION advisory 40 | guidance-engine §4 |
| bỏ phiếu N-of-M | 3/5 khung | guidance-engine §6.2 |

### 5.2 Quy tắc riêng của lớp bố cục

1. **Một chỉ dẫn chính**; chỉ dẫn phụ chỉ khi **khác actor** và điểm ≥ 0,8 × điểm chính.
2. Hợp nhất xung đột theo bảng §5.4 của guidance-engine, ví dụ
   `MOVE_CLOSER` + `INCREASE_HEADROOM` → `COMPOSITE` (`guidance.frame.move_closer_lower_camera`).
3. **Abstain thay vì đoán**: `visibleFraction < V_min`, thiếu landmark đầu, `shotType = UNKNOWN`,
   đo quá cũ so với ngân sách của rule → không phát chỉ dẫn, chuyển `NEUTRAL`.
   **Không chép số ở đây.** Bốn con số dễ lẫn (`0.5` scoreable, `V_min = 0.55` với exit `0.60`,
   `confidence ≥ 0.5` của arbiter, `200 ms` ngân sách rule so với `500 ms` `MAX_AGE_MS` của nguồn) có
   đúng một nguồn sự thật là bảng hợp đồng ở `docs/composition-engine.md` §3.2 (C3). Lớp bố cục đọc
   `V_min` và `exitValue` từ `specs/rules/mvp-rules.json`, không khai báo lại.
4. **Không bao giờ chặn chụp thủ công** vì lý do bố cục. Headroom là tiêu chí *khuyến nghị*
   (§7.1 guidance-engine); "chủ thể quá nhỏ" là *bắt buộc* cho readiness nhưng chỉ **cảnh báo**
   với chụp tay.
5. **Reset** khi: đổi camera/facing, đổi geometry/aspect/resolution, lifecycle STOP→START,
   `degradationLevel ≥ 5`, hoặc mất subject.
6. Hysteresis bắt buộc trên mọi ngưỡng (`enter`, `exit = enter × (1 ± margin)`).

### 5.3 Trạng thái "đã tắt hướng dẫn" (D3)

`DISABLED` **không phải** trạng thái của máy trạng thái hướng dẫn — máy trạng thái chỉ tồn tại khi tính
năng bật. Khi người dùng tắt:

* Không gọi engine bố cục, không cấp phát target, không có `GuidanceState` bố cục để vẽ.
* Overlay bố cục không được vẽ dù chỉ một khung hình (tránh "tắt rồi vẫn thấy hộp ma").
* Đổi trạng thái công tắc giữa phiên phải **reset** mọi latch/hysteresis/EMA của lớp bố cục, để bật lại
  không thừa hưởng trạng thái cũ.
* Chụp ảnh, chuyển camera, khoá phơi sáng và mọi hành vi khác không đổi.

Hệ quả lên readiness khi tắt đã được chủ dự án chốt (**D8**, đóng **R2**): tiêu chí bố cục bị **loại
khỏi** phép tính readiness, không phải chỉ ẩn đi. Bảng tiêu chí chi tiết ở `guidance-engine.md` §7.1.1;
ba hệ quả bắt buộc (tính lại từ đầu, không lý do bố cục trong `readinessReasons`, không khoá nút chụp)
ở §3.3. Vì vậy `DISABLED` vẫn không phải một trạng thái của máy trạng thái — nó là điều kiện *bên ngoài*
máy trạng thái, quyết định máy trạng thái có được cấp tiêu chí bố cục hay không.

---

## 6. Thiết kế overlay UI

### 6.1 Overlay bản release — danh sách đóng (D4, D5)

Theo **D4**, bản release **được phép** vẽ overlay tối giản khi tính năng bật. "Tối giản" được định nghĩa
bằng **danh sách đóng** dưới đây: thêm bất kỳ thành phần nào ngoài danh sách là thay đổi phạm vi sản phẩm.

| Thành phần | Release | Ghi chú |
| --- | --- | --- |
| **Hộp mục tiêu** — 4 góc nhấn nét liền, mảnh | **CÓ** | **Khác hẳn** vệt dáng nét đứt của Phase 3; không dùng nét đứt cho bố cục |
| **Hộp chủ thể** — viền mảnh quanh `Subject.bbox` | **CÓ** | Đây là hình chữ nhật, **không** phải landmark; ẩn khi stale |
| **Banner 1 chỉ dẫn** (đúng `GuidanceState.primary`, phụ nếu có) | **CÓ** | UI **không tự soạn** lời khuyên |
| **"Vì sao?"** — sheet chữ + số đo dạng text | **CÓ** | Tra `explanations` theo `ruleIds[0]`; chuỗi lấy từ `messages.json` |
| **Trạng thái trung tính** — "Đang nhận diện…" | **CÓ** | Khi chất lượng kém / stale / abstain |
| **Nhãn band** — `GOOD / ACCEPTABLE / NEEDS_WORK` | **CÓ** | Chuỗi lấy từ `label.band.good` / `label.band.acceptable` / `label.band.needs_work` trong `specs/i18n/messages.json` (C5, đã thêm vi+en). Đây là khoá `label.*`: nhãn hiển thị, **không bao giờ** là `GuidanceInstruction`. Không được hard-code chuỗi trong Compose |
| **Thanh dải dọc** (vị trí trong dải) | **KHÔNG** | Chỉ bản dev; `composition-engine` §2.2 chốt "band + một chỉ dẫn là toàn bộ UI bố cục MVP" |
| **Xương Pose (bones), 33 chấm Pose, 478 chấm Face** | **KHÔNG BAO GIỜ** | **D5** cấm tuyệt đối. Không có cờ nào bật được ở release/profile |
| **ID landmark, giá trị ngưỡng thô, `strategyId`, confidence dạng số** | **KHÔNG** | Chỉ bản dev |
| **Điểm / score /10** | **KHÔNG TỒN TẠI** | Bị REJECT vĩnh viễn (`composition-engine` §2.2, §8) |

Quy tắc hiển thị: chỉ vẽ khi công tắc bật **và** nguồn không stale quá 500 ms; khi abstain, chỉ còn trạng
thái trung tính, không có hộp mục tiêu cũ sót lại.

### 6.2 Chẩn đoán chỉ tồn tại ở bản dev (D5)

* Xương/chấm landmark, số đo thô, band bar, `strategyId`, giá trị ngưỡng: chỉ trong source set `debug`.
  Cơ chế gate của dự án là **source-set shadowing** (cùng tên, cùng chữ ký composable khai báo riêng
  trong `src/debug`, `src/profile`, `src/release`), **không** phải `if (BuildConfig.DEBUG)` lúc chạy —
  đây là khuôn mẫu đã có của `DeveloperOverlay`/`DeveloperControls`, và nó an toàn hơn vì `profile`
  không thể vô tình bật chẩn đoán.
* **C1 đã sửa (29/10/2026, IMPLEMENTED):** landmark không còn trong `src/main`. `PerceptionOverlay.kt`
  được tách thành `PerceptionOverlayProjection.kt` (toán chiếu thuần, ở lại `main` vì phép chiếu dùng
  chung cho mọi overlay và có unit test) và `DiagnosticLandmarkOverlay.kt` (vẽ, chỉ ở `debug`; `profile`
  và `release` khai báo thân hàm rỗng). Call-site trong `MainActivity` đổi sang tên `DiagnosticLandmarkOverlay`.
  Bằng chứng: class `debug` 14 159 byte có `Canvas`/`PoseLandmarks`; class `profile` và `release`
  3 005 byte, không tham chiếu `Canvas`/`PoseLandmarks` (kiểm bằng `javap`); 4 test trong
  `DiagnosticOverlayVariantTest` xanh.
* Bản `profile` dùng cho đo hiệu năng: nếu cần landmark để chẩn đoán thì dùng `debug`; nếu buộc phải đo
  trên `profile` có overlay thì phải đo **cả hai trạng thái** (bật/tắt) và ghi rõ — xem **R4**.

### 6.3 Chiếu xạ, mirror và xoay (yêu cầu 6)

- Mọi hình học giữ ở **toạ độ ANALYSIS** (đã upright, **không** mirror). Chỉ mirror ở lúc vẽ.
- Một hàm duy nhất: `Coordinates.analysisToPreview(p, frame.width, frame.height, viewW, viewH,
  frame.isMirrored)` — đúng center-crop fill, đã có test `PerceptionOverlayProjectionTest`.
- `rotationDegrees ∈ {0, 90, 180, 270}` đã được áp **đúng một lần** ở `FrameRouter`; overlay không
  xoay lại.
- **Ngữ nghĩa hướng** (dễ sai nhất): với camera trước, "di chuyển máy sang phải" của người dùng
  ngược với trục x của ảnh đã mirror. Đề xuất một bảng ánh xạ duy nhất và test:
  `MOVE_CAMERA_LEFT/RIGHT`, `MOVE_CAMERA_UP/DOWN`, `ROTATE_CAMERA_CW/CCW` ⇒ vector hiển thị,
  phụ thuộc `facing` và `rotationDegrees`. Không suy diễn tại chỗ trong Composable.
- Đổi `FrameGeometry` ⇒ xoá target + overlay cũ, không vẽ trạng thái cũ lên khung mới.

### 6.4 Vị trí mã nguồn và hiệu năng vẽ

- Overlay sản phẩm ở `app/src/main/.../` (như `PerceptionOverlay.kt` trên nhánh Phase 2).
  `DeveloperOverlay` (debug/profile/release no-op) **không** phải chỗ đặt tính năng.
- Vẽ trong một `Canvas` duy nhất, đọc state bằng `collectAsStateWithLifecycle`; không tạo đối tượng
  nặng trong `draw`.
- Không poll `/proc`, không đọc file trong `LaunchedEffect` trên main thread (lỗi cũ đã sửa).
- Banner dùng text từ resource (`values-vi/`, `values/`), khoá `guidance.*` ổn định.

---

## 7. Cân nhắc hiệu năng và nhiệt

| Hạng mục | Thiết kế | Bằng chứng/nguồn |
| --- | --- | --- |
| Suy luận mới | **Không có.** Không model mới, không frame mới | §0.2 |
| Chi phí tính toán | bố cục ≈ **0,2 ms**/khung pose `[TGT]`; hợp nhất chỉ dẫn ≈ **0,5 ms**/cập nhật `[TGT]` | `performance-strategy.md` §5 — **mục tiêu**, chưa đo |
| Ngân sách tổng | **G-P4: ≤ 35 ms/khung phân tích trên MEDIUM** `[TGT]` | `performance-strategy.md` |
| Độ trễ hướng dẫn | p95 ≤ **250 ms** (G-P2b) `[TGT]` | nt |
| Cadence | theo `PerceptionSchedule` sẵn có (pose 8–15 Hz, face 100–500 ms) — **không thêm timer** | `PerceptionSchedule.kt` |
| Lưới luma | tái dùng 2 Hz có sẵn (chỉ cho bright-blob) | `FrameRouter` |
| Suy giảm | khi `degradationLevel ≥ 5`, hoặc `faceOnly`, hoặc nguồn stale → giữ target cuối + `NEUTRAL` | `PerceptionSchedule`, `guidance-engine` §8 |
| Cấp phát | không cấp phát mảng mới mỗi khung; tái dùng buffer như các audit trước yêu cầu | `phase-2-diagnostic-overhead-audit.md` |
| Nhiệt | **không đổi guard**; không thêm cảm biến/polling; không thêm GPU | checkpoint Phase 2 §"Thermal and physical-test safety" |
| Tải UI | overlay vẽ theo khung hình hiển thị, không theo nhịp suy luận; mục tiêu G-P3 60 FPS | roadmap G-P3 |

**D7 — trạng thái của mọi con số:** tất cả ngân sách ở trên, kể cả 0,2 ms và 0,5 ms, là **mục tiêu đề
xuất**, không phải số đo. Không câu nào trong tài liệu này được trích dẫn như bằng chứng hiệu năng.

**Không được tuyên bố** cải thiện FPS, nhiệt hay pin từ thiết kế này. Quy tắc D7 áp dụng cho cả tài liệu,
PR và báo cáo: một con số chỉ được nâng từ `[TGT]` lên `[GATE]` khi có lần đo độc lập trên thiết bị, ghi
kèm model, phiên bản Android, build type, nhiệt độ và thời lượng — đúng `performance-strategy.md` §0.

**Kế hoạch đo tối thiểu khi tới Phase 5 (chưa thực hiện):** (a) chi phí engine trong JVM (< 2 ms/10 000
khung, cổng §8.1); (b) G-P4 trên MEDIUM với công tắc tắt và bật, để tách chi phí overlay khỏi chi phí
phân tích; (c) nhiệt trong cùng điều kiện preflight, không đổi guard.

---

## 8. Chiến lược kiểm thử

### 8.1 Tầng thuần JVM (`:core:photography`)

1. **Bảng chiến lược**: mọi tổ hợp `shotType × aspect × facing × intent` → `StrategyId` mong đợi;
   `UNKNOWN` ⇒ abstain.
2. **Dải & hysteresis**: dao động đo quanh ngưỡng ⇒ tối đa **1** chuyển trạng thái mỗi `minDwellMs`
   (đúng `composition-engine` §9.4).
3. **Abstention**: landmark dưới ngưỡng usability ⇒ `evaluated = false`, có `abstainReason`.
4. **Ngưỡng chưa hiệu chỉnh**: test khẳng định không `CALIBRATION_REQUIRED` nào được dùng với severity
   trên `NUDGE` trong cấu hình release (`composition-engine` §9.2).
5. **Hiệu năng engine**: 10 000 khung tổng hợp < 2 ms (JVM) — chứng minh engine không phải nút cổ chai.
6. **Determinism**: cùng `(FrameAnalysis, State)` ⇒ kết quả giống hệt từng byte.

### 8.2 Tầng hợp nhất (`:core:guidance` khi tồn tại)

7. Bất biến **một-chính**: `primary != null ⇒ secondary.actor != primary.actor`.
8. Từng dòng bảng xung đột §5.4 là một test (hợp nhất / thắng theo điểm).
9. **Chống nhấp nháy**: chuỗi 30 Hz dao động ⇒ tối đa 1 lần đổi mỗi `minDwellMs`.
10. **Actor**: `SELF_MODE` không bao giờ phát hành động "di chuyển máy" mà không có ánh xạ chủ thể.

### 8.3 Tầng chiếu xạ UI

11. Mở rộng `PerceptionOverlayProjectionTest`: front/back × portrait/landscape × 4:3/16:9,
    mirror, stale, và **hộp mục tiêu** (sai số ≤ 1 px trên toạ độ hiển thị).
12. Bảng ánh xạ hướng (§6.3): mọi `facing × rotation` cho `MOVE_CAMERA_*`.

### 8.4 Tầng spec

13. `python specs/validation/validate_specs.py` → **0 error / 0 warning** sau mọi thay đổi
    `specs/` hoặc tham chiếu rule/message trong `docs/`.
14. Nếu thêm message/action mới: cập nhật `specs/i18n/messages.json` + explanation tương ứng,
    validator phải xanh.

### 8.5 Tầng thiết bị (KHÔNG chạy trong tác vụ này)

15. Các cổng Phase 5 theo roadmap: **G-P4** ≤ 35 ms/khung, **một-chính 60 s** không nhấp nháy,
    **L6** người mới khung tốt hơn sau 60 s hướng dẫn, **0 false positive** trên 30 ảnh tốt.
16. Mọi lần chạy máy thật cần: preflight camera-off NORMAL + `Ready` mới của chủ dự án,
    tối đa một lần, không tự retry — theo đúng checkpoint Phase 2 hiện hành.

### 8.6 Hiệu chỉnh (bắt buộc trước khi coi là "chốt")

Các hằng `T_*`, `k ≈ 1.45`, `V_min`, dải headroom, `m_min`, `t_joint` đều `CALIBRATION_REQUIRED`:
ghi log (id + giá trị) mỗi lần kích hoạt trong bản dev, hiệu chỉnh trên ảnh chụp cục bộ **có sự đồng ý**,
kết quả mới được nâng thành `DEVICE_TUNED`.

---

## 9. Lộ trình triển khai

Điều kiện vào: **Phase 2 được chấp nhận**, PR #3 xử lý xong, Phase 3 và 4 đi trước theo roadmap.
Các bước dưới đây là Phase 5 nội bộ; mỗi bước có cổng riêng.

| Bước | Nội dung | Phụ thuộc | Bằng chứng hoàn thành |
| --- | --- | --- | --- |
| **S0** | ~~Duyệt tài liệu + phân xử Q2 + chốt Q3–Q6~~ — **ĐÃ XONG 29/10/2026** (D1–D7). Còn lại: chốt R1–R4, R6 (§10.3) | chủ dự án | bảng D1–D7 trong tài liệu này; **chưa** ghi vào `docs/phase-status.md` vì file đang có thay đổi chưa commit của Codex → **R5** |
| **S1** | `CompositionEngine` thuần: mở rộng `ShotTypeEstimator` (dải bbox/face), 10 rule `FRAME_*`, `CompositionSummary` | Phase 4 accepted | test §8.1 xanh, không Android API |
| **S2** | `FramingStrategySelector` + `FramingTargetTracker` (EMA, hysteresis, reset) | S1 | test bảng chiến lược + độ ổn định target |
| **S3** | Nối vào arbiter hiện có → `GuidanceState`; message id đã có | S2 | test §8.2, `validate_specs.py` 0/0 |
| **S4** | Overlay sản phẩm (`src/main`) + bảng hướng + test chiếu xạ | S3, Phase 3 UI | §8.3 xanh; ảnh chụp màn hình khi được duyệt |
| **S5** | Readiness: chủ thể trong dải là bắt buộc, headroom khuyến nghị; chụp tay không bị chặn; auto-capture vẫn OFF | S4 | test readiness có kịch bản |
| **S6** | Hiệu chỉnh trên thiết bị (có đồng ý) → `DEVICE_TUNED` | S5 + `Ready` | số đo + phạm vi hiệu lực |
| **S7** | Cổng Phase 5: G-P4, một-chính 60 s, L6, 0 false positive/30 ảnh | S6 | ghi vào `docs/phase-status.md` |

Nguyên tắc: không bước nào được "tạm bỏ" guard hay hạ chuẩn để lấy màu xanh.

---

## 10. Câu hỏi: đã đóng và còn mở

### 10.1 Đã đóng ngày 29/10/2026 (không hỏi lại)

| ID cũ | Câu hỏi | Kết luận có hiệu lực | Nguồn |
| --- | --- | --- | --- |
| Q2 | `COMP_*` thuộc Phase 5 hay Phase 6? | **Phase 6**, giữ nguyên như JSON. MVP chỉ `FRAME_*` | **D1** |
| Q3 | "Tự chọn chiến lược" là gợi ý hay mặc định khoá? | **Tự động theo mặc định** khi tính năng bật; không khoá; người dùng tắt được | **D2 + D3** |
| Q4 | Hộp mục tiêu có hiện ở bản release? | **Có**, nhưng chỉ bản tối giản theo danh sách đóng §6.1, **không** landmark | **D4 + D5** |
| Q5 | Headroom là bắt buộc hay khuyến nghị cho readiness? | **Khuyến nghị** (advisory), không bao giờ chặn chụp tay | **D3** (suy ra: quy ước nghề không được thành lỗi khách quan) + `composition-engine` §2.1 |

Ghi chú trung thực: Q5 được đóng bằng **suy luận từ D3**, không phải một câu trả lời trực tiếp. Nếu chủ
dự án muốn headroom thành tiêu chí bắt buộc thì phải nói rõ, vì điều đó ngược với D3 cho trường hợp
headroom là quy ước `CALIBRATION_REQUIRED`.

### 10.2 Còn mở — kế thừa từ danh sách cũ

Vẫn giữ nguyên bảng dưới đây ở dạng **chưa duyệt**; không câu nào được coi là đã chốt.

| # | Câu hỏi | Vì sao cần quyết | Đề xuất |
| --- | --- | --- | --- |
| **Q1** | Tài liệu này giữ tiếng Việt, hay cần bản tiếng Anh song song? | Toàn bộ docs khác bằng tiếng Anh; agent sau có thể cần bản EN | Giữ VI (theo yêu cầu), dịch EN khi duyệt xong |
| **Q6** | Giao thức hiệu chỉnh: ai chụp, bao nhiêu mẫu, đồng ý thế nào, lưu ở đâu? | Ảnh người thật, riêng tư | Mẫu cục bộ, có đồng ý bằng văn bản, không commit |
| **Q7** | Có cho phép bắt đầu S1 trước khi Phase 2 được chấp nhận (chỉ trong nhánh dev)? | AGENTS.md cấm nhảy phase | Không; chờ chấp nhận Phase 2 |
| **Q8** | `SELF_MODE` (camera trước) có cần bộ chỉ dẫn riêng không (người dùng vừa là chủ thể)? | Tránh "hãy lùi lại" vô nghĩa khi tự chụp | Chỉ phát hành động khả thi trong SELF_MODE, hoặc chuyển sang hướng dẫn tư thế |
| **Q9** | `FRAME_BRIGHT_BLOB_BEHIND_HEAD` có đưa vào MVP UI không? | Cần luma 2 Hz; giá trị thấp, dễ gây nhiễu | Đưa vào dev trước, đánh giá false positive rồi mới bật |
| **Q10** | Wording tiếng Việt của banner/"vì sao?" | Nội dung hướng tới người dùng | Duyệt danh sách message hiện có (87) trước khi thêm chuỗi mới |

### 10.3 Mới phát sinh sau khi chốt D1–D7

| # | Câu hỏi | Vì sao cần quyết | Đề xuất |
| --- | --- | --- | --- |
| **R1** | Công tắc bật/tắt (D3) đặt ở đâu, lưu ở đâu? | `settings.gradle.kts` **không có** `:feature:settings`; `architecture.md` ghi module này vẫn deferred | **ĐÓNG (C2)** — công tắc trên màn hình camera + `SharedPreferences` cục bộ, không module mới; chi tiết chốt ở §3.4 |
| **R2** | Khi tắt hướng dẫn, tiêu chí bố cục có bị loại khỏi readiness không? | Nếu vẫn gate readiness thì "tắt" là không thật | **ĐÓNG (D8)** — loại hẳn tiêu chí bố cục khỏi readiness; bảng tiêu chí ở `guidance-engine.md` §7.1.1; chỉ tài liệu, chưa viết mã |
| **R3** | Xử lý `PerceptionOverlay` lộ landmark (C1) ở đâu: trong PR #3 hay để Phase 5? | D5 là bất biến đã duyệt | **ĐÓNG VÀ ĐÃ LÀM (C1)** — sửa trong nhánh Phase 2, có test; xem §6.2 và §12.1 |
| **R4** | Đo G-P4 khi overlay bật hay tắt? | Overlay cũng tiêu CPU/GPU → số đo phải nói rõ trạng thái | **CÒN MỞ.** Đo cả hai trạng thái, báo cáo tách riêng, không trung bình gộp |
| **R5** | Ai ghi D1–D8 vào `docs/phase-status.md`? | AGENTS.md yêu cầu cập nhật phase-status cuối phiên, nhưng file đang có sửa đổi chưa commit của Codex | **ĐÃ RÀ SOÁT — KHÔNG COMMIT (xem §14).** Ba file của Codex là ảnh chụp cũ của lịch sử Phase 2 đã nằm đầy đủ trên nhánh `codex/phase-2-pose-face-detection`; một file trùng byte-đệt, hai file có nội dung đã bị vượt qua. Ghi checkpoint Phase 2 ở nhánh Phase 2, không ở nhánh tài liệu |
| **R6** | Nhãn `GOOD / ACCEPTABLE / NEEDS_WORK` hiển thị thế nào khi **chưa có khoá chuỗi**? | Trước đây `specs/i18n/messages.json` không có khoá band nào | **ĐÓNG (C5)** — đã thêm `label.band.good` / `label.band.acceptable` / `label.band.needs_work` (vi+en); validator phải xanh |

---

## 11. Mâu thuẫn với spec hiện có (phát hiện, chưa sửa)

Mục này ghi lại cái **không khớp** giữa thiết kế, spec và mã. Mức độ phát hiện ban đầu: **CHẶN** = phải
xử lý trước khi phát hành; **CẦN CHỐT** = hai nguồn nói khác nhau, phải chọn một; **LƯU Ý** = không sai
nhưng dễ gây hiểu nhầm. Cột cuối ghi **trạng thái sau phiên 29/10/2026**: chỉ C1 (mã Phase 2) và C5
(chuỗi trong `specs/`) là thay đổi có thật trên đĩa; C2, C3, C4 được đóng bằng **quyết định + tài liệu**,
không bằng mã.

| ID | Mâu thuẫn | Bằng chứng | Mức | Đề xuất |
| --- | --- | --- | --- | --- |
| **C1** | Overlay Phase 2 vẽ xương Pose + chấm Face từ `src/main` và được gọi **vô điều kiện** — trái **D5** | `MainActivity.kt:133` gọi `PerceptionOverlay(camera)`; `PerceptionOverlay.kt` vẽ `PoseLandmarks.bones` + chấm face, tất cả trong `src/main` | **CHẶN** | **ĐÃ SỬA (IMPLEMENTED, trong Phase 2).** Chẩn đoán chuyển sang source set `debug`; `profile`/`release` là no-op; 4 test hồi quy; đối chứng `javap` 14 159 → 3 005 byte, xem §6.2 và §12.1 |
| **C2** | **D3** yêu cầu người dùng tắt được, nhưng chưa có màn hình settings nào tồn tại | `settings.gradle.kts` chỉ include `:app`, `:core:model`, `:core:geometry`, `:feature:camera`, `:perception:api`, `:core:photography`, `:perception:mediapipe`; `architecture.md` ghi `:feature:settings` deferred | **CHẶN** (cho D3) | **ĐÃ CHỐT (DOCUMENTED)** — công tắc trên màn hình camera + `SharedPreferences` cục bộ, không module mới; chi tiết ở §3.4 và R1 |
| **C3** | Cổng chủ thể dùng **bốn đại lượng khác nhau bị gọi lẫn**: `0.5` (scoreable), `0.55`/`0.60` (`V_min` + exit), `confidence ≥ 0.5` (arbiter), `200 ms` (ngân sách rule) vs `500 ms` (`MAX_AGE_MS`) | `docs/composition-engine.md` §3 pipeline; `docs/photography-rules.md` §4–§5; `specs/rules/mvp-rules.json` `V_min=0.55`, `exitValue=0.6`; `guidance-engine.md` §5.2; `SubjectAssembler.kt` `MAX_AGE_MS = 500L` | **CẦN CHỐT** | **ĐÃ CHỐT (DOCUMENTED)** — bảng hợp đồng duy nhất ở `docs/composition-engine.md` **§3.2** (đại lượng / giá trị / chủ sở hữu / thứ tự ưu tiên / không được nhầm với). Không đổi ngưỡng production nào; thiết kế này trích dẫn §3.2, không chép số |
| **C4** | `composition-engine` đặt `COMP_*` ở bước 9 của thứ tự đánh giá và trong ví dụ Phase 5, dù **D1** giữ chúng ở Phase 6 | `docs/composition-engine.md` §3.1 bước 9; §6 ví dụ có `COMP_EYE_LINE_PLACEMENT → NUDGE` | **LƯU Ý** | **ĐÃ SỬA (DOCUMENTED)** — `composition-engine.md` §3.1 thêm đoạn "ordering is not phase ownership" và §6 thêm câu ghi rõ ví dụ không phải lịch giao hàng; `roadmap.md` Phase 5 đổi thành "framing set only (`FRAME_*`)", Phase 6 nhận `COMP_*` |
| **C5** | **D4** cho phép overlay release, nhưng `CompositionSummary.overallBand` (`GOOD/ACCEPTABLE/NEEDS_WORK`) **không có chuỗi** vi/en | `specs/i18n/messages.json` (trước đây) chỉ có `guidance.frame.*` và `guidance.why.*` | **CẦN CHỐT** | **ĐÃ SỬA (IMPLEMENTED, spec).** Thêm namespace hiển thị `label.*` với 3 khoá vi+en; `message-catalog.schema.json` + `messages.json` notes ghi rõ namespace; validator tách `label.*` khỏi phép kiểm "key mồ côi" và **cấm** `label.*` xuất hiện như instruction/explanation; `composition-engine.md` §2.2 trỏ tới khoá |

**Đã đối chiếu và KHÔNG mâu thuẫn** (ghi lại để chứng minh không có số tự phát minh): bảng dải ở §4.3
khớp đúng chuỗi `math` của `FRAME_SUBJECT_TOO_SMALL` trong `specs/rules/mvp-rules.json`
(`FULL_BODY 0.75–0.98; THREE_QUARTER 0.55–0.85; HALF_BODY 0.35–0.60; CLOSE_PORTRAIT face 0.18–0.35;
HEADSHOT face 0.28–0.45`) và khớp `docs/photography-rules.md:138`; headroom `0.03–0.20` / `0.04–0.14`
khớp `fullBodyMin/Max` và `headshotMin/Max`. Các cổng shot-type `T_*` khớp `composition-engine` §4.

---

## 12. Nhiệm vụ triển khai nhỏ nhất tiếp theo

**Đề xuất: S1a — bổ sung nhánh `h_face` / `h_body` cho `ShotTypeEstimator` (thuần Kotlin, có test).**

Việc trước của mục này (gate overlay landmark, C1) **đã hoàn thành** trong phiên 29/10/2026 — xem §12.1.

| Khía cạnh | Nội dung |
| --- | --- |
| Vì sao là việc này trước | Thiếu nó thì **MVP chân dung một người không chạy được**: `ShotTypeEstimator.estimate()` hiện chỉ phân loại theo khớp "usable” và trả `UNKNOWN` khi chỉ thấy vai. Không có `h_face`, `HEADSHOT` và `CLOSE_PORTRAIT` **không bao giờ** được sinh ra, nên toàn bộ dải `0.18–0.35` / `0.28–0.45` của `FRAME_SUBJECT_TOO_SMALL`/`HEADROOM` (§4.3) là không thể tiếp cận |
| Bằng chứng | `core/photography/.../ShotTypeEstimator.kt`: `else -> ShotType.UNKNOWN` với comment "no calibrated boundary distinguishes headshot from close portrait yet"; `composition-engine.md` §4 đã có nhánh `h_face ≥ T_headshot` với `T_headshot = 0.30`, `T_close = 0.20` (`CALIBRATION_REQUIRED`) |
| Phạm vi | Một hàm thuần trong `:core:photography` + test bảng (mỗi ngưỡng ±1 mẫu biên) + test "confidence = 0 khi thiếu `face.bbox`". Không UI, không thêm model, không thêm module, không đổi `CompositionSummary` |
| Thuộc phase | **Phase 5**, sau khi Phase 2 được chấp nhận (AGENTS.md 1–2). Đây là mã *chuẩn bị* cho Phase 5, không phải bắt đầu Phase 5 |
| Cần gì trước khi làm | 1) Phase 2 được chấp nhận và PR #3 merge → ngưỡng `T_*` vẫn `CALIBRATION_REQUIRED` nên không được dùng cho lỗi cứng người dùng thấy; 2) chốt Q6 (giao thức hiệu chỉnh) để biết dữ liệu nào sẽ hiệu chỉnh chúng |
| Rủi ro nếu bỏ qua | Tự chọn chiến lược (D2) luôn rơi vào `UNKNOWN` → engine abstain → tính năng "tự động" thực chất không làm gì ở đúng ca phổ biến nhất (chân dung) |
| Việc **kế tiếp sau đó** | **S1b**: `FramingTargetTracker` (EMA + hysteresis + dwell, §4.5) thuần Kotlin; rồi **S2**: 10 rule `FRAME_*` đọc ngưỡng từ `specs/rules/mvp-rules.json`. Cả hai vẫn khoá bởi điều kiện Phase 2 → Phase 5 |

Nhiệm vụ trên **chưa** bắt đầu: lớp bố cục vẫn chưa có dòng mã nào. Phần duy nhất của thiết kế này đã
thành mã là C1 — và C1 là **sửa lỗi Phase 2**, không phải triển khai Smart Framing.

### 12.1 Phiên 29/10/2026 — những gì đã thực sự thay đổi trên đĩa

| Việc | Loại | Tệp | Bằng chứng kiểm chứng |
| --- | --- | --- | --- |
| C1 — landmark chỉ ở bản debug | **IMPLEMENTED** (mã Phase 2) | `app/src/main/.../PerceptionOverlayProjection.kt` (mới, toán chiếu thuần), `app/src/debug/.../DiagnosticLandmarkOverlay.kt` (mới, vẽ), `app/src/profile|release/.../DiagnosticLandmarkOverlay.kt` (mới, no-op), `app/src/main/.../MainActivity.kt`, `app/src/test/.../DiagnosticOverlayVariantTest.kt` (mới); xoá `app/src/main/.../PerceptionOverlay.kt` | 4/4 test xanh; `assembleRelease` + `assembleProfile` thành công; `javap`: debug 14 159 B (có `Canvas`, `PoseLandmarks.bones`), profile/release 3 005 B (không có) |
| C2 — công tắc | **DOCUMENTED** | §3.4 của tài liệu này | Không có mã; thuộc Phase 5 |
| C3 — hợp đồng confidence/staleness | **DOCUMENTED** | `docs/composition-engine.md` §3.2 (mới), §3 sửa dòng cổng | Không đổi ngưỡng production nào |
| C4 — chủ sở hữu phase | **DOCUMENTED** | `docs/composition-engine.md` §3.1 + §6, `docs/roadmap.md` Phase 5/Phase 6 | Validator 0 lỗi |
| C5 — nhãn band | **IMPLEMENTED** (spec) | `specs/i18n/messages.json` (3 khoá + notes), `specs/schemas/message-catalog.schema.json` (mô tả namespace), `specs/validation/validate_specs.py` (tách `label.*`), `docs/composition-engine.md` §2.2 | Validator 0 lỗi / 0 cảnh báo với 90 message |
| R2 — readiness khi tắt | **DOCUMENTED** (D8) | `docs/guidance-engine.md` §7.1.1 (mới), §3.3/§5.3/§10.3 của tài liệu này | Không đổi ngưỡng, không đổi `readyHoldMs`; validator 0 lỗi |
| R5 — rà soát 3 file Codex | **REVIEWED, KHÔNG COMMIT** | Không file nào bị sửa; kết luận + bằng chứng ở **§14** | `sha256` trùng byte-đệt; so sánh tiêu đề/tình trạng với nhánh Phase 2 |

---

## 13. Truy vết yêu cầu → thiết kế

| Yêu cầu | Được đáp ứng ở |
| --- | --- |
| 1. Tái dùng Pose/Face | §1, §2.1 (không suy luận mới) |
| 2. Chân dung một người trước | §0.2, §3.2, §4.3 |
| 3. Tự chọn chiến lược khung | §4.1–§4.4 |
| 4. Mục tiêu vị trí/tỉ lệ ổn định | §4.5, `FramingTargetTracker` |
| 5. Hướng dẫn di chuyển thời gian thực | §5 (arbiter sẵn có, nhịp pose) |
| 6. Mirror + xoay front/rear | §6.3, `analysisToPreview`, bảng hướng |
| 7. Không model nặng | §0.2, §7 |
| 8. Chạy offline | §0.2 (không INTERNET, không tải model) |
| 9. Tối thiểu nhiệt/hiệu năng | §7 (0,2 ms + 0,5 ms đều `[TGT]`, không timer mới, guard bất biến) |
| 10. Phân biệt đo được vs chủ quan | §4.6, §3.2, **D3** |

### 13.1 Truy vết quyết định chủ dự án → thiết kế

| Quyết định | Được thể hiện ở |
| --- | --- |
| **D1** (`COMP_*` ở Phase 6) | §0.3, §3.1 bảng, §4.4, §11 C4 |
| **D2** (tự động theo mặc định) | §0.4, §4.1, §4.4, §10.1 Q3 |
| **D3** (tắt được, không coi chủ quan là lỗi) | §0.4, §3.1 #12, §3.3, §4.6, §5.3, §10.1 Q5 |
| **D4** (overlay release tối giản) | §6.1 danh sách đóng, §10.1 Q4 |
| **D5** (không landmark ở release) | §6.1 cấm tuyệt đối, §6.2, §11 C1, §12 |
| **D6** (tái dùng kiến trúc) | §1, §2, §5 |
| **D7** (ngân sách là mục tiêu) | §7, `[TGT]` trên mọi số |
| **D8** (R2: tắt thì bỏ tiêu chí bố cục khỏi readiness) | §0.4, §3.3, §5.3, §10.3 R2, `guidance-engine.md` §7.1.1 |

---

### 13.2 Truy vết 17 hạng mục thiết kế được yêu cầu

| # | Hạng mục | Được đáp ứng ở | Trạng thái |
| --- | --- | --- | --- |
| 1 | Tái dùng perception Pose/Face | §1 (bảng 14 dòng), §2.1 | Theo thiết kế |
| 2 | MVP chân dung một người | §0.2, §3.1–§3.2, §4.3 | Theo thiết kế |
| 3 | Tự chọn chiến lược khung | §4.1–§4.4 | Theo thiết kế |
| 4 | Ước lượng loại khung + lỗ hổng đã biết | §4.2 (thiếu nhánh `h_face` trong `ShotTypeEstimator`), §12 S1a | Lỗ hổng đã xác minh bằng mã; chưa sửa |
| 5 | Sinh mục tiêu chủ thể ổn định | §4.5 (`FramingTargetTracker`, EMA/hysteresis/dwell) | Theo thiết kế |
| 6 | Hướng dẫn di chuyển thời gian thực | §5, §5.1 (dùng arbiter sẵn có) | Theo thiết kế |
| 7 | Ràng buộc headroom + tỉ lệ chủ thể | §3.1 #3–#4, §4.3, §5.2 #4 | Theo thiết kế |
| 8 | Mirror + xoay front/rear | §6.3, `analysisToPreview` (đã có) | Theo thiết kế |
| 9 | Overlay release tối giản | §6.1 (danh sách đóng) | Theo thiết kế |
| 10 | Landmark chẩn đoán chỉ ở bản dev | §6.2 | **IMPLEMENTED** trên nhánh Phase 2 (C1) |
| 11 | Hành vi bật/tắt | §3.3, §3.4, §5.3 | **DOCUMENTED** (C2) |
| 12 | Tích hợp máy trạng thái sẵn có | §5, §5.1 | Theo thiết kế |
| 13 | Chạy offline | §0.2 (không `INTERNET`, không tải model) | Theo thiết kế |
| 14 | Ngân sách hiệu năng/nhiệt | §7 (mọi số là `[TGT]`, không phải số đo) | Chưa đo trên thiết bị |
| 15 | Kiểm thử tự động + thiết bị | §8.1–§8.6 | Theo thiết kế |
| 16 | Lộ trình Phase 5 | §9 (S0…S7) | Theo thiết kế |
| 17 | Quyết định còn mở + rủi ro | §10.2, §10.3 (các mục CÒN MỞ), §11 | Còn 3 câu mở: R2, R4, R5 (cộng Q1, Q6–Q10) |

---

## 14. R5 — rà soát ba file tài liệu còn treo của Codex (kết luận: **KHÔNG** commit)

Chủ dự án duyệt R5 với điều kiện: nếu nội dung nhất quán thì commit riêng; nếu có xung đột thật thì
**không đoán, không ghi đè**, báo rõ mục xung đột và để nguyên file. Kết quả rà soát là trường hợp thứ hai.
Ba file ở main worktree (`codex/epose-inspired-plan-docs`) là **ảnh chụp cũ** của lịch sử Phase 2 mà
nhánh `codex/phase-2-pose-face-detection` đã có đầy đủ hơn. Bằng chứng cụ thể:

| File | Phát hiện | Hệ quả nếu commit |
| --- | --- | --- |
| `docs/phase-2-face-thermal-review-20261014.md` (untracked) | **Trùng byte-đệt** với bản đã commit trên nhánh Phase 2: `sha256 = 9bbd171f699cbadab5ab0b4b06c9a1ea325458d88c7c6b90567e8a2878186e93` sau khi chuẩn hoá CRLF | Thêm một file đã tồn tại trong lịch sử repo → xung đột `add/add` không tránh được khi merge, không thêm thông tin nào |
| `docs/phase-status.md` | Dòng đầu: main ghi **"Last updated: 2026-10-13"**, nhánh Phase 2 ghi **"2026-10-15"**. Dòng tình trạng: main ghi "one guarded rear-portrait Face-only run ... completed on **2026-10-11**", Phase 2 ghi "one **post-YUV** ... on **2026-10-14** ... 30-second counter window completed". Main thêm **3** mục ngày 10-14; Phase 2 đã có **15** mục đến 10-17 | Lùi mốc handoff và **mâu thuẫn trực tiếp** về kết quả: mục "ONE Face-only attempt after Ready; thermal abort" kết luận "the controlled 30-second Face-only/continuity gate remains pending", trong khi Phase 2 ghi "Post-YUV SM-S918B Face-only collection completed after Ready - 2026-10-14" |
| `docs/phase-2-physical-verification.md` | Tiêu đề tình trạng: main "ONE GUARDED FACE-ONLY WINDOW COMPLETED, **2026-10-11**" vs Phase 2 "ONE **POST-YUV** GUARDED FACE-ONLY WINDOW COMPLETED, **2026-10-14**". Đoạn 10-14 giống nội dung nhưng **khác tiêu đề** ("Latest Face-only interruption and offline review" vs "Latest Face-only interruption"). Phase 2 còn 4 mục main không có (tham chiếu checklist, quy tắc cửa sổ 30–60 s, preflight post-YUV dừng ở **42,6 °C MODERATE**) | Tạo hai mục gần trùng cho cùng một sự kiện, và làm mất/thụt các mục 10-14 → 10-17 đang là bản ghi mới nhất |

**Kiểm tra mất mát dữ liệu:** mọi con số nằm trong hunk chưa commit của Codex (`291 Face completions`,
`268 detections`, `266` cặp continuity, PSS `247,513` KiB, p50/p95 `39.242/49.487 ms`) đều **đã có**
trong file `phase-2-face-thermal-review-20261014.md` đã commit trên nhánh Phase 2. Vì vậy quyết định
không commit **không làm mất bằng chứng nào**.

**Kết luận và việc còn lại (không tự quyết):**

* Ba file được **giữ nguyên** trạng thái chưa commit ở main worktree; không bị sửa, không bị xoá.
* Chúng chỉ nên được xử lý bởi người **merge nhánh Phase 2 vào `main`** (lúc đó bản của Phase 2 thắng vì
  mới hơn và đầy đủ hơn), hoặc bởi Codex nếu muốn chuẩn hoá lại. Ghi chúng vào nhánh tài liệu là sai chỗ.
* Checkpoint của phiên 29/10/2026 được ghi ở `docs/phase-status.md` **trên nhánh Phase 2**
  (commit `6ecc863`), không phải ở nhánh tài liệu — đó là cách tránh trộn hai tác giả trong một hunk.

---

*Tài liệu này chủ yếu là thiết kế. Trong phiên 29/10/2026 có ba nhóm thay đổi có thật trên đĩa: sửa lỗi C1
(mã Phase 2, gate landmark vào bản debug), thêm khoá `label.band.*` (spec) và ghi hành vi readiness khi
tắt (D8/R2, tài liệu). Không có mã Smart Framing nào được viết. Không chạy ADB trên thiết bị thật, không
chạy camera thật, không đổi guard nhiệt, không merge PR #3, không bắt đầu Phase 3/Phase 5. Mọi số hiệu
năng vẫn là `[TGT]` cho đến khi có phép đo độc lập.*
