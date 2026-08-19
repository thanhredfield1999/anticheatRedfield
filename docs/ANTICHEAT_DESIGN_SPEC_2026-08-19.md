# Anticheat Hybrid Design Spec — Paper `1.21.11`

- Trạng thái: Design only
- Ngày: 2026-08-19
- Target: Paper `1.21.11` (`1.21.11-131` đã có runtime evidence trong repo)
- Java: `21`
- Project: `E:\AI.WORK\heomc-anticheat-plugin`
- LivingNPC: chỉ là reference read-only; không thuộc project này
- Phạm vi: plugin anticheat riêng + client verifier riêng
- Trạng thái: candidate design, không phải stack decision
- Không có implementation/runtime evidence trong tài liệu này

## 1. Tóm tắt quyết định kiến trúc

1. Anticheat không nhúng vào `LivingNPC`.
2. Server plugin anticheat là authority cho movement, combat, inventory, block interaction và policy enforcement.
3. Client verifier chỉ cung cấp signal, protocol capability và client telemetry. Client không được tự quyết định flag, setback, kick hoặc ban.
4. Client mặc định đề xuất Fabric client mod. Platform vẫn cần user chốt.
5. Server plugin tách domain core, Paper adapter, packet adapter, client contract và evidence store.
6. Client verification dùng challenge-response theo từng connection, nonce mới, connection binding, expiry và sequence. Response cũ phải bị từ chối.
7. Không gọi Bukkit/Paper world/entity API từ packet/Netty thread. Packet thread chỉ parse, cập nhật immutable/raw state bounded; check cần world state chạy trên main thread hoặc dùng snapshot hợp lệ.
8. MVP enforcement bắt đầu bằng `OBSERVE`. `REQUIRE_VERIFIED` rollout theo cohort. Không tự động ban từ một check hoặc một client report.
9. Packet dependency chưa chốt. Chọn ProtocolLib hoặc PacketEvents sau compatibility spike trên Paper `1.21.11`; không hardcode packet ID.
10. Evidence bounded, redactable, rate-limited, có schema version. Không lưu full packet stream lâu dài.

## 2. Bằng chứng local và ràng buộc

### 2.1 Repo hiện tại

`CURRENT_STATE.md` xác nhận:

- `Paper 1.21.11`, Java `21`.
- Citizens `2.0.42-SNAPSHOT`, build `4173` trong live evidence.
- WorldGuard `7.0.16` optional, mutation policy fail-closed khi yêu cầu nhưng dependency không có.
- `LivingNPC` dùng `onEnable`/`onDisable`, tick task, config migration, telemetry JSON và runtime stop coordinator.
- Unit test không chứng minh Paper event ordering, Citizens lifecycle, chunk lifecycle, restart persistence hoặc tick performance.

`build.gradle.kts` hiện chỉ có:

