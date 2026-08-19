# Checkpoint — 2026-08-19

## Trạng thái

Phase nghiên cứu + MVP implementation đã dừng an toàn. Không deploy production. Không sửa `E:\AI.WORK\living-npc-plugin`.

## Decision đã duyệt

- Client: Fabric mod, Minecraft `1.21.11`.
- Fabric Loader: `0.19.3`.
- Fabric API: `0.141.6+1.21.11`.
- Transport: play-phase challenge/response sau join.
- Pre-login trusted attestation: không claim.
- Policy: `OBSERVE`.
- Không auto-kick/ban.
- Paper quarantine chỉ là policy tương lai sau khi runtime round trip pass.
- PacketEvents `2.13.0`: compile-only/optional boundary.
- Chưa đăng ký packet hook thật.
- Không dùng ProtocolLib.
- Client không native launcher, driver, injection, process/file scan hoặc credential scan.

## Artifact hiện tại

Server plugin:

`E:\AI.WORK\heomc-anticheat-plugin\anticheat-plugin\build\libs\HybridAnticheat-0.1.0-mvp.jar`

SHA-256:

`7ac3af8644d9ad39f8eb3ae799639dc1fce6a9c6bd969c4962a91388aa810c42`

Fabric spike:

`E:\AI.WORK\heomc-anticheat-plugin\spikes\fabric-client-spike\build\libs\heomc-verifier-spike-1.0.0.jar`

SHA-256:

`7d476ba0135dbf3613ff76f51df243c4e8ce152e115177c778f8fb204b85ad01`

## Code đã có

- Pure-Java `ChallengeVerifier`.
- Nonce `SecureRandom`, 32 bytes, Base64 URL-safe.
- Connection binding.
- TTL expiry.
- Monotonic `System.nanoTime()` clock qua `MonotonicClock`.
- Sequence, duplicate/replay reject.
- Strict UTF-8 decoder.
- `WireCodec` canonical v1:
  - Challenge: `v1|connectionId|nonce`
  - Response: `v1|protocol|connectionId|nonce|sequence|clientBuildId`
  - canonical UUID/number/nonce/build validation.
- Bounded redacted evidence.
- 50 ms/player response rate gate.
- Reconnect-safe close theo `connectionId`.
- `/hac status`.
- `/hac reload` chỉ reload config, giữ session/evidence; runtime limits require restart.

## Verification đã pass

Command:

`./gradlew clean test build --console=plain`

Kết quả:

- `BUILD SUCCESSFUL`.
- 20 Gradle tasks pass.
- Focused core tests pass.
- Fabric client `clean build` pass.
- Controlled Paper `1.21.11-131` + Java `21.0.4` + PacketEvents `2.13.0` load/enable pass.
- Clean plugin set chỉ gồm `HybridAnticheat` + `PacketEvents`.
- Server ready log: `Done (16.956s)`.
- Hybrid log: `enforcement=OBSERVE packet-hook=disabled packet-dependency=present`.

## Chưa verified / blocker

- Real Minecraft client `1.21.11` chạy với Fabric mod.
- Paper plugin message → Fabric custom payload → client response → Paper verifier round trip.
- PacketEvents callback với client thật.
- Clean Paper shutdown; worker pool còn treo, process test bị kill.
- Heartbeat/timeout sau initial response.
- Movement/combat checks.
- False-positive benchmark.
- Production compatibility/scale.

## Files quan trọng

- `docs/DECISIONS.md`
- `docs/RESEARCH_EVIDENCE_2026-08-19.md`
- `docs/ANTICHEAT_DESIGN_SPEC_2026-08-19.md`
- `anticheat-plugin/src/main/java/vn/heomc/anticheat/core/WireCodec.java`
- `anticheat-plugin/src/main/java/vn/heomc/anticheat/core/ChallengeVerifier.java`
- `anticheat-plugin/src/main/java/vn/heomc/anticheat/core/MonotonicClock.java`
- `anticheat-plugin/src/main/java/vn/heomc/anticheat/paper/HybridAnticheatPlugin.java`
- `anticheat-plugin/src/main/java/vn/heomc/anticheat/paper/HacCommand.java`
- `anticheat-plugin/src/test/java/vn/heomc/anticheat/core/ChallengeVerifierTest.java`
- `spikes/fabric-client-spike/src/main/java/com/example/network/ChallengePayload.java`
- `spikes/fabric-client-spike/src/main/java/com/example/network/ResponsePayload.java`
- `spikes/fabric-client-spike/src/client/java/com/example/client/ExampleModClient.java`

## Cập nhật tiếp theo — server-first

