# Research review — Packet movement gaps

Ngày: 2026-08-19
Target: Paper `1.21.11`, Java 21, PacketEvents `2.13.0`
Phạm vi: đối chiếu local source với Paper Javadocs, PacketEvents Javadocs và Minecraft Java protocol reference.

## Kết luận

Movement adapter hiện đủ baseline position/rotation packet coverage và giữ `OBSERVE`, nhưng chưa đủ để gọi là movement validation production-ready.

Thiếu sót load-bearing:

1. Teleport-confirmation gate đã có bounded correlation, nhưng chưa có runtime client evidence.
2. `MOVE_VEHICLE` telemetry + player vehicle suppression đã có; chưa có vehicle physics validator.
3. Chưa lưu `onGround`/horizontal-collision từ movement packets.
4. Teleport ID state đã có; correction generation/velocity expiry context còn thiếu.
5. Chưa chứng minh PacketEvents callback thread bằng runtime evidence.
6. Velocity/teleport/vehicle cancellation filtering đã có; expiry/grace semantics còn thiếu.
7. Chưa có latency/TPS compensation và server physics context.

## Evidence ngoài repo

- Paper `BukkitScheduler` ghi sync task chạy trên main server thread; async task chạy thread scheduler. Điều này ủng hộ packet callback chỉ enqueue immutable data, còn Bukkit/state check chạy sync. [1]
- PacketEvents Javadocs hiện là artifact `packetevents-api v2.13.0`, nhưng landing page không tự chứng minh callback thread contract cho listener cụ thể. Cần giữ claim “thread behavior chưa runtime-verified” cho tới khi có exact API/source hoặc instrumentation. [2]
- Minecraft Java protocol liệt kê bốn serverbound movement packets: `move_player_pos`, `move_player_pos_rot`, `move_player_rot`, `move_player_status_only`; client vanilla gửi movement status packet định kỳ cả khi đứng yên. [3]
- Protocol reference mô tả server gửi player-position teleport; client phải gửi `Confirm Teleportation`, và server bỏ qua movement cũ cho tới khi confirm đúng ID. [3]
- Protocol reference mô tả vehicle movement là packet riêng `move_vehicle`; movement player packet không phải thay thế vehicle context. [3]
- Yaw không bị giới hạn `0..360`; finite yaw ngoài range đó hợp lệ. Pitch có miền `[-90, 90]`. [3]

## Đối chiếu local source

### Đúng

