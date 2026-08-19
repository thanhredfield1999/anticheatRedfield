# Runtime BotChecker AntiCheat — 2026-08-20

## Phạm vi

Controlled disposable Paper `1.21.11-131` tại `127.0.0.1:25575`. Không production. Plugin giữ `enforcement=OBSERVE`.

BotChecker chạy bằng Node.js/Mineflayer, scenario:

`E:\AI.WORK\botcheckerminecraft-botchecker\scenarios\hybrid-anticheat-movement-smoke.json`

Report:

`E:\AI.WORK\botcheckerminecraft-botchecker\reports-anticheat-goal-2\0f10d9ef-9be5-4fea-a591-9f6eb13320f0.json`

## RCA handshake

Lần đầu dùng username `HeoMC_AnticheatBot`, dài hơn giới hạn username Minecraft 16 ký tự. Paper ngắt ở `serverbound/minecraft:hello`. Đổi account disposable thành `HACBot01` và cấp OP trên test server. Handshake sau đó pass với negotiated version `1.21.11`, protocol `774`.

`enforce-secure-profile=false` chỉ áp dụng disposable server để cho phép offline BotChecker; không áp dụng production.

## RCA false-positive movement

Lần chạy đầu sau handshake ghi `movementSignals=7` trước teleport và `11` sau teleport. `vertical-allowance-per-tick=0.9` thấp hơn vận tốc rơi hợp lệ của client, nên normal falling bị flag. Đây không chứng minh teleport gate hỏng.

Fix:

- `vertical-allowance-per-tick` đổi từ `0.9` thành `4.0`.
- Thêm regression `MovementSimulatorTest#normalFallSpeedIsAccepted`.
- JAR runtime khớp build SHA-256:
  `13257af565fd14765713c82d57576a11125393e769a9a02e8de77fa537e49589`

## Runtime evidence — PASS

Run ID: `0f10d9ef-9be5-4fea-a591-9f6eb13320f0`

BotChecker summary: `9 passed, 0 failed, 0 skipped`.

Initial status:

```text
HybridAnticheat enforcement=OBSERVE sessions=1 evidence=0 movementPackets=1 movementSamples=0 movementSignals=0 flyingPackets=0 statusPackets=0 invalidPackets=0 teleportConfirms=1 vehiclePackets=0 teleportSuppressed=0 vehicleSuppressed=0
```

Sau `/tp @s ~ ~10 ~` và 5 giây movement observation:

```text
HybridAnticheat enforcement=OBSERVE sessions=1 evidence=0 movementPackets=24 movementSamples=20 movementSignals=0 flyingPackets=2 statusPackets=2 invalidPackets=0 teleportConfirms=2 vehiclePackets=0 teleportSuppressed=0 vehicleSuppressed=0
```

Kết luận slice teleport + normal movement: `PASS` controlled runtime. Evidence không có false-positive (`evidence=0`, `movementSignals=0`), không invalid packet, không suppression bất thường.

## Velocity runtime — INCONCLUSIVE

Scenario:

`E:\\AI.WORK\\botcheckerminecraft-botchecker\\scenarios\\hybrid-anticheat-velocity-smoke.json`

Run ID: `c534c050-e997-484d-b96a-eb235a832235`.

BotChecker join, `/effect give @s minecraft:levitation 1 5 true`, quan sát 5 giây và disconnect sạch. Tất cả 8 bước pass, nhưng status đầu đã có `evidence=8 movementSignals=8` trước khi effect chạy; status cuối vẫn `evidence=8 movementSignals=8`. Vì baseline không sạch, run chỉ chứng minh lifecycle/velocity journey không crash, chưa chứng minh velocity grace không false-positive. Verdict: `INCONCLUSIVE`.

## Chưa hoàn tất

- Chưa có velocity runtime verdict sạch do baseline spawn/fall đã có evidence.
- Vehicle slice không thuộc scope hiện tại; không mở lại.
- Chưa production verification.
- Chưa bật enforcement ngoài `OBSERVE`.

Evidence này không chứng minh production safety hay toàn bộ SMART Goal đã hoàn tất.
