# HybridAnticheat — Research và gap matrix

Ngày: 2026-08-20
Target: Paper `1.21.11-131`, Java `21`, PacketEvents `2.13.0`.

## Nguồn đã đối chiếu

- Paper Javadocs `1.21.11`: `PlayerVelocityEvent`, `PlayerTeleportEvent`, `BukkitScheduler`.
- PacketEvents Javadocs `2.13.0`: `PacketListenerAbstract`, `onPacketReceive`, `onPacketSend`.
- Minecraft Java protocol reference: movement packets, `Confirm Teleportation`, `Vehicle Move`.
- Local source, tests, runtime BotChecker reports, `docs/RESEARCH_REVIEW_2026-08-19_PACKET_MOVEMENT_GAPS.md`.

## Evidence hiện có

- Pure Java movement state/simulator: Verified by Gradle tests.
- PacketEvents adapter load: Verified controlled Paper.
- Teleport gate: Verified unit; teleport journey BotChecker pass với `evidence=0`, `movementSignals=0`.
- Normal movement: Verified controlled BotChecker journey.
- Velocity lifecycle: BotChecker journey chạy sạch về kết nối, nhưng verdict false-positive là `INCONCLUSIVE`; baseline đã có `evidence=8` trước effect.
- Enforcement: luôn `OBSERVE`; không kick/ban.

## Gap matrix

| Gap | Mức | Hiện trạng | Acceptance evidence |
|---|---:|---|---|
| Teleport confirm correlation | P0 | Có gate bounded, mismatch/expiry test | Runtime packet suppression + đúng confirm + recovery |
| Velocity/knockback context | P0 | Có `PlayerVelocityEvent` window; chưa vector/applied tick | Baseline sạch, velocity event, window bounded, không signal ngoài nguyên nhân |
| Vehicle physics | P1 | Có telemetry và suppress player sample; chưa validator | Controlled boat/minecart journey; không dùng walking threshold |
| World physics | P0 | Chưa có block/liquid/ladder/elytra/slime context | Snapshot bounded trên main thread; test từng context |
| Latency/TPS | P1 | Chưa đo | RTT/TPS bucket bounded; threshold không đổi tùy tiện |
| Packet rate/sequence | P1 | Chưa có validator riêng | Flood/out-of-order test không crash, evidence bounded |
| Evidence persistence | P1 | Bounded memory buffer | Schema, atomic write, corrupt/future-version/restart tests |
| Client verifier | P1 | Challenge contract có; runtime client proof chưa đủ | Fresh nonce, binding, expiry, replay rejection runtime |
| Enforcement | Release gate | OBSERVE only | Review + approval trước policy mutation |

## Quyết định scope

1. Code tiếp các contract pure-Java có acceptance rõ: arrival sequence/rate bounded, velocity context, evidence persistence.
2. Vehicle physics không tự suy đoán. Chỉ code sau khi có fixture xác định loại vehicle và physics source.
3. Không nâng enforcement khỏi `OBSERVE`.
4. Không claim production-ready từ unit test hoặc BotChecker journey đơn lẻ.
5. Không sửa LivingNPC; BotChecker chỉ là test client.

## Tài liệu chứng minh

Paper scheduler xác nhận sync task chạy trên main server thread; adapter hiện enqueue pending packet rồi xử lý trong repeating sync task. `PlayerVelocityEvent` mô tả vector gửi tới player, đơn vị meters per tick; event cancellable. Protocol xác nhận server teleport cần client confirm đúng ID và vehicle movement là packet riêng.

## Trạng thái

Verified: build, unit, controlled Paper load, teleport/normal movement journey.
Inconclusive: velocity false-positive proof, callback thread exact runtime instrumentation, vehicle physics.
Unknown: production false-positive rate, peak performance, full vanilla physics coverage.