- `MovementPacketAdapter` nhận `PLAYER_POSITION`, `PLAYER_POSITION_AND_ROTATION`, `PLAYER_ROTATION`, `PLAYER_FLYING`.
- PacketEvents `2.13.0` exact API đã xác nhận `TELEPORT_CONFIRM` + `WrapperPlayClientTeleportConfirm.getTeleportId()`, `VEHICLE_MOVE` + `WrapperPlayClientVehicleMove` (position/yaw/pitch/on-ground), và server `PLAYER_POSITION_AND_LOOK` + `WrapperPlayServerPlayerPositionAndLook.getTeleportId()`.
- Adapter đã thêm correlation gate bounded: server send lưu teleport ID theo UUID; client movement bị bỏ qua khi chờ confirm; chỉ confirm đúng ID mới clear gate; mismatch ghi evidence redacted. Review source phát hiện gate ban đầu chưa được áp dụng trong `capture()`/`captureRotation()`; đã sửa và thêm counter `teleportSuppressed`.
- Đã thêm bounded vehicle mode: vehicle enter/exit event không cancelled bật/tắt mode; player position/rotation bị suppress khi active; thêm counter `vehicleSuppressed`. `VEHICLE_MOVE` chỉ telemetry, chưa vào walking validator.
- Runtime startup sau vehicle-mode change bị chặn bởi process ngoài scope: process Paper PID `35564` đang chạy `paper.jar -Xms512M -Xmx1G` và giữ port `25575`, `spike-server/world/session.lock`, `.paper-remapped/HybridAnticheat-0.1.0-mvp.jar`. Không xóa lock, không kill process. Build đã pass; Paper startup sau thay đổi chưa verified.
- Research thêm phát hiện PacketEvents wrapper decode có thể ném `RuntimeException`; adapter đã bọc receive/send decode bằng bounded catch, tăng `invalidPacketsRejected` và ghi evidence redacted. Lifecycle reset/open xóa teleport gate để tránh suppress vô hạn sau respawn/world-change/reconnect.
- `onPacketSend()` chỉ mở teleport gate khi send event không cancelled. Nếu packet teleport bị plugin khác cancel, adapter không chờ confirm không bao giờ đến.
- Audit race phát hiện `reset()` dùng cho velocity/vehicle có thể xóa teleport gate trong lúc confirm chưa đến; đã tách `resetAndClearTeleport()` và chỉ dùng cho teleport/respawn/world-change. Velocity/vehicle reset state nhưng giữ teleport gate.
- Stale send hardening: `onPacketSend()` chỉ lưu teleport ID khi UUID đang có active `MovementState`; packet sau quit trước reconnect không tạo gate orphan.
- Normalized context đã thêm vào pure-Java `MovementSample`: `onGround` và `MovementPacketKind`; PacketEvents adapter truyền dữ liệu từ position/rotation wrappers. Context chỉ telemetry/input normalization, chưa dùng riêng làm cheat proof.
- Firecrawl protocol review xác nhận `Set Player Movement Flags` (`PLAYER_FLYING`) là status-only packet có `onGround`, không nên tạo movement sample từ previous position. Adapter giữ telemetry riêng và thêm `statusPackets` counter; `MovementPacketKind.STATUS_ONLY` reserved cho normalization, chưa đưa vào simulator.
- Firecrawl Paper review xác nhận `PlayerMoveEvent` cancellation rollback không phải packet-layer evidence; adapter không dùng event đó làm physics proof. Vehicle movement có path riêng; player movement event không phủ mọi vehicle.
- Firecrawl review xác nhận `PlayerVelocityEvent` chỉ đại diện velocity được gửi cho player, không phải velocity liên tục của walking/flying; không được dùng event này làm thay thế physics snapshot. Local reset đúng hướng, nhưng chưa lưu vector/applied tick/expiry.
- Protocol review ghi nhận server movement checks có thể correction/rubber-band trong tình huống rơi xa hoặc vehicle; đây là evidence cho false-positive risk, không phải công thức threshold. Không dùng issue/forum để tự suy ra physics constants.
- Keep-alive RTT chỉ là latency signal; không đủ làm client movement tick hoặc physics proof. Cần đo bounded RTT/TPS riêng trước khi bù allowance.
- Verification command đầu tiên ghép `:anticheat-plugin:test` với `clean test build` gây Gradle task graph deadlock; chạy lại full command chuẩn riêng, pass.
- Paper issue evidence cho thấy `PlayerTeleportEvent` có thể xuất hiện sau `PlayerQuitEvent` khi player đang trong vehicle. `close()` dọn state trước; teleport handler chỉ reset state nếu còn state, nên không tái mở dữ liệu sau quit. Vẫn cần controlled Paper reproduction.
- Packet callback chỉ giữ pending sample bounded; `processTick()` chạy từ Bukkit sync repeating task.
- Core tách khỏi Bukkit/PacketEvents.
- Invalid finite/domain input bị reject, evidence redacted.
- `OBSERVE`, không setback/kick/ban.
- Lifecycle cleanup/reset đã có cho join, quit, teleport, respawn, world change, velocity, vehicle.

### Sai hoặc thiếu

#### 1. Teleport confirmation gate chưa có runtime proof

Local code đã theo dõi server teleport ID, client `Confirm Teleportation`, và bỏ qua movement trước confirm đúng ID. Chưa có client journey chứng minh packet callback, confirm đúng/sai, suppression và recovery.

Mức: P0 cho movement correctness.

Runtime proof còn cần:

- Observe client `CONFIRM_TELEPORTATION` wrapper exact bằng `javap`.
- Observe server teleport packet hoặc Paper teleport correlation.
- Per-session `awaitingTeleportConfirm` bounded state.
- Không đưa movement packet vào simulator khi gate đang mở.
- Clear gate chỉ khi ID khớp; stale/mismatched confirm chỉ tạo bounded evidence, không enforcement.

