# Decision record — server plugin-only và client verifier

Ngày: `2026-08-19`
Trạng thái: `RECOMMENDED_PENDING_SPIKE`
RAG: mọi claim bên dưới gắn `Verified`, `Inferred` hoặc `Unknown`.

## Câu hỏi

Server dùng Paper plugin, không chạy modded server. Cần anti-cheat server plugin và phần mềm/verifier cho người chơi cài; tránh tăng nguy cơ Windows Defender/SmartScreen nhận nhầm.

## Evidence đã xác minh

### Paper/client communication

`Verified` — Paper docs mô tả plugin messaging là kênh plugin giao tiếp với client:

- https://docs.papermc.io/paper/dev/plugin-messaging

`Verified` — Fabric docs mô tả custom payload hai chiều bằng `ClientPlayNetworking` và server receiver:

- https://docs.fabricmc.net/develop/networking

`Verified` — Fabric có hướng dẫn riêng cho Minecraft `1.21.11`; Fabric docs chỉ rõ mod phải đúng Minecraft version và đúng loader:

- https://fabricmc.net/2025/12/05/12111.html
- https://docs.fabricmc.net/players/installing-mods
- https://docs.fabricmc.net/develop/loom

### NeoForge

`Verified` — NeoForge networking rework hỗ trợ payload ở configuration/play, nhưng nguồn chính thức ghi không thể đăng ký custom payload ở login phase:

- https://neoforged.net/news/20.4networking-rework

`Verified` — cùng nguồn ghi protocol hiện tại chưa hỗ trợ truyền mod list.

### Velocity

`Verified` — Velocity hỗ trợ Paper và modern forwarding tới Paper `1.13.2+`:

- https://docs.papermc.io/velocity/server-compatibility

`Unknown` — modern forwarding có phải client verifier attestation hay không. Không dùng forwarding làm security proof.

### Windows trust

`Verified` — Microsoft ghi EV certificate không còn tự động bypass SmartScreen; reputation xây dựng theo download history/volume và các tín hiệu khác:

- https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation

`Verified` — Microsoft Artifact Signing FAQ ghi SmartScreen reputation build tự động; file mới vẫn có thể hiện prompt, và có thể submit file để review:

- https://learn.microsoft.com/en-us/azure/artifact-signing/faq

`Verified` — Microsoft mô tả Authenticode/code signing và timestamping cho binary/package; chữ ký chứng minh publisher/integrity, không chứng minh binary không có bug hoặc không bị heuristic flag.

## Quyết định kiến trúc hiện tại

### Dùng gì

`Recommended / Inferred`: giữ server là Paper plugin-only; tạo client verifier dưới dạng Fabric client-side Java mod cho Minecraft `1.21.11`.

Lý do:

- Không buộc server chuyển sang NeoForge/Fabric.
- Fabric official docs có API networking và tài liệu target `1.21.11`.
- Không cần native executable, driver hoặc launcher đặc quyền ở MVP.
- Phù hợp mục tiêu cài phần mềm nhẹ trong thư mục `mods`.

### Không dùng gì ở MVP

`Decision`: không làm:

- Windows kernel driver.
- DLL injection/JVM injection ngoài loader API.
- Process scan.
- Scan toàn bộ file máy.
- Đọc browser/session token/credential.
- Tắt hoặc yêu cầu người chơi whitelist Defender.
- Tự tải/chạy executable từ mod.
- Native `.exe` verifier nếu chưa có yêu cầu bắt buộc.

Đây là quyết định giảm attack surface/privacy và giảm các hành vi dễ bị heuristic đánh dấu. `Unknown`: không thể suy ra Defender sẽ luôn cho phép artifact.

### Có cần Velocity không

`Decision`: không thêm Velocity chỉ vì anti-cheat.

- Nếu server hiện không có proxy, plugin Paper + verifier client là topology nhỏ hơn.
- Nếu sau này cần nhiều backend/server hoặc login gateway thật, đánh giá Velocity trong change riêng.
- `Unknown`: custom attestation trước login vẫn chưa được reproduction.

### Packet adapter

`Decision`: chưa chọn ProtocolLib hoặc PacketEvents.

Cần compatibility spike exact trước. Release notes của cả hai có các claim support liên quan `1.21.11`, nhưng claim release không thay runtime test:

- https://github.com/retrooper/packetevents/releases
- https://github.com/dmulloy2/ProtocolLib/releases

## Giới hạn security

`Verified by threat model, not runtime`: client mod không thể chứng minh máy sạch cheat. Attacker có thể patch mod, giả payload hoặc proxy traffic. Server movement/combat/inventory authority vẫn bắt buộc.

`Unknown`: mức bypass resistance của verifier cụ thể trước khi có threat-model test.

Không gọi handshake là “trusted attestation” ở MVP. Gọi đúng: `client verifier presence/protocol signal`.

## Windows distribution contract

Được cam kết:

- Source code public hoặc audit-able.
- Build reproducible ở mức dự án chứng minh được.
- Artifact có SHA-256.
- Phát hành qua HTTPS/repository chính thức.
- JAR không chứa secret.
- Không yêu cầu administrator.
- Không native code/driver/injection/process scan ở MVP.
- Ghi rõ dữ liệu verifier gửi server.
- Release artifact test trên Windows VM/Sandbox trước công bố.
- Nếu có installer/native bootstrapper về sau: ký Authenticode và timestamp; vẫn không hứa SmartScreen không cảnh báo.

Không được cam kết:

- “Windows chắc chắn không nhận virus”.
- “Không bao giờ có false positive”.
- “Chữ ký số chứng minh file an toàn tuyệt đối”.
- “Client mod phát hiện mọi cheat”.

## Gate trước implementation

1. Tạo minimal Paper plugin exact `1.21.11`.
2. Tạo minimal Fabric client mod exact `1.21.11`.
3. Gửi challenge sau khi connection vào phase mà cả hai API hỗ trợ; ghi phase thực tế.
4. Client trả response; server validate schema, size, timeout, duplicate.
5. Vanilla client không trả verifier response; kiểm tra server chuyển quarantine/kick theo policy test.
6. Fake client trả response; chứng minh response chỉ là presence signal, không tuyên bố attestation.
7. Spike ProtocolLib và PacketEvents riêng: load, movement listener, custom payload path, cleanup.
8. Test artifact trên Windows VM/Sandbox, Defender cập nhật; lưu output và SHA-256.
9. Chỉ sau gates pass mới chuyển trạng thái thành `APPROVED_FOR_IMPLEMENTATION`.

## Trạng thái quyết định

- Server plugin-only: `RECOMMENDED`, compatibility exact chưa reproduction.
- Fabric client mod: `RECOMMENDED`, exact build/API chưa reproduction.
- NeoForge: `REJECTED FOR MVP`, không có lợi ích đã chứng minh cho plugin-only.
- Velocity: `DEFERRED`, không cần cho topology hiện tại.
- Native Windows app: `DEFERRED/REJECTED FOR MVP`, tăng risk và chưa cần.
- ProtocolLib/PacketEvents: `UNKNOWN`, spike trước chọn.
- Auto-ban: `REJECTED FOR MVP`.
- Initial enforcement: `OBSERVE`, rồi mới cân nhắc `REQUIRE_VERIFIED` sau evidence.

## Action sau record này

Tạo compatibility spike disposable, không production. Không deploy, không restart production, không sửa LivingNPC.