- `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- `net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT`
- `com.sk89q.worldguard:worldguard-bukkit:7.0.16`
- `de.bluecolored:bluemap-api:2.7.7`

Chưa có ProtocolLib, PacketEvents hoặc client module.

`src/main/resources/plugin.yml` hiện thuộc `LivingNPC`, có `depend: [Citizens]`, chưa có anticheat entry point. Anticheat không được thêm entry point vào file này.

`MovementService.java` phục vụ movement của Citizens NPC. Không tái sử dụng trực tiếp cho player anticheat. Nếu cần code chung, phải tạo contract mới, không làm player state phụ thuộc NPC state.

`NpcTelemetryCollector.java` có pattern bounded telemetry, immutable snapshot và async export. Chỉ tham khảo pipeline; schema NPC không phải bằng chứng player cheating.

### 2.2 Ràng buộc an toàn

- Không scan toàn world/chunk/entity không bounded trong tick.
- Không force-load chunk.
- Không blocking I/O, DB hoặc HTTP trong server thread.
- Async chỉ dùng immutable snapshot, I/O hoặc pure computation; kết quả Bukkit phải marshal về main thread.
- Lifecycle phải hủy task, listener, packet hook, worker và flush bounded evidence.
- Persistence phải versioned, migration-aware, reject future schema và giữ file corrupt/unsupported để điều tra.
- Không log credential, token, secret, process list, file list hoặc private client data.
- Không dùng `/reload`, PlugMan hoặc hot-loader. Restart server là release boundary.

## 3. Phạm vi và non-goals

### In scope

- Verification gate trước gameplay.
- Protocol handshake client verifier.
- Packet preprocessor.
- Server-authoritative movement baseline.
- Illegal packet/rate/sequence checks.
- Interaction consistency cơ bản.
- Evidence, review workflow, metrics và rollout policy.
- API boundary để `LivingNPC` hoặc plugin khác chỉ đọc risk state nếu sau này cần.

### Out of scope MVP

- Trusted attestation tuyệt đối.
- Kernel driver, process scan, file scan, credential collection.
- Client tự ban hoặc tự kick.
- Full combat simulation.
- Anti-bot, anti-alt, VPN/IP reputation.
- Economy-specific anticheat.
- Tích hợp sâu vào Citizens NPC.
- Hỗ trợ Bedrock/Geyser nếu chưa có contract riêng.
- Launcher riêng nếu user chưa chốt.

## 4. Deployment topology

### 4.1 Repository/plugin boundaries

Khuyến nghị tạo repository/plugin riêng, không đặt source anticheat dưới `src/main/java/vn/heomc/livingnpc`.

Logical modules:

```text
anticheat-core
anticheat-paper
anticheat-packets
anticheat-client-contract
evidence-store
client-verifier
```

`anticheat-core`, `anticheat-paper`, `anticheat-packets`, `anticheat-client-contract`, `evidence-store` có thể dùng một Gradle multi-project repository trong phase đầu. `client-verifier` nên là repository riêng để tách release, signing và platform mapping.

Server-side dependency direction:

```text
anticheat-paper
  -> anticheat-core
  -> anticheat-packets
  -> anticheat-client-contract
  -> evidence-store

anticheat-packets
  -> anticheat-core
  -> anticheat-client-contract

client-verifier
  -> anticheat-client-contract
