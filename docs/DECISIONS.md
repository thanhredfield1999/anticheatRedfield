# Anticheat — Quyết định kỹ thuật

## 2026-08-19

### Đã chốt

- Anticheat là dự án plugin riêng.
- Không sửa hoặc nhúng code vào `living-npc-plugin`.
- Plugin target Paper `1.21.11`, Java `21`.
- Kiến trúc tách server plugin, packet adapter, verifier contract và client verifier.
- Server giữ quyền quyết định gameplay; client attestation không phải bằng chứng máy sạch cheat.
- Không deploy hoặc restart production trong phase phát triển.

### Chưa chốt

- Tên project/plugin chính thức.
- Có Velocity/login gateway hay chỉ Paper quarantine sau login.
- Danh sách client/mod hợp lệ và chính sách privacy.

### Đã chốt sau compatibility spike

- Client platform: Fabric client mod, Minecraft `1.21.11`, Loader `0.19.3`, Fabric API `0.141.6+1.21.11`.
- Client spike build: `Verified`; Gradle `9.5.1`, Java `21.0.4`.
- Client artifact hiện là disposable spike, không phải release.
- Enforcement mặc định: `OBSERVE`; verifier chỉ tạo `presence/protocol signal`.
- Không auto-kick/ban từ một verifier response hoặc một check đơn lẻ.
- Pre-login trusted attestation: chưa có bằng chứng; Paper plugin-message path chỉ chứng minh post-join play path.
- Protocol adapter: chọn tạm `PacketEvents 2.13.0` cho implementation spike. Compile + Paper `1.21.11` load/enable pass. Packet callback chưa test với client thật. ProtocolLib `5.4.0` compile pass nhưng controlled runtime spike bị Paper remapper/reloader incompatibility và adapter linkage failure. Đây chưa là production approval; cần review API/license và client packet test trước release.
- Không dùng native launcher, driver, injection, process/file scan hoặc credential scan ở MVP.

### Kết quả research bổ sung — chưa phải quyết định

- Fabric official networking docs xác nhận custom payload ở client/server networking, nhưng chưa chứng minh một Fabric mod play/configuration payload có thể hoàn tất challenge ở login phase mà không thêm mixin/launcher protocol.
- NeoForge có hệ thống `CustomPacketPayload` và modded network negotiation; đổi lại người chơi phải chạy NeoForge, làm giảm compatibility với client Fabric/vanilla.
- Velocity hỗ trợ modern forwarding tới Paper; forwarding xác thực proxy/backend, không tự xác thực client verifier. Plugin messaging thường cần connection/player đã tồn tại, nên không đủ để tuyên bố pre-login attestation.
- Muốn challenge thật sự trước khi vào backend cần kiểm tra login-phase packet support của client verifier và gateway bằng reproduction. Nếu không, dùng Paper quarantine sau login.
- PacketEvents release `2.11.0` công bố hỗ trợ Minecraft `1.21.11`; ProtocolLib release notes cũng công bố hỗ trợ `1.21.11`. Cả hai vẫn phải spike trên server exact version trước khi chọn dependency.
- Kết luận hiện tại: chưa đủ bằng chứng để chốt Fabric, NeoForge, launcher riêng, Velocity hoặc packet library.

### Cập nhật implementation MVP

- Tạo plugin `HybridAnticheat` riêng tại `anticheat-plugin/`.
- Core challenge verifier thuần Java: nonce, connection binding, expiry, sequence, duplicate/replay, bounded payload.
- Paper adapter: join/quit, plugin-message challenge/response, `/hac status`, `/hac reload`.
- Enforcement vẫn `OBSERVE`; chưa movement checks, kick hoặc ban.
- PacketEvents được giữ ở dependency boundary; chưa dùng packet heuristic trong MVP.
- Controlled Paper load `1.21.11` + Java `21` + PacketEvents `2.13.0`: `Verified` cho plugin enable.

### Phạm vi phase đầu

- Tạo protocol contract chống replay sau khi chốt login/play phase.
- Tạo pure-Java core cho session, nonce, attestation state và violation state.
- Chưa auto-ban.