- User chọn server-side anticheat; Fabric client verifier không còn là hướng bắt buộc.
- Đã triển khai pure-Java baseline/context/state: `MovementSample`, `MovementValidator`, `MovementExemption`, `MovementSimulationInput`, `MovementSimulator`, `MovementViolationBuffer`, `MovementState`.
- Đã harden tick-gap, coordinate domain, pitch, finite delta, exemption ordering, decay/score saturation và result invariants.
- Đã thêm PacketEvents `2.13.0` disposable inbound registration spike cho `PLAYER_POSITION` và `PLAYER_POSITION_AND_ROTATION`.
- Controlled Paper load pass: `SPIKE_PACKETEVENTS enabled version=2.13.0 receive-movement=registered`, `Done (12.610s)`.
- PacketEvents movement adapter đã wire vào `HybridAnticheat` ở `OBSERVE`; normalize hai packet position thành immutable `MovementSample`, bounded `MovementState` theo player, server tick atomic từ main-thread task, cleanup join/quit/disable. Chưa có Minecraft client thật nên inbound callback chưa runtime-verified; adapter chưa có physics/world snapshot/exemption context đầy đủ.
- Full gate gần nhất: `./gradlew.bat clean test build --no-daemon --console=plain` → `BUILD SUCCESSFUL`, `20 actionable tasks: 20 executed`.
- Đã thêm lifecycle reset cho `TELEPORT`, `RESPAWN`, `WORLD_CHANGE`, `VELOCITY`, `VEHICLE`; controlled Paper load sau wiring pass, vẫn `OBSERVE`.
- Đã thêm bounded coalescing: packet callback giữ sample mới nhất/player, main-thread task xử lý tối đa một sample/player/tick để tránh `OUT_OF_ORDER` giả khi client gửi nhiều packet/tick.
- Đã thêm bounded telemetry không chứa tọa độ/player data; `/hac status` hiển thị packet captured/sample processed/signal emitted.
- Controlled load sau telemetry: `Done (11.291s)`; full gate pass, chưa có client thật.
- Residual risk: chưa mô phỏng acceleration, friction, collision, knockback, vehicle, elytra, liquid, piston/slime hoặc latency.
- Chưa deploy production, chưa enforcement.

## Bước tiếp theo

1. Khi có client test thật, chạy callback evidence redacted trên isolated Paper `25575`.
2. Nếu callback pass, tạo adapter normalization chỉ ghi immutable `MovementSample`.
3. Wire core simulator vào `OBSERVE`, bounded evidence, per-player lifecycle.
4. Benchmark/false-positive test trước mọi enforcement.
5. Không deploy production nếu chưa có user approval.

## Resume sau cúp điện — 2026-08-19

### Slice đã hoàn tất

- Harden teleport-confirmation gate bằng `TeleportGate` pure-Java state.
- Gate lưu `teleportId` + `expiresAtTick`; timeout mặc định `40` server ticks từ `movement.teleport-confirm-timeout-ticks`.
- Movement/rotation chỉ suppress khi gate còn active; gate hết hạn tự clear trong `processTick()` hoặc lúc packet đến.
- Confirm đúng ID chỉ clear khi còn trong TTL; mismatch còn hạn ghi bounded evidence; confirm đến sau expiry không mở lại gate.
- Dùng saturated tick addition, tránh overflow `long`.
- Regression test: `TeleportGateTest.expiresAtBoundedTick`.

### Verification

- Focused: `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat :anticheat-plugin:test --no-daemon --console=plain` → `BUILD SUCCESSFUL`.
- Full: `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat clean test build --no-daemon --console=plain` → `BUILD SUCCESSFUL`, `20 actionable tasks: 20 executed`.
- Artifact SHA-256 mới: `507829e77a905a44587e013563d0ebc815a6eef9dbe4e1e5982e5d3b1f023466`.
- `git diff --check`: pass khi repo Git khả dụng; workspace hiện không có `.git` nên không có commit checkpoint.
- Runtime PacketEvents/client thật: chưa verified. Enforcement vẫn `OBSERVE`; không deploy/restart.

### Remaining blocker

- Chưa có client Minecraft `1.21.11` Fabric profile + verifier mod local.
- Chưa chứng minh teleport packet → `TELEPORT_CONFIRM` → suppression/recovery bằng controlled client journey.
- Chưa thêm vehicle physics validator, latency/TPS compensation hoặc collision snapshot.

## Resume tiếp — bounded server-caused movement window

- User clarified: `vehicle` trong Minecraft là boat/minecart/rideable entity, nhưng chưa phải ưu tiên anticheat hiện tại. Không thêm `VehicleSession` hoặc vehicle physics validator.
- Đã xóa test vehicle dở sau compile failure; không còn source vehicle contract mới.
- Thêm `MovementExemptionWindow` pure-Java: window có `reason`, `startsAtTick`, `expiresAtTick`; duration bounded, `MovementExemption.NONE` bị reject, tick expiry chống overflow.
- Thêm `MovementExemptionWindowTest`: start boundary, expiry boundary, overflow saturation.
- Slice hiện mới là contract/test; chưa wire window vào `MovementState` hoặc PacketEvents event adapter. Không claim velocity/knockback grace runtime.

### Verification

- RED: test ban đầu fail compile vì thiếu `MovementExemptionWindow`; sau khi thêm implementation, test fail một lần do kỳ vọng duration boundary sai; sửa test về semantics `duration=3` gồm ticks `10..12`.
- Focused: `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat :anticheat-plugin:test --tests vn.heomc.anticheat.core.MovementExemptionWindowTest --no-daemon --console=plain` → `BUILD SUCCESSFUL`.
- Full: `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat clean test build --no-daemon --console=plain` → `BUILD SUCCESSFUL`, `20 actionable tasks: 20 executed`.
- Runtime Paper/client: chưa verified. `OBSERVE`, không deploy/restart.

