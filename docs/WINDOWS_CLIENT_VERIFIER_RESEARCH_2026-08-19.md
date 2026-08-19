# Nghiên cứu client verifier Windows — 2026-08-19

## Trạng thái RAG

- `Verified`: docs/source chính thức hoặc kết quả thực tế đã có.
- `Inferred`: suy luận từ bằng chứng; chưa reproduction.
- `Unknown`: chưa đủ bằng chứng.

Chưa code client, chưa build, chưa scan, chưa ký file, chưa test Windows Defender.

## Câu hỏi

Server chỉ chạy plugin Paper `1.21.11`; có thể yêu cầu người chơi cài phần mềm verifier mà không biến server thành modded server không? Phần mềm phải giảm khả năng bị Windows Defender/SmartScreen nhận nhầm là malware.

## Kết quả

### 1. Server plugin-only + client mod

**Verified:** Paper docs mô tả plugin messaging là kênh plugin giao tiếp với client; Velocity docs mô tả plugin messaging tới client hữu ích khi client có mod.

Sources:

- `https://docs.papermc.io/paper/dev/plugin-messaging`
- `https://docs.papermc.io/velocity/dev/plugin-messaging`

**Verified:** Fabric docs mô tả client gửi và nhận custom payload bằng `ClientPlayNetworking`, server nhận bằng `ServerPlayNetworking`.

Source:

- `https://docs.fabricmc.net/develop/networking`

**Kết luận:** Có thể giữ server là Paper plugin-only và phát hành client-side Fabric mod riêng. Đây là compatibility statement ở mức API/docs; exact Minecraft `1.21.11` client build vẫn phải reproduction.

**Không được kết luận:** custom payload tự tạo trusted attestation. Payload có thể bị client giả mạo.

### 2. Không cần NeoForge chỉ vì server là Paper

**Verified:** NeoForge networking rework dùng `CustomPacketPayload` cho configuration/play; nguồn chính thức nói không thể đăng ký custom payload ở login phase và protocol hiện tại chưa hỗ trợ truyền mod list.

Source:

- `https://neoforged.net/news/20.4networking-rework`

**Inferred:** Chọn NeoForge sẽ tăng yêu cầu cài loader và giảm compatibility client so với client-side Fabric mod. Chưa benchmark nên chưa gọi đây là kết quả hiệu năng.

### 3. Fabric mod không phải phần mềm anti-malware/kernel

**Đề xuất an toàn, chưa implementation:** verifier chỉ là Java client mod chạy trong Minecraft:

- nhận challenge ở protocol phase được hỗ trợ;
- trả protocol response;
- heartbeat bounded;
- không driver/kernel;
- không DLL injection;
- không hook JVM ngoài API loader;
- không đọc process list;
- không scan file ngoài chính mod/config cần thiết;
- không đọc credential/browser/token;
- không tự tải và chạy executable khác;
- không yêu cầu administrator;
- không tự tắt Defender/SmartScreen;
- không tự whitelist chính nó.

**Lý do:** các hành vi trên có thể tạo rủi ro bảo mật, privacy risk và tăng khả năng bị heuristic antivirus đánh dấu. Đây là engineering safety recommendation, chưa phải claim của Microsoft về một file cụ thể.

### 4. SmartScreen khác malware detection

**Verified:** Microsoft Learn ghi EV certificates không còn tự động bypass SmartScreen; reputation xây dựng organically qua download volume. Microsoft Store được nêu là kênh khuyến nghị trong ngữ cảnh Windows app.

Source:

- `https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation`

**Verified:** Microsoft Learn material về Authenticode nêu signing giúp thiết lập/chia sẻ reputation; certificate phải từ CA thuộc Windows Root Certificate Program để thiết lập reputation.

Sources:

- `https://learn.microsoft.com/en-ca/archive/blogs/ieinternals/everything-you-need-to-know-about-authenticode-code-signing`
- `https://learn.microsoft.com/en-us/answers/questions/2745466/defender-smartscreen-warning`

**Verified:** EV code signing không phải cách bảo đảm SmartScreen không cảnh báo.

**Unknown:** thời điểm và mức reputation cụ thể của project mới; không thể hứa người chơi sẽ không thấy cảnh báo.

### 5. Ký Java JAR chưa đồng nghĩa Windows trust hoàn chỉnh

**Verified:** Windows Authenticode/SmartScreen trust chủ yếu áp dụng cho executable/package được Windows đánh giá; Fabric mod là JAR chạy qua Java/Minecraft launcher.

**Unknown:** tác động exact của Authenticode lên JAR tải qua launcher và cảnh báo Defender trên từng kênh phân phối. Không được nói “ký JAR là hết virus warning”.

**Khuyến nghị:** nếu có Windows bootstrapper/installer, ký installer và executable bằng Authenticode; mod JAR phát hành qua HTTPS, checksum/signature riêng và repository chính thức. Không tạo native launcher ở MVP nếu chưa cần.

