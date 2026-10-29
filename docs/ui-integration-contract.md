# Hợp đồng tích hợp UI — Smart Framing (Phase 5)

Tài liệu này là **hợp đồng** giữa tầng chức năng (đã hoàn thành) và model phát triển giao diện.
Nó chỉ định nghĩa *dữ liệu vào/ra* và *quy tắc sử dụng*; **không** quy định thiết kế thị giác, màu sắc,
bố cục màn hình hay animation.

Nguồn sự thật song song: [`docs/ai-smart-framing-design.md`](ai-smart-framing-design.md) (quyết định D1–D8),
[`docs/guidance-engine.md`](guidance-engine.md) (§5–§7), [`docs/composition-engine.md`](composition-engine.md)
(§2, §3.2). Nếu tài liệu này và các tài liệu kia lệch nhau, tài liệu kia thắng và tài liệu này phải sửa.

---

## 1. Phạm vi

| Có trong tầng chức năng | Thuộc về UI model |
| --- | --- |
| CameraX preview/capture, chuyển camera, lưu ảnh (`:feature:camera`) | Bố cục màn hình, theme, icon |
| MediaPipe Pose/Face, lịch suy luận, guard nhiệt (`:perception:*`) | Vẽ overlay (hộp mục tiêu, hộp chủ thể, banner) |
| Engine bố cục + arbiter + readiness (`:core:photography`) | Sheet "vì sao?", nút công tắc, nút chụp |
| `SmartFramingController` + preference (`:app`, package `framing`) | Điều hướng, chuyển màn hình |

UI **không** được tự tính toán bố cục, tự soạn lời khuyên, hay tự quyết định khi nào một chỉ dẫn hợp lệ.

---

## 2. Kiểu dữ liệu trạng thái

Package: `com.aiphotographer.app.framing` (`:app`).

```kotlin
data class SmartFramingUiState(
    val enabled: Boolean,                 // công tắc đang bật?
    val shotIntent: ShotType?,            // ghi đè tuỳ chọn; null = engine tự chọn (mặc định, D2)
    val strategyId: String?,              // "FULL_BODY_CENTERED" | ... | null khi abstain
    val targetBox: RectUi?,               // hộp dung sai cần vẽ; null = không vẽ
    val subjectBox: RectUi?,              // viền mảnh quanh chủ thể; null khi mất chủ thể
    val primary: GuidanceUi?,             // tối đa MỘT chỉ dẫn chính
    val secondary: GuidanceUi?,           // chỉ khi khác actor
    val summary: SummaryUi?,              // nhãn band + số đo (chỉ hiển thị)
    val readiness: ReadinessUi,
    val neutralReason: String?,           // "NO_SUBJECT" | "STALE" | "DEGRADED" |
                                          // "SHOT_TYPE_UNKNOWN" | "LOW_CONFIDENCE" | "DISABLED" | null
    val explanationKey: String?,          // khoá guidance.why.* cho sheet "vì sao?"
    val overlayVisible: Boolean,          // điều kiện duy nhất để được vẽ overlay bố cục
)

data class RectUi(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class GuidanceUi(
    val instructionId: String,   // id hành động ngữ nghĩa, ví dụ "MOVE_CLOSER"
    val messageId: String,       // khoá chuỗi, ví dụ "guidance.frame.move_closer"
    val actor: String,           // "PHOTOGRAPHER" | "SUBJECT" | "BOTH"
    val direction: String?,      // "CLOSER" | "DOWN" | ... | null
    val magnitude: Float,        // 0..1, mức lệch đã chuẩn hoá (không phải pixel)
    val severity: String,        // "INFO" | "NUDGE" | "IMPORTANT" | "BLOCKING"
    val confidence: Float,       // 0..1
    val ruleIds: List<String>,   // nguồn gốc, ví dụ ["FRAME_SUBJECT_TOO_SMALL"]
)

data class SummaryUi(
    val bandId: String,              // "GOOD" | "ACCEPTABLE" | "NEEDS_WORK"
    val bandLabelKey: String,        // "label.band.good" | ... (dùng trực tiếp làm stringResource)
    val shotType: String,
    val subjectHeightFraction: Float,
    val headroomRatio: Float?,
    val eyeLineFromTop: Float?,
    val subjectCentreOffset: Float,
    val cameraRollDegrees: Float?,
)

data class ReadinessUi(
    val state: String,               // "NOT_READY" | "NEAR_READY" | "READY"
    val reasons: List<String>,       // khoá guidance.why.*; rỗng khi đủ điều kiện
    val compositionIncluded: Boolean,// false khi Smart Framing tắt (D8)
    val blocksManualCapture: Boolean // LUÔN false — xem §7
)
```

