# Implementation note — movement contracts

## Đã làm

- Thêm `ArrivalSequenceGuard` pure-Java bounded contract.
- Reject duplicate, out-of-order và sequence budget vượt giới hạn.
- Reset session-safe.
- Sửa `MovementState`:
  - tick giảm trả `OUT_OF_ORDER` thay vì `INVALID_INPUT`;
  - khi velocity grace hết, sample hiện tại trở thành baseline mới;
  - không so delta sau grace với sample cuối trong grace.
- Thêm regression tests và chạy focused tests pass.

## Không wire giả

PacketEvents `PacketReceiveEvent` không cung cấp sequence protocol cho movement packet. Tạo sequence local trong adapter chỉ đo thứ tự callback, không chứng minh client packet order và dễ tạo false confidence. Guard giữ ở core contract tới khi có sequence source thật hoặc packet timestamp policy được chốt.

## Remaining gaps

World physics snapshot, latency/TPS compensation, vehicle physics validator, persistent evidence, và velocity runtime baseline sạch vẫn chưa hoàn tất. Enforcement giữ `OBSERVE`.

## Evidence

Focused command:

`./gradlew.bat :anticheat-plugin:test --tests vn.heomc.anticheat.core.MovementStateTest --tests vn.heomc.anticheat.core.ArrivalSequenceGuardTest --no-daemon --console=plain`

Result: `BUILD SUCCESSFUL`.

Không production deploy/restart.
