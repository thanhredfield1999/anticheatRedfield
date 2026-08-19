# Compatibility Spike Plan — Paper plugin + Fabric verifier

Ngày: `2026-08-19`
Trạng thái: `STARTED`
Mục tiêu: kiểm tra runtime exact trước khi viết Anticheat production.

## Scope

- Server: Paper `1.21.11`.
- Server extension: plugin Paper, không chuyển server thành Fabric/NeoForge.
- Client candidate: Fabric client-side Java mod cho Minecraft `1.21.11`.
- Packet adapters: PacketEvents và ProtocolLib chạy trong spike riêng; chưa chọn dependency.
- Enforcement: observe/quarantine trong test; không ban.
- Environment: disposable test server/client; không production.

## Evidence hiện tại

- Fabric docs xác nhận client/server custom payload API: `https://docs.fabricmc.net/develop/networking`.
- Fabric có hướng dẫn target `1.21.11`: `https://fabricmc.net/2025/12/05/12111.html`.
- NeoForge source nói custom payload login phase không đăng ký được: `https://neoforged.net/news/20.4networking-rework`.
- Paper plugin messaging docs: `https://docs.papermc.io/paper/dev/plugin-messaging`.
- PacketEvents releases: `https://github.com/retrooper/packetevents/releases`.
- ProtocolLib releases: `https://github.com/dmulloy2/ProtocolLib/releases`.

Các URL trên không thay thế reproduction exact.

## Machine preflight

Verified ngày bắt đầu:

- Java khả dụng: `25.0.1`.
- Gradle global: không có trong PATH.
- Python khả dụng: `3.14.5`.
- Git khả dụng: `2.55.0.windows.3`.

Blocker cần xử lý trước build:

- Cần JDK `21` exact hoặc toolchain resolver hợp lệ.
- Cần Gradle Wrapper trong project spike hoặc dùng wrapper từ template chính thức.
- Không dùng Java `25` để tuyên bố target Java `21` đã pass.

## Test matrix

### S1 — Paper plugin baseline

1. Build minimal Paper plugin target API `1.21.11`.
2. Load trên disposable Paper `1.21.11`.
3. Log plugin enable/disable và exact server version.
4. Không dùng `/reload`.

Expected evidence: startup/disable clean, exact version recorded.

### S2 — Payload phase

1. Server gửi challenge bằng API được support.
2. Client verifier nhận challenge.
3. Client trả response.
4. Ghi connection phase thực tế: configuration hoặc play.
5. Ghi timeout, duplicate, malformed payload.

Expected evidence: phase không suy ra từ tên API; log có timestamp/session ID, không có secret.

### S3 — Vanilla client

1. Kết nối client vanilla không có verifier.
2. Xác nhận server không nhận response verifier.
3. Test quarantine/kick policy trong disposable server.

Expected evidence: vanilla path xử lý bounded, không treo main thread.

### S4 — Fake verifier

1. Gửi payload đúng schema nhưng không chạy Fabric verifier.
2. Xác nhận server chỉ đánh dấu `presence/protocol signal`.
3. Không nâng signal thành trusted attestation.

Expected evidence: threat-model test ghi rõ bypass thành công là expected limitation.

### S5 — Replay/duplicate

1. Reuse response cũ ở session mới.
2. Reuse cùng response hai lần trong session.
3. Gửi response hết TTL.
4. Gửi sai connection/session binding.

Expected evidence: reject bounded, reason code rõ, không log payload secret.

### S6 — Packet adapter A: PacketEvents

1. Pin version đã chọn trong spike.
2. Load plugin dependency exact.
3. Register movement/custom-payload listener.
4. Record callback thread and lifecycle cleanup.
5. Disable server cleanly.

Expected evidence: no linkage error, listener cleanup, exact dependency/artifact hash.

### S7 — Packet adapter B: ProtocolLib

Lặp S6 độc lập với ProtocolLib. Không trộn hai adapter trong một test kết luận.

Expected evidence: same schema of results.

### S8 — Client artifact Windows

Chỉ chạy sau khi client JAR build được:

1. Build clean.
2. SHA-256 artifact.
3. Inspect JAR contents and permissions.
4. Run on disposable Windows VM/Sandbox.
5. Verify no admin prompt, no native child process, no external file/process scan.
6. Run updated Defender scan.
7. If any detection: preserve exact artifact hash and submit through official Microsoft Security Intelligence flow.

Không upload private data hoặc secret artifact.

## Acceptance gates

- G1: target Java `21` build passes.
- G2: Paper `1.21.11` plugin startup/disable passes.
- G3: Fabric `1.21.11` client startup and payload response passes.
- G4: actual phase recorded.
- G5: replay/duplicate/TTL rejection passes.
- G6: vanilla/fake client limitations recorded honestly.
- G7: PacketEvents and ProtocolLib results recorded separately.
- G8: Windows artifact scan result recorded per hash.

Only G1–G8 complete can update `DECISION_2026-08-19_PLUGIN_ONLY_CLIENT_VERIFIER.md` to `APPROVED_FOR_IMPLEMENTATION`.

## Current result

- Spike plan created: `Verified`.
- Paper `1.21.11-131` disposable startup: `Verified`.
- Paper API plugin load/enable: `Verified`.
- Plugin message probe observed in `POST_JOIN_PLAY_UNVERIFIED` path: `Verified` from probe log.
- Pre-login attestation through this Paper plugin-message path: `Rejected by evidence`; probe occurs after join.
- Probe artifact build with Gradle `9.3.0`: `Verified`.
- Probe artifact SHA-256: `595901d815f6981f3daaf1ba98c7e9038e6ebae81adb625cd9d0dd79180ea425`.
- Disposable Paper startup process ended by process termination because tracked stdin was unavailable: clean shutdown `Not verified`.
- First disposable startup logged Java `25.0.1`; exact Java `21` gate then re-run separately.
- Second disposable startup logged Java `21.0.4` and Paper `1.21.11-131`: `Verified` for startup/load.
- Second probe metadata resolved `0.0.1-spike`: `Verified`.
- Second run accepted `stop` and saved all dimensions, but process remained in worker-pool termination after the observation window and was terminated by process tool: clean shutdown `Not verified`.
- Fabric client artifact/round trip: `Not started`.
- Exact client/server runtime compatibility: `Unknown`.
- Windows Defender result: `Unknown`.
- Packet adapter choice: `Unknown`.
- Production code: not started.
- Production deploy/restart: not performed.

### Corrections before next run

- Make plugin resource version resolve to `0.0.1-spike`.
- Verify actual executable path and `java -version` in same shell immediately before Paper launch.
- Start disposable Paper with PTY/available stdin; send `stop` and verify `Stopping server`/`Saving worlds`.
- Do not reuse `living-npc-paper-test` data or process.

## Safety

- Không đọc secrets.
- Không sửa LivingNPC.
- Không deploy/restart production.
- Không tắt Defender/SmartScreen.
- Không hướng dẫn người chơi whitelist artifact chưa được kiểm chứng.
- Không báo pass nếu chỉ có docs hoặc compile pass.