Mọi trường chuỗi là **định danh**, không phải chữ hiển thị. UI tra bảng ở §9.

---

## 3. StateFlow

```kotlin
class SmartFramingController(
    perception: PerceptionPipeline?,
    settings: SmartFramingSettings,
    scope: CoroutineScope,
    engineConfig: EngineConfig = EngineConfig(),
) : AutoCloseable {
    val state: StateFlow<SmartFramingUiState>
    val isEnabled: Boolean
    fun setEnabled(enabled: Boolean)
    fun setShotIntent(value: ShotType?)
    fun resetLayer()
    override fun close()
}
```

```kotlin
val controller = SmartFramingController(camera.perception, SmartFramingSettings(context), rememberCoroutineScope())
val ui by controller.state.collectAsStateWithLifecycle()
DisposableEffect(controller) { onDispose { controller.close() } }
```

`perception == null` (chế độ `framingOnly`/preview-only) là hợp lệ: controller vẫn tồn tại, `state` chỉ có
`enabled` + `neutralReason = "DISABLED"`, không có job nào chạy.

`MainActivity` hiện đã tạo và huỷ controller cạnh `CameraSession` (một `remember` + một `DisposableEffect`,
không vẽ gì). UI model có thể **giữ nguyên** chỗ đó và chỉ đọc `state`, hoặc chuyển việc tạo controller vào
composable của mình — miễn mỗi camera có đúng một controller và nó được `close()`.

---

## 4. Hành động người dùng

| Hành động | Gọi | Hiệu lực |
| --- | --- | --- |
| Bật/tắt Smart Framing | `setEnabled(Boolean)` | Tức thời, không cần khởi động lại; tự ghi preference |
| Chọn ý định khung hình (tuỳ chọn) | `setShotIntent(ShotType?)` | `null` = engine tự chọn (mặc định) |
| Reset lớp bố cục | `resetLayer()` | Xoá latch/EMA/target/cooldown khi UI tự đổi bối cảnh |
| Chụp ảnh | `camera.takePhoto()` (không đổi) | Không phụ thuộc readiness |
| Chuyển camera | tái tạo `CameraSession` (đã có) | Controller mới → trạng thái bố cục mới |

Không có API nào để UI tự đặt chỉ dẫn, tự chọn band, hay tự tính hộp mục tiêu. Cố tình không có.

---

## 5. Vòng đời camera

| Sự kiện | UI cần làm | Vì sao |
| --- | --- | --- |
| Đổi camera trước/sau | Không làm gì thêm (controller được tạo lại theo `key`) | `FrameGeometry` đổi → engine reset |
| Đổi aspect / resolution | Như trên | idem |
| Xoay máy (recreate) | Như trên | idem |
| `ON_STOP` → `ON_START` | Gọi `resetLayer()` nếu controller được giữ qua lifecycle | tránh target cũ sống lại |
| Cấp/thu hồi quyền camera | Không liên quan — controller không giữ camera | — |

`PerceptionStatus`, `DiagnosticLandmarkOverlay`, `DeveloperOverlay` **giữ nguyên** hành vi Phase 2.
Chẩn đoán landmark chỉ tồn tại ở source set `debug` (C1); UI không được thêm bất kỳ hình vẽ landmark nào.

---

## 6. Công tắc (D2, D3, D8)

* Mặc định **bật** (`SmartFramingSettings.DEFAULT_ENABLED = true`), lưu ở `SharedPreferences`
  khoá `smart_framing_enabled_v1`.
* Bật = engine tự đề xuất; **không** có bước chọn kiểu chụp trước.
* Tắt = engine **không được gọi**, `targetBox`/`subjectBox`/`summary`/`primary` đều `null`,
  `neutralReason = "DISABLED"`, `overlayVisible = false`. Không được để "hộp ma" một khung.
* Bật lại = trạng thái mới hoàn toàn: không latch, không target, không cooldown cũ.
* Khi tắt, `readiness.compositionIncluded == false` và `readiness.reasons` **không** chứa lý do bố cục.
* Nút chụp **không** bị khoá trong mọi trường hợp.