```

`anticheat-core` không phụ thuộc Bukkit, Paper, Citizens, WorldGuard, ProtocolLib hoặc PacketEvents.

### 4.2 Plugin identity đề xuất

Tên plugin và package chưa được user chốt. Đề xuất:

- Plugin name: `HybridAnticheat`
- Main class: `vn.heomc.anticheat.HybridAnticheatPlugin`
- Config: `plugins/HybridAnticheat/config.yml`
- Evidence directory: `plugins/HybridAnticheat/evidence/`
- Persistent state: `plugins/HybridAnticheat/state/`
- Audit log: `plugins/HybridAnticheat/audit/`
- Command: `/hac`
- Admin permission: `hybridanticheat.admin`
- Review permission: `hybridanticheat.review`
- Bypass permission: `hybridanticheat.bypass`

Đây là proposal, không phải API đã tồn tại.

`plugin.yml` của plugin riêng phải khai báo `api-version: '1.21'`. Packet dependency phải dùng `softdepend` nếu plugin có optional adapter; server phải fail-closed hoặc disable chức năng packet khi adapter không tương thích, không load nhầm silent.

## 5. Module boundaries

### 5.1 `anticheat-core`

Pure Java domain:

- `PlayerSessionState`: immutable state snapshot theo connection/session.
- `MovementState`: vị trí, rotation, on-ground, vehicle, velocity context, tick/sequence.
- `EnvironmentFlags`: liquid, ladder, climbable, slime, honey, powder snow, elytra, vehicle, piston/slime, block support.
- `ExemptionState`: teleport, respawn, join, velocity, knockback, vehicle, dimension change, world change, plugin movement, server correction.
- `TickClock`: server tick và monotonic elapsed time abstraction.
- `ViolationBuffer`: score/decay/burst buffer, không kick một flag.
- `CheckResult`: `PASS`, `INFO`, `FLAG`, `REJECT_INPUT`, `ERROR`.
- `EvidenceEvent`: schema-bound, redactable, bounded.
- `PolicyDecision`: observe, warn, setback, kick, require verification, ban review.
- `CheckContext`: immutable input; không chứa Bukkit object.
- `CheckRegistry`: check ID, version, enabled policy, budget.

Core không được giữ `Player`, `World`, `Entity`, `Location` hoặc chunk reference.

### 5.2 `anticheat-paper`

Paper/Bukkit adapter và lifecycle:

- Plugin bootstrap và dependency readiness.
- Join/login/configuration lifecycle.
- `PlayerJoinEvent`, quit, respawn, teleport, world change, vehicle, velocity/knockback hooks.
- Permission và command `/hac`.
- Main-thread `ActionExecutor` cho setback, cancel, kick theo policy.
- `ExemptionManager` với TTL và reason.
- World/environment snapshot provider bounded.
- Scheduler/task ownership.
- Optional service API cho plugin khác.

Paper adapter chuyển event thành domain input. Không đặt heuristic trực tiếp trong listener.

### 5.3 `anticheat-packets`

Packet adapter SPI:

- `PacketSource` interface.
- Một implementation ProtocolLib hoặc PacketEvents.
- Packet normalization theo semantic event, không expose packet ID vào core.
- `PacketPreprocessor` cập nhật raw session state theo sequence.
- Validation malformed, NaN, Infinity, out-of-order, duplicate, impossible field range.
- Rate limiter theo connection/player/packet class.
- Adapter compatibility self-test khi enable.

Không hardcode packet ID. Dùng API packet wrapper của dependency đã pin version. Không gọi Bukkit world API trên Netty thread.

### 5.4 `anticheat-client-contract`

Protocol-only module:

- Protocol major/minor.
- Feature bits.
- Message type.
- Challenge/response schema.
- Canonical serialization.
- Failure codes.
- Timeout, max payload, max sequence.
- Client build ID và compatibility range.

Module này không ký secret vào source, không chứa Fabric/NeoForge API và không coi `clientBuildId` là proof.

### 5.5 `evidence-store`

- Bounded ring buffer per player và global quota.
- Redaction policy.
- Async writer từ immutable snapshots.
- Atomic replace khi ghi file.
- Schema version/migration/recovery.
- Retention và deletion policy.
- Hash chain tùy chọn cho audit integrity; hash không biến evidence thành sự thật tuyệt đối.

Không ghi full packet stream. Evidence record chỉ giữ context cần review.

### 5.6 `client-verifier`

Default assumption: Fabric client mod, Java Edition.

Trách nhiệm:

- Hiển thị consent/disclosure khi cần.
- Nhận challenge.
- Validate protocol server.
- Tạo response đúng canonical format.
- Gửi heartbeat/sequence.
- Report capability tối thiểu, không thu thập private machine inventory.
- Hiển thị trạng thái verified/failure.
- Từ chối server không hợp lệ nếu policy client yêu cầu.

Không cho client quyết định server enforcement. Artifact phải được ký và phát hành checksum/signature. Nếu không có backend attestation, server chỉ xác nhận protocol participant, không xác nhận binary thật.

## 6. Client verifier protocol

### 6.1 Channel và lifecycle

Dùng plugin channel/custom payload trong `Configuration` và `Play` state theo khả năng của Minecraft Java protocol. Channel names phải versioned, ví dụ proposal:

- `hac:hello`
- `hac:challenge`
- `hac:response`
- `hac:heartbeat`
- `hac:server_result`
- `hac:disconnect_reason`

Tên channel chỉ là proposal; cần chốt canonical namespace trước code.

Handshake flow:

1. Server tạo `connectionId` random và `nonce` cryptographically random mới cho mỗi connection.
2. Server gửi `CHALLENGE` sau phase phù hợp, trước gameplay acceptance.
3. Client trả `RESPONSE` gồm protocol version, client platform, client build ID, nonce binding, connection binding, sequence và proof.
4. Server kiểm tra payload size, canonical encoding, protocol range, nonce, connection ID, expiry, sequence, duplicate và state machine.
5. Server lưu trạng thái `UNVERIFIED`, `VERIFIED`, `FAILED`, `EXPIRED`, `CLOSED`.
6. Server gửi `SERVER_RESULT`.
7. Client heartbeat theo interval bounded; server tăng sequence expectation và timeout.
8. Disconnect/reconnect tạo session mới; không reuse nonce, sequence hoặc verified state.

### 6.2 Chống replay

Bắt buộc:

- Nonce server random mới mỗi connection.
- Nonce không reuse sau disconnect.
- `connectionId` random và gắn với server-side connection object.
- Expiry ngắn, ví dụ 10 giây cho response đầu tiên; giá trị cần benchmark/chốt.
- Sequence monotonic, bắt đầu từ giá trị protocol quy định.
- Reject duplicate response.
- Reject response gắn player/session cũ.
- Reject response sau state `CLOSED`.
- Reject payload vượt max size trước parse sâu.
- Rate limit challenge response và heartbeat.
- Session state reset khi reconnect, world transfer không reset connection nếu protocol policy cho phép nhưng phải có revalidation riêng.

TLS không thay thế nonce/sequence và không chứng minh client binary.

### 6.3 Proof model

Mức mặc định không có trusted attestation:

- `proof = canonical_mac_or_signature` chỉ có ý nghĩa nếu key model được thiết kế đúng.
- Không nhúng server secret dùng chung vào client mod.
- Không coi chữ ký bằng private key phân phối trong client là trusted nếu attacker có thể trích xuất key.
- `clientBuildId`, checksum hoặc self-report chỉ là metadata.

Nếu user cần trusted attestation, phải bổ sung backend licensing/attestation server, key rotation, revocation và threat model riêng. Không gọi protocol handshake hiện tại là attestation.

### 6.4 Failure codes đề xuất

- `UNSUPPORTED_PROTOCOL`
- `MALFORMED_PAYLOAD`
- `NONCE_MISMATCH`
- `CONNECTION_MISMATCH`
- `EXPIRED_CHALLENGE`
- `SEQUENCE_REPLAY`
- `DUPLICATE_RESPONSE`
- `HEARTBEAT_TIMEOUT`
- `PAYLOAD_TOO_LARGE`
- `CLIENT_DECLINED`
- `SERVER_POLICY_REJECTED`
- `ADAPTER_UNAVAILABLE`

Failure code không ghi secret và không tiết lộ chi tiết giúp bypass.

## 7. Verification policy

### 7.1 States

- `UNVERIFIED`: chưa hoàn tất handshake.
- `VERIFIED`: handshake hợp lệ trong session hiện tại.
- `DEGRADED`: heartbeat trễ hoặc adapter gặp lỗi tạm thời; chưa tự động ban.
- `FAILED`: response invalid hoặc timeout theo policy.
- `EXEMPT`: admin/test account, phải audit rõ.
- `CLOSED`: session kết thúc.

### 7.2 Modes

- `OBSERVE`: cho vào server, ghi metric/evidence, không enforcement tự động.
- `REQUIRE_VERIFIED`: không cho gameplay sau deadline nếu chưa verified; kick với hướng dẫn rõ ràng hoặc giữ ở gate theo UX đã chốt.
- `KICK`: chỉ áp dụng cho protocol violation chắc chắn hoặc policy đã review.
- `BAN_REVIEW`: tạo case cho admin; không auto-ban MVP.
- `BAN`: chỉ phase sau, cần threshold, appeal, audit và user approval.

Recommended rollout:

1. `OBSERVE` toàn server.
2. `OBSERVE` + `REQUIRE_VERIFIED` trên test group.
3. `REQUIRE_VERIFIED` cho cohort opt-in.
4. `REQUIRE_VERIFIED` toàn server sau controlled Paper test, replay test và false-positive review.

Client verifier failure không tự động chứng minh cheating. Trong `OBSERVE`, server chỉ ghi signal.

## 8. MVP checks

### 8.1 Verification gate

- Timeout bounded, không block main thread.
- Non-client/sai protocol bị giữ ở gate hoặc kick theo policy.
- Reconnect tạo challenge mới.
- Admin/test bypass có permission, TTL và audit.

### 8.2 Illegal packet

- NaN/Infinity coordinates/rotation.
- Coordinate delta vượt domain range trước khi check movement.
- Unexpected packet state/phase.
- Malformed payload hoặc size vượt giới hạn.
- Duplicate/out-of-order sequence.
- Excessive packet rate theo class và sliding window bounded.
- Unknown semantic event xử lý fail-closed ở adapter, không crash plugin.

### 8.3 Movement baseline

Checks chạy trên server state + snapshot:

- Finite coordinates.
- Pitch/yaw domain validation.
- Impossible delta theo elapsed ticks.
- Timer/speed consistency.
- Blink/position silence với timeout và lag compensation.
- Horizontal/vertical simulation baseline.
- Server velocity/knockback compensation.
- Teleport/respawn/world change exemption.
- Vehicle exemption hoặc model riêng.
- Elytra, liquid, ladder/climbable, powder snow, slime/honey, piston và plugin velocity context.
- Setback về vị trí server hợp lệ gần nhất, chỉ khi policy check cho phép.

Không dùng speed threshold one-shot. Mỗi check phải có buffer, decay, latency context, exemption context và evidence.

### 8.4 Interaction consistency

Phase MVP sau movement core:

- Reach/raycast bounded.
- Impossible digging/use timing.
- Inventory transaction consistency.
- Block interaction sequence.
- Cancel/setback chỉ sau server state xác nhận.

### 8.5 Combat

Deferred sau simulation:

- Rotation/attack sequence.
- Reach/line-of-sight.
- Attack cooldown/timing.
- Target validity.

Không dùng one-shot heuristic để kick/ban.

## 9. Check pipeline

Pipeline chuẩn:

```text
raw packet/event
  -> adapter normalization
  -> preprocessor
  -> session state update
  -> exemption resolver
  -> check scheduler
  -> ViolationBuffer
  -> EvidenceEvent
  -> PolicyDecision
  -> main-thread ActionExecutor