## Resume tiếp — wire velocity grace window

- Wire `MovementExemptionWindow` vào `MovementState`.
- `reset(reason, startsAtTick, durationTicks)` tạo window bounded và reset baseline/score.
- `MovementState.accept()` dùng exemption chỉ khi `current.serverTick()` còn trong window; hết hạn trả về validator bình thường.
- `MovementPacketAdapter.resetWithWindow()` áp dụng grace theo config.
- `PlayerVelocityEvent` hợp lệ, non-zero, không cancelled dùng `velocity-grace-ticks` mặc định `3`.
- Teleport/respawn/world-change vẫn dùng reset vô hạn đến sample kế tiếp; không đổi semantics.
- Chưa thêm knockback window vì project chưa có event adapter riêng cho knockback.

### Verification

- Focused `MovementStateTest` + `MovementExemptionWindowTest`: pass.
- Full `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat clean test build --no-daemon --console=plain`: `BUILD SUCCESSFUL`, `20 actionable tasks: 20 executed`.
- Artifact SHA-256: `fb06e612944868cb7803f771fea9e75b6f1f4ab79bd482aaaff2d5978f332ce1`.
- Runtime Paper/client: chưa verified. `OBSERVE`, không deploy/restart.

## Controlled runtime attempt — 2026-08-19

### Verified

- Disposable Paper `1.21.11-131`, Java `21`, `spike-server`, port `25575` started successfully.
- Plugin set: `HybridAnticheat-0.1.0-mvp.jar` + PacketEvents `2.13.0`.
- Runtime log: `Done (13.225s)!`; `PacketEvents movement observe hook registered; enforcement=OBSERVE`; no plugin fatal error.
- Server accepted `stop`; log recorded `Stopping server` and `Saving worlds`; this isolated shutdown completed without manual kill.
- Fabric profile file exists: `Fabric 1.21.11 HeoMC Verifier Spike`.
- Verifier JAR copied only to isolated directory `C:/Users/thanh/AppData/Roaming/.minecraft/hermes-anticheat-spike/mods/`; source and copy SHA-256 both `7d476ba0135dbf3613ff76f51df243c4e8ce152e115177c778f8fb204b85ad01`.

### Blocked / not verified

- Client launch failed before Minecraft startup: `bash: C:/XboxGames/Minecraft Launcher/Content/Minecraft.exe: Permission denied`.
- No client process appeared, no connection to `25575`, no `Verifier response accepted`, no custom-payload round trip.
- Isolated mods directory is not the launcher's configured game directory; do not claim profile loaded.
- Movement PacketEvents callback, teleport-confirm suppression/recovery, and velocity grace remain unverified.
- No production deploy/restart. `OBSERVE` unchanged.

## Quy tắc tiếp tục

- Đọc checkpoint này trước khi làm tiếp.
- Đọc lại source hiện tại, không tin hash cũ nếu artifact đã rebuild.
- Không dùng `living-npc-paper-test` cho test mới nếu có thể dùng disposable server.
- Không gọi MVP production-ready.
- Giữ phân loại `Verified` / `Inferred` / `Unknown`.
- Không restart/deploy production.

## Resume verification — 2026-08-19

### Verified

- Root Gradle gate đã chạy lại với Java `21`:
  `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat clean test build --no-daemon --console=plain`
  → `BUILD SUCCESSFUL`, `20 actionable tasks: 20 executed`.
- Fabric spike gate đã chạy lại với Java `21`:
  `JAVA_HOME='C:/Program Files/Java/jdk-21' PATH="$JAVA_HOME/bin:$PATH" ./gradlew.bat clean build --no-daemon --console=plain`
  → `BUILD SUCCESSFUL`, `10 actionable tasks: 10 executed`.
- Artifact SHA-256 không đổi so với checkpoint ban đầu:
  - server: `7ac3af8644d9ad39f8eb3ae799639dc1fce6a9c6bd969c4962a91388aa810c42`
  - Fabric spike: `7d476ba0135dbf3613ff76f51df243c4e8ce152e115177c778f8fb204b85ad01`
- Minecraft Launcher có trên máy tại `C:/XboxGames/Minecraft Launcher/Content/Minecraft.exe`.

### Unknown / blocker hiện tại

- Không có profile Fabric `1.21.11` local. Profiles Fabric hiện có: `1.21.1`, `1.21.4`.
- Có profile `ForgeOptiFine 1.21.11` và `OptiFine 1.21.11`, không phù hợp Fabric verifier spike.
- Không có mod Fabric/verifier trong `C:/Users/thanh/AppData/Roaming/.minecraft/mods`.
- Vì vậy chưa thể chạy client thật hay claim custom-payload round trip.
- Workspace hiện không có `.git`; không thể báo branch, diff hoặc commit state.