### Bổ sung server-side simulation — 2026-08-19

- User chọn hướng server-first, không bắt player cài Fabric hoặc desktop verifier trong phase này.
- Thêm pure-Java movement baseline tại `MovementSample`, `MovementValidator`, `MovementValidationResult`.
- Baseline chỉ kiểm tra finite input, tick order và delta bounded; chưa wired vào Paper runtime, chưa mô phỏng physics/world, chưa có enforcement.
- Không dùng baseline để kết luận cheat hoặc auto-kick/ban.
- Controlled Paper load với artifact mới pass: `HybridAnticheat enabled enforcement=OBSERVE packet-hook=disabled packet-dependency=present`, `Done (10.912s)`.
- Focused `MovementValidatorTest` pass; root `clean test build` pass, `20 actionable tasks: 20 executed`.
- Risk còn lại: ngưỡng movement hiện là test-only proposal, chưa hợp lệ cho gameplay thực tế nếu thiếu version physics, latency, velocity, teleport và environment context.
- Review architecture độc lập xác nhận chưa nên wire PacketEvents runtime ở slice kế tiếp; cần khóa pure-Java contract trước: `MovementState`, `MovementSimulationInput`, `MovementSimulator`, exemption/context và tests deterministic.
- Correctness review phát hiện tick-gap overflow, coordinate delta overflow, pitch range và result invariant. Đã sửa `MovementSample`, `MovementValidationResult`, `MovementValidator`; thêm tests tick-gap/domain/pitch/invariant. Focused test và full build pass.
- Pure-Java slice tiếp theo đã triển khai: `MovementExemption`, `MovementSimulationInput`, `MovementSimulator`, `MovementViolationBuffer` và `MovementSimulatorTest`.
- Simulator chỉ tạo observe signal; exemption nhận `TELEPORT`, `RESPAWN`, `VELOCITY`, `KNOCKBACK`, `VEHICLE`, `WORLD_CHANGE`; chưa đọc Bukkit, chưa packet hook, chưa setback/kick/ban.
- Full `clean test build` pass, `20 actionable tasks: 20 executed`.
- PacketEvents controlled spike đã compile và Paper load/enable pass: `SPIKE_PACKETEVENTS enabled version=2.13.0 receive-movement=registered`, `Done (12.610s)`. Wrapper exact cho `PLAYER_POSITION` và `PLAYER_POSITION_AND_ROTATION` đã xác nhận qua `javap`/compile.
- Chưa có Minecraft client thật trong isolated run nên inbound movement callback chưa được runtime-verified; chưa wire vào `HybridAnticheat` production path. Không gọi slice này là physics simulation hoặc production anticheat.
- Review độc lập lần hai PASS 5 lỗi chính: elapsed-tick bound, coordinate-domain trước exemption, decay overflow, input/result invariants và baseline false-positive boundary.
- Đã harden thêm `MovementViolationBuffer.add` chống floating-point overflow và thêm regression test score saturation.
- Focused simulator/validator tests và root `clean test build` pass, `20 actionable tasks: 20 executed`.
- Chưa wire PacketEvents vào production path. Runtime callback mới chỉ verified registration/load, chưa verified inbound player packet.
- Residual risk: chưa mô phỏng acceleration, friction, collision, knockback, vehicle, elytra, liquid, piston/slime hoặc latency; chưa production-ready, chưa enforcement.
- Đã thêm `MovementState` per-session bounded state, reset lifecycle và không advance state khi sample/domain/tick invalid. `MovementStateTest` focused pass; root full build pass.
- State review độc lập phát hiện invalid/out-of-order sample vẫn đổi exemption; đã sửa để state chỉ mutate `previous/exemption` sau result hợp lệ, thêm regression test. Focused test từng fail do kỳ vọng reason sai rồi đã sửa; root `clean test build` pass.
- Đã wire PacketEvents movement adapter vào `HybridAnticheat` ở `OBSERVE`; adapter normalize `PLAYER_POSITION`/`PLAYER_POSITION_AND_ROTATION` thành immutable `MovementSample`, giữ bounded `MovementState` theo player, dùng server tick atomic từ main-thread task, cleanup join/quit/disable và evidence redacted. Không gọi Bukkit/world API từ packet callback.
- Đã thêm lifecycle reset cho `TELEPORT`, `RESPAWN`, `WORLD_CHANGE`, `VELOCITY`, `VEHICLE` trong Paper adapter; reset xóa previous sample và score không bị cộng từ server movement kế tiếp.
- Adapter vẫn là baseline delta pipeline. Không có physics/world snapshot đầy đủ; rotation-only/flying/vehicle/teleport/velocity chưa là check độc lập.
- Phân tích runtime phát hiện nhiều movement packet có thể đến trong cùng server tick; xử lý từng packet sẽ tạo `OUT_OF_ORDER` giả. Đã sửa `MovementPacketAdapter` thành bounded coalescing: packet callback chỉ giữ sample mới nhất/player, main-thread task xử lý tối đa một sample/player/tick.
- Main-thread task đồng thời increment server tick và gọi `processTick`; task được cancel khi disable.
- Đã thêm bounded telemetry không chứa tọa độ/player data: `movementPacketsCaptured`, `movementSamplesProcessed`, `movementSignalsEmitted`; `/hac status` hiển thị ba counter để phân biệt registered/captured/processed/signal.
- Controlled isolated Paper load sau telemetry pass: `PacketEvents movement observe hook registered`, `HybridAnticheat enabled enforcement=OBSERVE packet-hook=observe`, `Done (11.291s)`. Full `clean test build` pass, `20 actionable tasks: 20 executed`.
- Phân tích tiếp phát hiện `MovementViolationBuffer.decay()` đã tồn tại nhưng runtime chưa gọi mỗi tick; score có thể tích lũy vĩnh viễn. Đã thêm `MovementState.decayOneTick()` và gọi trước mỗi `processTick`; thêm regression test decay.
- Full `clean test build` sau decay wiring pass, `20 actionable tasks: 20 executed`.
- Phân tích coverage phát hiện adapter bỏ qua `PLAYER_ROTATION` và `PLAYER_FLYING`; đã xác nhận PacketEvents `2.13.0` constants/wrappers exact bằng `javap`. Adapter nay nhận rotation bằng previous position và flying bằng previous sample/on-ground coverage, coalesce cùng tick, không gọi world API.
- `PLAYER_FLYING` không được đưa vào delta validator: packet này không mang position; adapter chỉ tăng `flyingPacketsObserved` để đo coverage. `/hac status` hiển thị counter này. Không dùng on-ground để kết luận physics vì core chưa có environment/velocity context.
- Adapter malformed-input hardening: position phải finite; yaw phải finite; pitch phải finite và trong `[-90, 90]`. Invalid packet bị ghi evidence redacted, không vào pending/state. Thêm counter `invalidPacketsRejected` vào `/hac status`; full `clean test build` pass sau hardening.
- Phân tích lifecycle phát hiện reset exemption chỉ xóa previous sample nhưng giữ violation score cũ. Đã thêm `MovementViolationBuffer.reset()`, gọi từ `MovementState.reset()`, và regression test xác nhận teleport/velocity reset score về `0`.
- Phân tích config phát hiện giá trị movement YAML không hợp lệ có thể làm constructor adapter ném exception và chặn enable. Đã thêm fallback + warning cho allowance, coordinate, tick gap, score, increment, decay; giữ `OBSERVE` và fail-safe.
- Controlled isolated load sau config hardening pass: `PacketEvents movement observe hook registered`, `HybridAnticheat enabled enforcement=OBSERVE packet-hook=observe`, `Done (12.075s)`. Full `clean test build` pass, `20 actionable tasks: 20 executed`.
- Full `clean test build` sau hardening flying pass, `20 actionable tasks: 20 executed`.
- Controlled isolated load sau rotation coverage pass: `PacketEvents movement observe hook registered`, `HybridAnticheat enabled enforcement=OBSERVE packet-hook=observe`, `Done (13.710s)`. Full `clean test build` pass, `20 actionable tasks: 20 executed`.
- Controlled journey attempt bị chặn: cửa sổ Minecraft hiện tại là `Minecraft* Forge 1.21.11 - Multiplayer (3rd-party Server)`, không phải Fabric 1.21.11 HeoMC Verifier Spike. Không tương tác client hiện tại để tránh ảnh hưởng server/user session. Isolated Paper đã stop sạch; counter packet/sample vẫn chưa runtime-verified.
- Firecrawl + local source review phát hiện gaps load-bearing: teleport-confirmation gate, `MOVE_VEHICLE`/vehicle mode, on-ground normalized context, correction/velocity generation, latency/TPS compensation. Đã ghi đầy đủ tại `docs/RESEARCH_REVIEW_2026-08-19_PACKET_MOVEMENT_GAPS.md`.
- Đã sửa lifecycle event filtering: cancelled teleport/vehicle không reset movement state; velocity chỉ reset khi event không cancelled, vector finite và non-zero. Full `clean test build` + isolated Paper load pass.
- Exact PacketEvents `2.13.0` spike xác nhận `TELEPORT_CONFIRM`/`WrapperPlayClientTeleportConfirm.getTeleportId()`, `VEHICLE_MOVE`/`WrapperPlayClientVehicleMove`, và server `PLAYER_POSITION_AND_LOOK`/`WrapperPlayServerPlayerPositionAndLook.getTeleportId()`. Adapter đã thêm bounded teleport correlation gate: lưu server teleport ID, bỏ qua movement khi chờ confirm, chỉ ID khớp mới mở gate, mismatch ghi evidence redacted. Source review phát hiện gate ban đầu chưa chặn trong `capture()`/`captureRotation()`; đã sửa và thêm `teleportSuppressed` counter. `teleportConfirms`/`vehiclePackets` vẫn hiển thị qua `/hac status`.
- Đã thêm bounded vehicle mode: enter/exit event không cancelled bật/tắt mode; player position/rotation bị suppress khi active; thêm `vehicleSuppressed`; `VEHICLE_MOVE` không vào walking validator. Build pass.
- Phân tích lock xác nhận process Paper khác đang chạy: PID `35564`, `paper.jar -Xms512M -Xmx1G`, port `25575` LISTENING. Lần start mới thất bại vì chạy trùng server và lock `.paper-remapped`/`world/session.lock`, không phải lỗi vehicle code. Không dừng process đang chạy vì chưa có phê duyệt.
- Research PacketEvents/Paper phát hiện wrapper decode có thể ném `RuntimeException`; adapter đã bọc receive/send decode, tăng `invalidPacketsRejected`, ghi evidence redacted. `open()`/lifecycle reset xóa teleport gate để tránh suppress vô hạn sau reconnect/respawn/world-change. `onPacketSend()` bỏ qua event cancelled để không tạo gate không có confirm. Audit race phát hiện `reset()` velocity/vehicle có thể xóa gate trước confirm; đã tách `resetAndClearTeleport()` cho teleport/respawn/world-change, còn velocity/vehicle giữ gate. Stale send chỉ lưu gate khi UUID có active `MovementState`; packet sau quit không tạo orphan gate. Pure-Java `MovementSample` nay giữ `onGround` và `MovementPacketKind`; adapter truyền context từ packet wrappers, chưa dùng làm cheat proof. Status-only `PLAYER_FLYING` chỉ telemetry + `statusPackets`, không tạo sample giả. Firecrawl xác nhận `PlayerVelocityEvent` không phải velocity liên tục; cần vector/applied tick/expiry. Keep-alive RTT chỉ latency signal, chưa được dùng bù threshold. Paper issue còn ghi nhận `PlayerTeleportEvent` có thể đến sau `PlayerQuitEvent` khi player trong vehicle; `close()` đã dọn state trước và không tái mở state. Full build pass; runtime Paper sau thay đổi vẫn chưa verify do PID `35564` giữ isolated server.
- Review phát hiện `ChallengeVerifier.maxClientBuildLength` chưa được enforce; đã sửa `WireCodec`/`ChallengeVerifier` và thêm regression test giới hạn build ID. Focused test + full build pass.