#### 2. Vehicle physics validator thiếu

Local code nhận telemetry `MOVE_VEHICLE` và suppress player position/rotation khi vehicle mode active. Chưa route `MOVE_VEHICLE` vào vehicle-specific simulation; chưa có vehicle physics/context.

Mức: P0 cho false positive vehicle.

Fix còn cần có:

- Observe `MOVE_VEHICLE` exact wrapper.
- Khi vehicle active: ignore player movement samples hoặc route sang vehicle-specific state.
- Reset on enter/exit sau event không cancelled.
- Không áp dụng player walking allowance cho vehicle.

#### 3. `onGround` bị bỏ qua

`WrapperPlayClientPlayerFlying` hiện chỉ tăng counter. Các packet movement đều mang trạng thái on-ground theo protocol, nhưng core không giữ field này. Đây không phải lỗi nếu chưa dùng physics; tuy nhiên không thể claim jump/fall/airborne reasoning.

Mức: P1 coverage gap.

Fix cần có:

- Thêm `onGround` và packet kind vào immutable normalized sample/context.
- Không dùng `onGround` riêng lẻ làm cheat proof.
- Kết hợp block/environment/velocity/vehicle/latency context trước signal.

#### 4. Server correction/velocity context thiếu

Local code reset state nhưng không lưu correction generation, velocity vector, applied tick hoặc expiry grace window. Trước hardening, `PlayerTeleportEvent`, `PlayerVelocityEvent`, `VehicleEnterEvent` và `VehicleExitEvent` cũng chưa được lọc theo cancellation/zero velocity. Đã sửa event filtering: teleport/vehicle bỏ qua event cancelled; velocity chỉ reset khi event không cancelled, vector finite và non-zero.

Mức: P1.

Fix còn cần có:

- Per-session context: correction generation, velocity vector, expiry tick, world ID, vehicle mode.
- Chỉ áp dụng velocity exemption khi event không cancelled và vector có magnitude hữu hạn > 0.
- Grace window bounded, không bypass malformed input.

#### 5. Tick mapping và coalescing semantics

Adapter giữ sample mới nhất/player/tick. Điều này tránh `OUT_OF_ORDER` giả do packet burst, nhưng làm mất packet count/order và có thể che sequence anomaly. Server tick không phải client tick; elapsed tick chỉ là server scheduler tick. Chưa có packet timestamp/arrival sequence hoặc TPS/latency estimate.

Mức: P1.

Fix cần có:

- Giữ `arrival sequence` bounded cho telemetry.
- Tách packet-rate/sequence check khỏi movement delta sample.
- Latency/TPS compensation trước khi đổi threshold.

## Không phải thiếu sót

- Fabric không bắt buộc cho server-side movement anticheat. Fabric client chỉ là optional verifier path.
- `PLAYER_FLYING` không nên tạo sample giả từ previous position. Chỉ count coverage là đúng khi chưa có normalized context.
- Yaw không cần clamp `0..360`; chỉ cần finite. Pitch clamp `[-90,90]` đúng.
- Unit test pass không chứng minh PacketEvents callback, Paper event order, vanilla physics hoặc production performance.

## Thứ tự sửa đề xuất

1. Teleport-confirmation gate.
2. Vehicle packet/mode gate.
3. Normalized movement context: packet kind, onGround, arrival sequence, correction/velocity generation.
4. Controlled vanilla/Forge/Fabric client journey với `/hac status` counters.
5. Latency/TPS measurement và physics context.
6. Independent correctness review.
7. Chỉ sau đó cân nhắc signal threshold; vẫn `OBSERVE`.

## MTVehicles source assessment

Nguồn: https://github.com/MTVehicles/MinetopiaVehicles, commit `9b4f9f49b4631db36e97b40ea59f388d3b23a8d0`.