---

## 7. Guidance, readiness và chụp ảnh

* `primary` là chỉ dẫn duy nhất được hiển thị; `secondary` chỉ xuất hiện khi khác `actor`.
  UI không được hiển thị quá hai, và không được đổi thứ tự.
* `summary.bandLabelKey` là **nhãn hiển thị**, không phải điểm số. Không hiển thị `bandId` thô,
  không quy đổi thành "x/10".
* Sheet "vì sao?" tra `explanationKey` (một khoá `guidance.why.*`), và có thể hiển thị số đo từ `summary`.
* `readiness.state` chỉ là thông tin; `blocksManualCapture` luôn `false` và UI không được suy ra điều ngược lại.
* **Luồng chụp không đổi**: nút chụp gọi `camera.takePhoto()`; `capture`/`savedLocation` vẫn đọc từ
  `CameraSession.state` như Phase 2. Smart Framing không chèn bước nào vào luồng lưu ảnh.

---

## 8. Không gian toạ độ và mirroring

* `RectUi` là toạ độ **chuẩn hoá 0..1 trong ANALYSIS đã upright, chưa mirror** — đúng `FrameAnalysis.frame`.
* Chiếu sang view: dùng `Coordinates.analysisToPreview(Point(x, y), frameWidth, frameHeight, viewWidth,
  viewHeight, mirrored = frame.isMirrored)` (đã có, đã có test). Không tự viết phép biến đổi thứ hai.
* Với camera trước (`isMirrored = true`), phép chiếu tự lật X; **không** lật thêm lần nữa trong Compose.
* `frame.rotationDegrees` đã được áp dụng đúng một lần ở pipeline; UI không xoay lại.
* Hộp mục tiêu và hộp chủ thể dùng **cùng** phép chiếu. Sai số cho phép ≤ 1 px ở toạ độ hiển thị.

---

## 9. Khoá bản địa hoá

Nguồn: `specs/i18n/messages.json`. UI cần thêm các khoá sau vào `values/strings.xml` (en) và
`values-vi/strings.xml` (vi) với cùng tên.

| `messageId` / khoá | Dùng ở đâu |
| --- | --- |
| `guidance.frame.move_closer` | `MOVE_CLOSER` (chủ thể quá nhỏ) |
| `guidance.frame.move_farther` | `MOVE_FARTHER` (chủ thể quá lớn) |
| `guidance.frame.zoom_in` / `guidance.frame.zoom_out` | biến thể zoom (advisory) |
| `guidance.frame.increase_headroom` | `INCREASE_HEADROOM` |
| `guidance.frame.reduce_headroom` | `DECREASE_HEADROOM` |
| `guidance.frame.reframe` | `REFRAME` (lề mép, lệch tâm cực đoan) |
| `guidance.frame.reframe_joint` | `REFRAME` biến thể cắt khớp |
| `guidance.frame.level_camera` | `LEVEL_CAMERA` |
| `guidance.frame.move_camera_down` | `MOVE_CAMERA_DOWN` |
| `guidance.frame.move_closer_lower_camera` | `COMPOSITE` (gộp "lại gần" + "thêm khoảng trên đầu") |
| `guidance.frame.wait_for_subject` | `WAIT` (chưa có người trong khung) |
| `guidance.pose.step_inward` | `SUBJECT_MOVE_INWARD` (chỉ dẫn cho chủ thể) |
| `guidance.why.frame_subject_present` … `guidance.why.frame_camera_tilt` | sheet "vì sao?" |
| `guidance.why.ready_subject_stable` / `guidance.why.ready_camera_stable` | lý do readiness |
| `label.band.good` / `label.band.acceptable` / `label.band.needs_work` | nhãn band (C5) |

Quy tắc: `label.*` là nhãn hiển thị, **không bao giờ** được phát như một `GuidanceInstruction`
(validator `specs/validation/validate_specs.py` chặn việc này).

---

## 10. Trạng thái loading / lỗi / tắt