```

Quy tắc:

- Preprocessor cập nhật state trước check.
- Check không mutates Bukkit state.
- Check có `checkId`, `checkVersion`, budget và enable policy.
- Mỗi tick/player có work budget.
- Quá budget thì defer, không chạy vô hạn.
- Một exception trong check bị cô lập, rate-limited log và chuyển check sang degraded/disabled theo policy.
- `MONITOR` event không dùng cho mutation.

## 10. Lifecycle và threading

### Enable

1. Validate Java/Paper version và config schema.
2. Validate plugin identity và dependency adapter.
3. Khởi tạo core immutable config.
4. Khởi tạo bounded evidence store.
5. Đăng ký Paper listeners.
6. Khởi tạo packet adapter và chạy compatibility self-test.
7. Đăng ký commands/permissions.
8. Start scheduler sau khi mọi component ready.
9. Publish service API chỉ sau readiness.

Nếu packet adapter không tương thích:

- `OBSERVE`: plugin vẫn chạy event-only checks, log rõ packet checks unavailable.
- `REQUIRE_VERIFIED`: fail-closed, không tự cho qua verification nếu contract cần packet adapter.

### Join/quit

- Join tạo `PlayerSession` mới trên main thread.
- Packet callback chỉ lookup session theo immutable connection key.
- Quit đánh dấu `CLOSED`, ngăn callback cũ mutation, hủy TTL/task và enqueue bounded evidence.
- Reconnect không dùng state cũ.

### Async boundary

Main thread:

- Player/world/entity access.
- Teleport, velocity, kick, inventory/block mutation.
- Snapshot environment bounded.
- Session lifecycle transition gắn Bukkit.

Async:

- Serialize immutable evidence snapshot.
- File I/O.
- Pure simulation nếu input immutable và budget rõ.
- Optional remote attestation HTTP nếu sau này có, có timeout/circuit breaker.

Kết quả async phải kiểm tra generation/session còn hợp lệ trước khi apply. Không giữ stale `Player`, `World`, `Entity`, `Location` mutable reference.

### Disable

1. Stop accepting new sessions.
2. Mark global lifecycle generation stopped.
3. Unregister packet hooks/listeners.
4. Cancel scheduled tasks.
5. Stop worker sau khi bounded queue drain hoặc deadline.
6. Flush evidence bounded.
7. Persist policy/audit state.
8. Close resources.
9. Log incomplete flush và preserve queue nếu deadline hết.

Disable phải idempotent; cleanup component failure không được ngăn component sau chạy.

## 11. Persistence và evidence

### 11.1 Files

Đề xuất:

```text
plugins/HybridAnticheat/config.yml
plugins/HybridAnticheat/state/schema.yml
plugins/HybridAnticheat/state/sessions.yml
plugins/HybridAnticheat/state/policy.yml
plugins/HybridAnticheat/evidence/index.json
plugins/HybridAnticheat/evidence/YYYY-MM-DD/*.jsonl
plugins/HybridAnticheat/audit/YYYY-MM-DD.jsonl
```

Nếu chọn SQLite, dùng database riêng của plugin, không dùng `LivingNPC` files. Quyết định YAML/JSONL/SQLite cần user chốt theo scale.

### 11.2 Evidence record

Mỗi record tối thiểu:

- `schemaVersion`
- `eventId`
- `timestamp`
- `serverInstanceId` dạng pseudonymous
- `playerId` pseudonymous hoặc UUID theo privacy policy
- `connectionId` không reuse
- `sessionState`
- `checkId`
- `checkVersion`
- `decision`
- `serverTick`
- elapsed time/latency bucket
- exemption reasons
- movement summary đã redact
- violation score/buffer summary
- client verification state
- protocol failure code nếu có
- evidence integrity hash nếu bật

Không lưu:

- password/token/credential
- full client filesystem/process list
- raw private machine inventory
- full packet stream mặc định
- chat/private content nếu không cần check

### 11.3 Retention

Default proposal:

- `OBSERVE` low-severity: 24–72 giờ.
- Flagged case: 7–30 ngày.
- Ban review: giữ tới khi appeal/incident đóng, có owner.
- Delete request/retention expiry phải audit.

Tất cả quota phải bounded theo player, ngày và toàn server. Disk full không được làm treo main thread; policy chuyển sang `DEGRADED` và log rate-limited.

### 11.4 Integrity và review

Evidence là signal, không phải phán quyết. Ban review cần:

- Nhiều event độc lập.
- Check version/config snapshot.
- Latency/exemption context.
- Server restart/reconnect context.
- Replay/false-positive reproduction nếu có.
- Admin decision, actor, timestamp và reason.

## 12. API boundary với plugin khác

Anticheat không phụ thuộc `LivingNPC`. Nếu cần tích hợp sau này, expose Java-pure immutable API qua `ServicesManager`:

- `PlayerRiskSnapshot`
- `VerificationState`
- `ExemptionHandle`
- `EvidencePublisher`

Consumer chỉ đọc snapshot hoặc tạo exemption có owner, reason, TTL. Consumer không được gọi check internals, sửa violation score hoặc tự ghi evidence vào storage.

`LivingNPC` không được import anticheat implementation classes. Tích hợp chỉ xảy ra qua API riêng sau khi có use case và test lifecycle.

## 13. Configuration và policy contract

Mọi setting cần phân loại:

- reload-safe
- connection-bound, áp dụng connection mới
- restart-required

Proposal keys:

```yaml
mode: OBSERVE
verification:
  required: false
  response-timeout-ms: 10000
  heartbeat-timeout-ms: 5000
checks:
  illegal-packet: true
  movement-baseline: true
  interaction-consistency: false
  combat: false
evidence:
  enabled: true
  max-events-per-player: 256
  max-events-global: 10000
  retention-hours: 72
packet-adapter:
  provider: AUTO
```

Đây là shape proposal, chưa phải config implementation. Config migration phải reject future schema và không silently reset file.

## 14. Test matrix

| Lớp | Case | Kỳ vọng | Bằng chứng |
|---|---|---|---|
| Core unit | Nonce mismatch | Reject | Unit verified |
| Core unit | Expired challenge | Reject | Unit verified |
| Core unit | Duplicate/replayed sequence | Reject | Unit verified |
| Core unit | Wrong connection binding | Reject | Unit verified |
| Core unit | Malformed/oversized payload | Reject bounded | Unit verified |
| Core unit | NaN/Infinity movement | Reject/flag | Unit verified |
| Core unit | Tick wrap/elapsed timing | Không overflow | Unit verified |
| Core unit | Teleport/velocity exemptions | Không flag giả | Unit verified |
| Core unit | Violation buffer decay | Đúng threshold/window | Unit verified |
| Core unit | Evidence redaction/quota | Không vượt bound | Unit verified |
| Core unit | Schema migration/future schema | Reject, giữ file | Unit verified |
| Adapter | Packet semantic mapping | Đúng event contract | Controlled integration |
| Adapter | ProtocolLib/PacketEvents version | Self-test pass/fail rõ | Controlled Paper |
| Paper | Cold start | Plugin load đúng | Controlled Paper |
| Paper | Missing packet adapter | Policy fail-closed/degraded đúng | Controlled Paper |
| Paper | Join/quit/reconnect | Session reset, no stale callback | Controlled Paper |
| Paper | Configuration phase gate | Client hợp lệ qua gate | Controlled Paper |
| Paper | Non-client/invalid client | Gate/kick theo policy | Controlled Paper |
| Paper | Teleport/respawn/world change | Exemption đúng | Controlled Paper |
| Paper | Vehicle/elytra/liquid/powder snow | Không false flag | Controlled Paper |
| Paper | Knockback/piston/slime/plugin velocity | Context đúng | Controlled Paper |
| Paper | Chunk unload/world unavailable | Không force-load/crash | Controlled Paper |
| Paper | `/hac` permissions | Console/player/admin đúng | Controlled Paper |
| Persistence | Crash giữa temp write/replace | Recovery bounded | Failure injection |
| Persistence | Disk full/permission denied | Không block tick | Failure injection |
| Lifecycle | Disable trong handshake | Không callback mutation | Controlled Paper |
| Lifecycle | Disable trong async flush | Generation guard | Controlled Paper |
| Performance | 1/20/100/500 concurrent players | Tick budget p50/p95/p99 | spark/profile |
| Performance | Packet flood | Rate limiter bounded | Load test |
| Security | Replay old response | Reject | Protocol test |
| Security | Forged client build ID | Không coi verified binary | Threat test |
| Security | Duplicate connections | Session isolation | Protocol test |
| UX | Client missing/old version | Message rõ, không leak internals | Journey test |
| Rollout | OBSERVE metrics | Không enforcement ngoài policy | Controlled Paper |
| Rollout | Cohort REQUIRE_VERIFIED | Chỉ cohort bị gate | Controlled Paper |

Mọi runtime claim phải ghi rõ `unit verified`, `controlled Paper verified`, `production verified`. Không dùng unit test để claim Citizens/Paper/runtime/performance.

## 15. Acceptance criteria MVP

1. Server target `Paper 1.21.11`, Java `21` load plugin riêng không sửa `LivingNPC`.
2. Nonce mới theo connection; response replay bị từ chối.
3. Wrong connection, expired response, duplicate sequence bị từ chối.
4. Timeout không block main thread.
5. Session cũ không mutation sau quit/reconnect.
6. Malformed packet/rate flood không crash hoặc làm unbounded memory growth.
7. Teleport, knockback, vehicle, elytra, liquid, powder snow, piston/slime và plugin velocity có exemption/test context.
8. Movement checks dùng server-authoritative state, buffer và lag compensation.
9. Mỗi violation có `checkId`, `checkVersion`, tick, latency bucket, exemption context, score và redaction.
10. Evidence retention/quota bounded; disk failure không chặn tick.
11. `onDisable` hủy listener/task/packet hook, flush bounded và không còn mutation sau stop.
12. `OBSERVE` không kick/ban ngoài protocol policy đã chốt.
13. `REQUIRE_VERIFIED` chỉ bật sau cohort test, replay test và false-positive review.
14. Packet adapter compatibility được xác minh trên exact Paper `1.21.11`; không hardcode packet ID.

## 16. Rollout và rollback

### Phase 0 — Decision lock

Chốt client platform, plugin identity, packet library, storage, policy, privacy, attestation scope.

### Phase 1 — Protocol-only development

- Core handshake state machine.
- Replay/expiry/sequence tests.
- Client verifier Fabric prototype nếu assumption được duyệt.
- Không movement enforcement.

### Phase 2 — Local controlled server

- Exact Paper `1.21.11`.
- Packet adapter spike.
- Cold start, reconnect, config gate, disable/restart.
- Test client hợp lệ, client thiếu, client giả protocol.

### Phase 3 — `OBSERVE`

- Ghi bounded evidence.
- Theo dõi false positive, tick cost, packet flood.
- Không kick/ban vì movement score.

### Phase 4 — Cohort `REQUIRE_VERIFIED`

- Test accounts/cohort rõ.
- Có bypass admin audit.
- Có rollback config/plugin JAR.

### Phase 5 — Production gate

Chỉ deploy sau explicit approval, clean stop, backup JAR/data, exact artifact hash, controlled smoke pass và rollback plan. Không `/reload`.

Rollback:

- Tắt `REQUIRE_VERIFIED` bằng config đã được chứng minh reload-safe hoặc restart plugin/server theo contract.
- Nếu protocol/plugin lỗi, clean stop, restore exact prior JAR + `plugins/HybridAnticheat/` backup.
- Không xóa evidence trước khi incident review.
- Record artifact SHA-256, config hash, server build, packet adapter version.

## 17. Assumptions hiện tại

1. Chỉ hỗ trợ Minecraft Java Edition.
2. Default client platform là Fabric client mod.
3. Server operator chấp nhận client verifier trước gameplay.
4. Client verifier không phải trusted attestation.
5. Server có thể kiểm soát Paper plugin và restart server.
6. Target không phải Folia; nếu hỗ trợ Folia phải thiết kế scheduler/thread contract lại.
7. Paper `1.21.11` API và protocol adapter phải được kiểm tra trên exact server build.
8. Không cần tương thích Bedrock/Geyser trong MVP.
9. Evidence lưu local, chưa có cloud backend.
10. Auto-ban chưa bật.
11. `LivingNPC` tiếp tục độc lập.
12. User chấp nhận disclosure dữ liệu tối thiểu và retention policy rõ.

## 18. Decision cần user chốt

### Bắt buộc trước code

1. Client platform: Fabric, NeoForge, launcher riêng hay nhiều platform?
2. Có bắt buộc cài phần mềm ngoài Minecraft launcher không?
3. Có hỗ trợ Lunar/Badlion/OptiFine/Sodium và mod gameplay hợp lệ không?
4. Plugin identity/package: chấp nhận `HybridAnticheat` / `vn.heomc.anticheat` hay tên khác?
5. Protocol library: ProtocolLib hay PacketEvents? Cho phép spike hai lựa chọn trên Paper `1.21.11` không?
6. Verification mode mặc định: `OBSERVE` hay `REQUIRE_VERIFIED`?
7. Timeout/mất heartbeat: gate, kick hay cho degraded gameplay?
8. Enforcement: chỉ `KICK`, có `SETBACK`, có `BAN_REVIEW`, ai duyệt ban?
9. Storage: JSONL, SQLite hay backend khác?
10. Evidence retention và UUID/pseudonymous ID policy?
11. Có cần backend licensing/attestation thật không, hay chấp nhận protocol verification không trusted?
12. Có cần `LivingNPC` integration API ngay MVP không? Khuyến nghị không.

### Chốt sau spike

1. Exact channel names và canonical serialization.
2. Timeout/heartbeat/rate limits.
3. Max evidence quota và tick budget.
4. Setback behavior theo từng check.
5. Vehicle/elytra/liquid/environment model.
6. Packet adapter version pin và license/redistribution terms.
7. Fabric Minecraft version range và client release signing.
8. Geyser/Bedrock policy.

## 19. Nguồn và giới hạn bằng chứng

Research note chính: `docs/ANTICHEAT_RESEARCH_2026-08-19.md`.

Local references:

- `AGENTS.md`
- `CURRENT_STATE.md`
- `docs/RISK_REGISTER.md`
- `build.gradle.kts`
- `src/main/resources/plugin.yml`
- `README.md`
- `MovementService.java`
- `NpcTelemetryCollector.java`

Web references đã có trong research note:

- `https://docs.papermc.io/paper/reference/global-configuration`
- `https://docs.papermc.io/paper/dev/scheduler/`
- `https://jd.papermc.io/paper/1.21.11/`
- `https://www.protocollib.com`
- `https://github.com/dmulloy2/ProtocolLib/releases`
- `https://grim.ac`
- `https://hangar.papermc.io/GrimAnticheat/GrimAnticheat`
- `https://minecraft.wiki/w/Java_Edition_protocol/Packets`
- `https://forums.papermc.io/threads/anti-cheating-on-the-client-side.491/`

Research note ghi rõ Spigot thread yêu cầu login, không đủ nội dung làm technical proof; community guide/Grim chỉ là architecture input, không phải API/license proof. ProtocolLib hoặc PacketEvents compatibility phải xác minh riêng trước implementation.

## 20. Kết luận

Kiến trúc đúng cho dự án là `HybridAnticheat` plugin riêng, server-authoritative, client verifier Fabric mặc định theo assumption, protocol challenge-response chống replay và evidence bounded. `LivingNPC` giữ nguyên boundary. MVP nên ưu tiên verification gate, illegal packet, movement baseline và evidence; combat để phase sau. Không gọi client handshake là trusted attestation. Không bật auto-ban trước khi có controlled Paper evidence, false-positive review, persistence/restart test và rollback được phê duyệt.