- License: MIT, copyright `GamerJoep_`; có thể tham khảo/copy substantial portions nếu giữ copyright/license notice.
- Không lấy nguyên plugin/JAR làm dependency cho anticheat. MTVehicles target cũ/rộng: POM hiện dùng Paper API `1.16.5`, Spigot `1.12.2`, Java source/target `1.8`; không chứng minh tương thích Paper `1.21.11`/Java `21` của project này.
- Vehicle movement của MTVehicles không phải validator dùng được trực tiếp: nó cài Netty `ChannelDuplexHandler` per player, reflection vào NMS/CraftPlayer, version-specific field names, rồi đọc steering packet và tự điều khiển ArmorStand vehicle.
- Với 1.21.2+, MTVehicles schedule sync repeating task và giữ `lastPacket`; đây là implementation detail của vehicle plugin, không phải bằng chứng PacketEvents callback thread hay server physics contract.
- `VehicleMovement` dùng state/config riêng: speed, acceleration, braking, friction, max speed, block checks, slab/snow/ice/honey, vehicle type. Đây là dữ liệu hữu ích để thiết kế `VehiclePhysicsContext`, nhưng không được chuyển thành anticheat threshold nếu chưa map chính xác vehicle implementation/runtime.
- MTVehicles có vehicle identity qua ArmorStand/custom name/license và driver state. Có thể tham khảo identity/role model, không dùng custom-name lookup hoặc Bukkit entity access trong packet thread.
- MTVehicles code có version-specific reflection và raw NMS channel injection; không copy vào `HybridAnticheat`. PacketEvents adapter hiện tại an toàn hơn cho compatibility boundary.

Quyết định: **không lấy vehicle implementation trực tiếp**. Chỉ dùng repo làm reference để thiết kế contract mới:

1. `VehicleSession`: player UUID, vehicle UUID/type, driver/passenger role, generation.
2. `VehiclePhysicsContext`: max speed, acceleration, braking, friction, collision/environment snapshot, applied tick.
3. `VehicleMovementSample`: position, yaw/pitch, on-ground, packet arrival sequence, vehicle identity.
4. Vehicle-specific validator chạy observe-only sau controlled runtime journey.

## Sources

[1] https://github.com/MTVehicles/MinetopiaVehicles
[2] https://github.com/MTVehicles/MinetopiaVehicles/blob/master/LICENSE
[3] https://github.com/MTVehicles/MinetopiaVehicles/blob/master/pom.xml
[4] https://github.com/MTVehicles/MinetopiaVehicles/blob/master/src/main/java/nl/mtvehicles/core/movement/PacketHandler.java
[5] https://github.com/MTVehicles/MinetopiaVehicles/blob/master/src/main/java/nl/mtvehicles/core/movement/VehicleMovement.java
[6] https://minecraft.wiki/w/Java_Edition_protocol/Packets
[7] https://javadocs.packetevents.com/
[8] https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerVelocityEvent.html
[9] https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerMoveEvent.html
[10] https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html

## Audit bổ sung — stale session race

- Verified local defect: `onPacketSend()` trước đây kiểm tra `states.containsKey(UUID)` rồi mới ghi `awaitingTeleport`. `PlayerQuitEvent` có thể chạy giữa hai thao tác, tạo teleport gate orphan sau khi session đã đóng.
- Root cause: check và mutation không atomic theo session lifecycle.
- Fix: thêm `sessionLock`; `onPacketSend()`, `open()`, `close()`, `stop()` đồng bộ hóa check/mutation/cleanup. `closeUnlocked()` tránh lock lồng nhau.
- Evidence level: unit/build verified; PacketEvents callback ordering và Paper runtime race chưa controlled-runtime verified.

## Evidence classification

- Verified local: source paths, build, isolated Paper plugin load, PacketEvents registration.
- Verified external: protocol packet categories, teleport confirmation requirement, vehicle packet distinction, yaw/pitch semantics, Bukkit scheduler contract.
- Inferred: exact PacketEvents callback thread behavior for this listener; needs exact source/runtime instrumentation.
- Unknown: real client callback counters, false-positive rate, physics correctness, latency behavior, production performance.

Không production deploy/restart. Không enforcement.