| `neutralReason` | Nghĩa | UI nên làm |
| --- | --- | --- |
| `NO_SUBJECT` | Chưa thấy người (hoặc < 15 khung) | Trạng thái trung tính "đang chờ người vào khung"; **không** hiện lỗi |
| `STALE` | Đo cũ hơn 200 ms | Trung tính; **không** vẽ hộp mục tiêu cũ |
| `DEGRADED` | `degradationLevel ≥ 5` | Trung tính; giữ nguyên camera |
| `SHOT_TYPE_UNKNOWN` | Chưa phân loại được khung hình | Trung tính |
| `LOW_CONFIDENCE` | Chủ thể chưa đủ rõ để khuyên | Trung tính |
| `DISABLED` | Người dùng đã tắt | Không vẽ gì |
| `null` + `primary == null` | Đang trong dải tốt | Hiện band `GOOD` (cửa sổ khen 600 ms do arbiter quyết định) |

Không có trạng thái "lỗi AI". Engine thuần không ném ngoại lệ ra UI; nếu pipeline perception lỗi,
`perception.state` đã có `SourceStatus` riêng (Phase 2) và UI hiển thị như cũ.

---

## 11. Threading và hiệu năng

* Engine chạy trên `Dispatchers.Default` ở nhịp `perception.state` (8–15 Hz theo `PerceptionSchedule`).
  **Không** thêm timer, không thêm cadence, không thêm luồng suy luận.
* `state` là `StateFlow` — đọc bằng `collectAsStateWithLifecycle()`; không đọc đồng bộ trong `onDraw`.
* Overlay vẽ theo nhịp khung hình hiển thị (Compose), không theo nhịp suy luận.
* Không cấp phát mảng lớn mỗi khung; `RectUi`/`GuidanceUi` là object nhỏ, bất biến.
* Khi tắt: chi phí engine = 0 (không gọi), không cấp phát target.
* Ngân sách tham chiếu — **tất cả là mục tiêu `[TGT]`, chưa đo trên thiết bị**:
  engine ≈ 0,2 ms/khung pose; hợp nhất chỉ dẫn ≈ 0,5 ms; G-P4 ≤ 35 ms/khung phân tích trên MEDIUM.
  Đo JVM đã có (`EngineBudgetTest`, 10 000 khung, giới hạn < 2 ms/khung) — **không** phải số đo thiết bị.

---

## 12. Ví dụ tích hợp (giả mã)

```kotlin
@Composable
fun CameraScreen(camera: CameraSession, controller: SmartFramingController) {
    val ui by controller.state.collectAsStateWithLifecycle()
    val capture by camera.state.collectAsStateWithLifecycle()

    Box {
        CameraPreview(camera) // đã có

        if (ui.overlayVisible && ui.targetBox != null) {
            FramingOverlay(
                target = ui.targetBox!!,           // chiếu bằng Coordinates.analysisToPreview
                subject = ui.subjectBox,
                frame = camera.perception?.state?.value?.snapshot?.frame, // upright, unmirrored; null = chưa có
            )
        }

        ui.primary?.let { instruction ->
            Text(stringResource(instruction.messageId.asStringRes())) // banner một dòng
            TextButton(onClick = { showWhy(ui.explanationKey) }) { Text("?") }
        }

        ui.summary?.let { Text(stringResource(it.bandLabelKey.asStringRes())) }

        Row {
            TextButton(onClick = { controller.setEnabled(!ui.enabled) }) {
                Text(if (ui.enabled) "Bố cục: Bật" else "Bố cục: Tắt")
            }
            Button(onClick = camera::takePhoto, enabled = capture.capture != CaptureStatus.SAVING) {
                Text(stringResource(R.string.capture))
            }
        }
    }
}
```

`asStringRes()` là helper của UI model (ánh xạ chuỗi khoá → `R.string.*`); tầng chức năng không biết
`R` và không giữ chuỗi hiển thị.

---

## 13. Những điều UI **không** được làm

Danh sách đóng theo **D4/D5** (`docs/ai-smart-framing-design.md` §6.1):

* Không vẽ xương Pose, chấm Pose, chấm Face — ở bất kỳ build nào.
* Không hiển thị id landmark, giá trị ngưỡng thô, `strategyId`, confidence dạng số.
* Không thêm thành phần ngoài danh sách overlay tối giản (hộp mục tiêu, hộp chủ thể, banner 1 chỉ dẫn,
  "vì sao?", trạng thái trung tính, nhãn band) mà không có quyết định mới của chủ dự án.
* Không hiển thị điểm số / thang điểm / "cân bằng thị giác" / rule-of-thirds.
* Không khoá nút chụp vì lý do bố cục.
* Không tự tính lại bất kỳ số đo nào từ landmark.