### 6. Antivirus false positive

**Verified:** SmartScreen reputation có thể phụ thuộc download history/popularity, signature, site reputation và AV results theo Microsoft material.

**Unknown:** Defender sẽ phân loại build tương lai thế nào. Chỉ test artifact cụ thể mới trả lời được.

**Quy trình evidence bắt buộc mỗi release:**

1. Build clean trong CI.
2. Sinh SHA-256.
3. Ký artifact nếu artifact format hỗ trợ; ký installer/native bootstrapper bằng Authenticode.
4. Upload artifact vào VirusTotal chỉ khi user chấp thuận chính sách privacy; không upload private build chứa secret.
5. Test trên Windows Defender sạch, Windows Sandbox/VM disposable.
6. Lưu kết quả engine/version/date, không tuyên bố “an toàn tuyệt đối”.
7. Nếu false positive, dùng kênh Microsoft Defender submission phù hợp; không hướng dẫn người chơi tắt Defender.
8. Phát hành changelog, source commit, build instructions, checksum, signature/public key.

## So sánh lựa chọn

| Lựa chọn | Server Paper plugin-only | Cài client | Khả năng giảm AV risk | Pre-login proof | Trạng thái |
|---|---:|---:|---|---|---|
| Fabric client-side Java mod | Có | Fabric + mod | Cao hơn native app nếu không có hành vi đáng ngờ; chưa test | Unknown | Khuyến nghị làm spike đầu |
| NeoForge client mod | Có về mặt protocol cần test | NeoForge + mod | Tương tự Java mod; thêm loader burden | Unknown | Chưa chọn |
| Native Windows `.exe` verifier | Có | App riêng | Thấp hơn nếu unsigned/native injection; ký và behavior sạch mới đánh giá | Unknown | Không khuyến nghị MVP |
| Kernel driver/low-level anti-cheat | Có | Quyền cao | Rủi ro AV/privacy/support cao | Unknown | Loại khỏi scope |
| Vanilla client + plugin message | Có | Không | Không áp dụng | Không chứng minh client verifier | Không đủ cho mandatory verifier |

## Kết luận có bằng chứng

1. **Khuyến nghị kiến trúc MVP:** Paper plugin + Fabric client-side Java mod, không đổi server sang Fabric/NeoForge. Đây là recommendation dựa trên API docs và mục tiêu server plugin-only; exact `1.21.11` compatibility vẫn `Unknown` đến khi reproduction.
2. **Không làm native `.exe` hoặc driver ở MVP.** Giảm attack surface và giảm nhóm hành vi thường gây lo ngại, nhưng không phải bảo đảm Defender không false positive.
3. **Không hứa “không bị Windows nhận virus”.** Cam kết đúng: không yêu cầu admin, không native injection, không process/file/credential scan, open source, reproducible build, checksum, release signing, minh bạch telemetry.
4. **Client verifier không chống hack tuyệt đối.** Server detection vẫn bắt buộc.
5. **Không dùng Velocity chỉ để chống hack.** Velocity chỉ thêm topology/proxy; pre-login attestation exact chưa được chứng minh.
6. **Không chọn PacketEvents/ProtocolLib từ research này.** Cần spike exact Paper `1.21.11` trước.

## Reproduction tiếp theo

- Tạo test client Fabric target exact `1.21.11`.
- Tạo Paper plugin nhỏ nhận custom payload.
- Đo phase thực tế: configuration/play; ghi log packet state và timeout.
- Test vanilla client: phải bị phân biệt khỏi verifier client nhưng không được coi response là trusted attestation.
- Test modified/fake client: gửi payload giả; chứng minh server chỉ xem đây là signal, không security proof.
- Sau đó spike PacketEvents và ProtocolLib, mỗi thư mục/repo độc lập.
- Test Windows artifact sau khi có client JAR; không test bằng claim.

## Sources

- `https://docs.papermc.io/paper/dev/plugin-messaging`
- `https://docs.papermc.io/velocity/dev/plugin-messaging`
- `https://docs.fabricmc.net/develop/networking`
- `https://neoforged.net/news/20.4networking-rework`
- `https://docs.papermc.io/velocity/server-compatibility`
- `https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation`
- `https://learn.microsoft.com/en-ca/archive/blogs/ieinternals/everything-you-need-to-know-about-authenticode-code-signing`
- `https://learn.microsoft.com/en-us/answers/questions/2745466/defender-smartscreen-warning`

## Trạng thái

- RAG research: `Verified` facts recorded.
- Recommendation: `Inferred`, pending exact reproduction.
- Fabric selection: chưa chốt chính thức.
- Native Windows verifier: chưa code; không nằm trong MVP recommendation.
- Production: không deploy/restart.
- Artifact hash: chưa có; không bịa.
- Windows Defender scan: chưa chạy; không bịa.
- User-facing safety promise: chỉ cam kết behavior/code policy, không cam kết AV outcome.
