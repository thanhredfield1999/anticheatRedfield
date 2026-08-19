# Nghiên cứu — lựa chọn thay cho Fabric client verifier

Ngày: 2026-08-19

## Kết luận

Không có cơ chế Paper plugin nào chứng minh máy người chơi sạch cheat. Bất kỳ dữ liệu client tự gửi, gồm brand, danh sách mod, build ID hoặc response từ desktop app, đều có thể bị client tùy biến giả mạo. Server phải coi đây là signal policy, không phải bằng chứng cheat hay căn cứ auto-ban.[5]

Hướng hiệu quả nhất cho HeoMC là phòng thủ nhiều lớp:

1. Server-side packet simulation và behavior checks là lớp quyết định chính.
2. Verification gate là lớp kiểm soát quyền vào mode cạnh tranh, không phải phát hiện cheat độc lập.
3. Staff review, evidence bounded và appeal xử lý false positive.

Grim là bằng chứng thị trường cho hướng server-side simulation: mô phỏng movement phía server, lag compensation và không yêu cầu player cài client.[1]

## Các lựa chọn

| Lựa chọn | Player cài gì | Server biết gì | Chống cheat | Rủi ro / giới hạn | Khuyến nghị |
|---|---|---|---|---|---|
| Server-side packet simulation | Không | Packet và server state | Tốt với movement, timer, reach, velocity bất thường | Không đọc client mod/file; cần mô hình physics phức tạp | Bắt buộc, nền tảng chính |
| Brand/channel fingerprint | Không | Client brand và registered plugin channel | Chặn client/mod phổ biến cấu hình kém | Client tùy biến có thể giả brand/channel; không thay full anticheat.[5] | Signal observe, không auto-ban |
| Fabric/NeoForge companion mod | Mod đúng loader | Custom payload, protocol/build self-report | Gate modpack hoặc mode riêng | Bắt cài loader; vanilla không dùng được; response không chứng minh client sạch | Chỉ dùng nếu server chấp nhận modded-only |
| Desktop verifier + one-time code | App riêng | App online, version, pairing session | Gate access/onboarding | Có thể bị giả mạo/hook; tăng attack surface, vận hành backend và privacy burden | Có thể làm, nhưng chỉ verification gate |
| Custom launcher | Launcher riêng | Game launch qua launcher, build/modpack expected | Kiểm soát distribution, update, modpack | Player friction lớn; client/launcher giả vẫn khả thi | Hợp server modpack kín |
| Velocity/login gateway | Không bắt buộc | IP/account policy, rate limit, routing, session gate | Chống bot/abuse và tách admission | Không tự xác thực desktop app hay máy client | Bổ trợ hạ tầng |
| Resource pack prompt | Không | Client chấp nhận/tải pack | UX/onboarding, cosmetic assets | Không thực thi code client; không anti-cheat | Không dùng làm verifier |
| OS scan process/file/%APPDATA% | App quyền máy | File/process/path cục bộ | Không tạo trust root trước attacker có quyền sửa client | Privacy, security, false-positive và legal risk cao; không dùng | Loại khỏi scope |

## Phân tích kỹ thuật

### 1. Server-side packet simulation

Đây là lựa chọn không cần cài client và có giá trị phát hiện thực tế nhất. Server nhận movement/interaction packet, giữ replica state theo player, mô phỏng khả năng hợp lệ theo tick, latency, velocity, teleport, môi trường block và version protocol. Mismatch lặp lại tạo evidence; policy sau đó mới setback, kick hoặc đưa staff review.

Grim mô tả kiến trúc simulation, Netty threads, world replication và lag compensation; đây chỉ là bằng chứng hướng tiếp cận, không phải bằng chứng rằng code hiện tại của dự án đã đạt độ chính xác tương tự.[1]

Yêu cầu dự án:

- Packet adapter version-boundary; không để check phụ thuộc Bukkit event timing.
- Snapshot/outgoing-packet replica; không truy cập Bukkit world async trái contract.
- Buffer + decay + exemption context; không kick từ một sample.
- Evidence gồm check ID, sequence, latency context, server state tối thiểu; không log raw sensitive data.
- Controlled Paper tests trước enforcement.

### 2. Brand/channel fingerprint

Vanilla protocol có custom/plugin payload trong configuration và play states.[3] Plugin có thể ghi nhận brand/channel để phát hiện client khai báo bất thường hoặc áp whitelist policy. Điều này không chứng minh danh sách mod thật. Một plugin cùng loại nêu rõ client tùy biến có thể giả cả brand lẫn channel, và nó không thay thế movement/combat anticheat.[5]

Dùng đúng:

- `OBSERVE` trước.
- Alert admin với signal và confidence thấp.
- Chỉ enforce allowlist khi public policy nêu rõ client/mod được phép.
- Không auto-ban vì brand/channel.

### 3. Companion mod

Fabric có API custom payload client-server; docs yêu cầu server validate mọi payload.[2] Companion mod phù hợp nếu mục tiêu là kiểm tra protocol compatibility hoặc enforce modpack. Nó không tạo trusted attestation: hacker có thể sửa/bọc mod hoặc tự tạo response hợp lệ theo protocol public.

Lựa chọn này phù hợp mode event/modpack riêng, không phù hợp nếu HeoMC muốn vanilla join không cài thêm gì.

### 4. Desktop verifier pairing

Mô hình giống mô tả EFlame có thể vận hành mà server chỉ cài plugin:

1. Plugin tạo one-time code, bind UUID + current connection + expiry ngắn.
2. Player tải và mở desktop app từ URL công khai.
3. App hiện consent rõ, nhập code, gọi backend HTTPS.
4. Backend xác thực code one-use, phát signed short-lived verification token cho plugin.
5. Plugin giữ player tại gate hoặc kick sau deadline theo policy.

Đây là access-control/onboarding. Token chỉ chứng minh backend đã thấy một app trình bày đúng protocol cho session đó. Không chứng minh binary nguyên vẹn, process sạch, mod list thật, hoặc player không cheat. Không dùng kết quả này làm auto-ban.

Nếu làm, contract tối thiểu:

- Thu thập: UUID, server ID, session nonce, app version, timestamp, result code.
- Không thu thập: `%APPDATA%`, file/path Minecraft, process list, token tài khoản, credential, raw IP ngoài vận hành hạ tầng cần thiết.
- Code TTL 2–5 phút, one-use, rate limit, expiry/replay rejection.
- App source/release ký code, update có hash/signature, HTTPS, key rotation, revoke version.
- Privacy notice, retention limit, delete/unlink flow, staff audit, appeal.
- Default `OBSERVE`; enable `REQUIRE_VERIFIED` chỉ sau isolated runtime test và false-positive review.

### 5. Custom launcher

Launcher kiểm soát UX hơn desktop app vì có thể tải Minecraft, modpack và update từ manifest. Nó vẫn không phải anti-tamper tuyệt đối: attacker kiểm soát máy có thể thay launcher, patch game hoặc giả backend client. Chọn khi community chấp nhận modpack/launcher riêng; không chọn cho server public vanilla-first.

### 6. Proxy/login gateway

Velocity hoặc gateway riêng thêm rate limit, IP/account reputation, queue, maintenance gate và ban propagation. Nó không nhìn thấy máy player và không thể tự xác thực desktop app. Dùng như admission layer, không thay anti-cheat.

## Kiến trúc đề xuất phase kế tiếp

Chốt server-first, không phụ thuộc Fabric:

1. Giữ `HybridAnticheat` Paper plugin và PacketEvents boundary.
2. Bỏ client verifier spike khỏi release scope; giữ disposable research artifact, không deploy.
3. Xây core packet pipeline: state replica, invalid packet, movement baseline, latency/exemption, bounded evidence.
4. Thêm brand/channel fingerprint ở `OBSERVE` như metadata có confidence thấp.
5. Chỉ thiết kế desktop pairing sau khi user chốt: vanilla-first hay verified-only competitive mode.
6. Không xây file/process/path scanner.

## Trạng thái bằng chứng

- Verified: plugin current build/load từng pass isolated Paper theo checkpoint.
- Verified: Fabric docs có custom payload và yêu cầu validate server-side payload.[2]
- Verified: Vanilla protocol tài liệu liệt kê plugin messages ở configuration/play.[3]
- Verified: public AntiSpoof documentation thừa nhận brand/channel có thể bị custom client giả và không thay full anticheat.[5]
- Inferred: desktop verifier có thể gate session bằng backend token. Cần threat model, backend design và isolated end-to-end reproduction.
- Unknown: EFlame protocol, dữ liệu thu thập, trust model và hiệu quả. Chưa có tài liệu chính thức được kiểm chứng.

## Sources

[1] https://grim.ac
[2] https://docs.fabricmc.net/develop/networking
[3] https://minecraft.wiki/w/Java_Edition_protocol/Packets
[5] https://modrinth.com/plugin/antispoof
