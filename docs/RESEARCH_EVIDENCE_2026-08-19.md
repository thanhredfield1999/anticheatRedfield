# Anticheat research evidence — 2026-08-19

## Quy tắc đọc

- `Verified`: nguồn chính thức hoặc reproduction đã chạy.
- `Inferred`: suy luận từ `Verified`, chưa reproduction.
- `Unknown`: chưa đủ bằng chứng.
- Web source không tự chứng minh compatibility runtime của project.

## Verified từ local

| Claim | Evidence | Status |
|---|---|---|
| Anticheat là project riêng | `docs/DECISIONS.md`, project path `E:\AI.WORK\heomc-anticheat-plugin` | Verified |
| Target server | User/project decision: Paper `1.21.11`, Java `21` | Verified as project target; runtime anticheat chưa test |
| Chưa có packet dependency/client module | Project hiện chỉ có docs | Verified |
| Chưa có implementation/runtime result | Project hiện chỉ có docs | Verified |

## Verified từ official sources

### Fabric

Source: `https://docs.fabricmc.net/develop/networking`

Fabric docs mô tả đăng ký và gửi/nhận `CustomPayload`, gồm `ClientPlayNetworking.send` và server receiver. Ví dụ thuộc networking sau khi client/server đã có play networking.

- Custom payload play networking: Verified.
- Fabric client mod có thể trả payload ở play phase: Verified từ docs.
- Fabric payload dùng được ở login phase: Unknown.
- Fabric mod có thể cung cấp trusted attestation: Unknown/không được docs chứng minh.

### NeoForge

Source: `https://neoforged.net/news/20.4networking-rework`

NeoForge mô tả custom payload cho play và configuration endpoints. Nguồn nói rõ: “It is impossible to register custom payloads that should be sent during the login phase of the connection.”

- NeoForge custom payload play/configuration: Verified.
- Custom payload đăng ký qua hệ thống này ở login phase: Không khả dụng theo source; Verified.
- NeoForge phù hợp cho login-phase verifier bằng custom payload: Không được chứng minh; design hiện tại không dùng claim này.
- NeoForge truyền mod list đầy đủ: Không được source chứng minh; source còn nói protocol hiện tại chưa hỗ trợ gửi mod list.

### Velocity/Paper

Source: `https://docs.papermc.io/velocity/server-compatibility`

Velocity docs xác nhận Paper tương thích, modern forwarding dùng được với Paper `1.13.2+`; Fabric có thể cần FabricProxy-Lite để forwarding player info.

- Velocity + Paper compatibility: Verified theo docs.
- Velocity modern forwarding tới Paper: Verified theo docs.
- Modern forwarding là client anti-cheat attestation: Unknown/không được docs chứng minh.
- Velocity tự tạo challenge custom ở login phase cho client verifier: Unknown.
- Fabric client mod chạy qua Velocity trong setup cụ thể: Chưa reproduction; docs chỉ mô tả compatibility/forwarding requirements.

### PacketEvents

Source: `https://github.com/retrooper/packetevents/releases`

Release listing hiện hiển thị PacketEvents `2.13.0` là latest và có release `2.11.0`. Search result/release announcement trước đó nêu `2.11.0` thêm hỗ trợ Minecraft `1.21.11`; release page hiện đã thay đổi theo các release mới hơn.

- PacketEvents có release history: Verified.
- PacketEvents `2.11.0` claim hỗ trợ `1.21.11`: Verified từ release announcement/page đã thu thập, nhưng chưa runtime test trong project.
- Latest `2.13.0` có compatibility exact `1.21.11`: Unknown; không suy ra từ “latest”.
- PacketEvents API phù hợp implementation này: Unknown; cần spike exact dependency.

### ProtocolLib

Source: `https://github.com/dmulloy2/ProtocolLib/releases`

ProtocolLib `5.4.0` release notes ghi support `1.21.4-1.21.8`. Dev build notes ghi commit/PR thêm và đánh dấu support `1.21.11`.

- ProtocolLib có dev-build claim support `1.21.11`: Verified từ release notes.
- ProtocolLib stable `5.4.0` support `1.21.11`: Không được release `5.4.0` chứng minh; Unknown.
- ProtocolLib phù hợp implementation production: Unknown; cần exact stable/dev build spike.

### Paper built-in protections

Source: `https://docs.papermc.io/paper/reference/global-configuration`

Paper docs mô tả các server configuration, gồm item obfuscation, packet-related protections ở tài liệu Paper và server configuration. Đây là server baseline, không phải proof cho hybrid verifier.

- Paper có built-in configuration liên quan security/anti-cheat surface: Verified.
- Built-in Paper config thay thế anticheat movement/combat: Unknown/không được chứng minh.

## Không được kết luận từ evidence hiện tại

- Client mod có thể chứng minh máy không có cheat.
- Fabric là platform đúng nhất.
- NeoForge là platform đúng nhất.
- Velocity là bắt buộc.
- Attestation có thể hoàn tất trước Paper login.
- PacketEvents tốt hơn ProtocolLib.
- Bất kỳ check nào đã giảm false positive trên server thật.
- Bất kỳ plugin/client nào đã tương thích runtime với exact server.

## Reproduction cần làm trước quyết định

1. Dựng disposable Paper `1.21.11` server, không production.
2. Dựng minimal plugin nhận `custom_payload`/plugin message ở exact state có thể truy cập bằng Paper API hoặc selected packet adapter.
3. Dựng minimal Fabric client mod target exact Minecraft `1.21.11`; ghi packet phase thực tế khi server gửi challenge và client trả response.
4. Ghi rõ response xảy ra ở login, configuration hay play phase; không suy luận từ tên API.
5. Lặp cùng test với NeoForge exact target nếu tiếp tục xem xét NeoForge.
6. Test Velocity + Paper chỉ để kiểm tra forwarding và connection state; tách kết quả forwarding khỏi attestation.
7. Spike PacketEvents và ProtocolLib độc lập: load, register listener, observe custom payload, observe movement packet, cleanup on disable.
8. Chỉ sau khi test pass mới cập nhật `DECISIONS.md` bằng decision có scope/version/hash/log.

## Compatibility reproduction update

- Paper `1.21.11-131` + Java `21.0.4` + PacketEvents `2.13.0` server artifact load/enable: `Verified`.
- PacketEvents adapter `2.13.0` compile: `Verified` after correcting invalid assumed constant `PacketType.Play.Server.LOGIN`.
- PacketEvents event listener registration: `Verified` by `SPIKE_PACKETEVENTS enabled version=2.13.0`.
- PacketEvents live packet callback: `Not verified`; no real client joined during disposable run.
- PacketEvents clean shutdown: `Not verified`; Paper saved dimensions but worker-pool termination remained pending and process was terminated.
- Fabric client spike exact target build: `Verified`; Minecraft `1.21.11`, Loader `0.19.3`, Fabric API `0.141.6+1.21.11`, Java `21`, Gradle `9.5.1`.
- Fabric client/server response round trip: `Not verified`; no real Minecraft client run.
- Fabric client artifact SHA-256: `7d476ba0135dbf3613ff76f51df243c4e8ce152e115177c778f8fb204b85ad01` for `heomc-verifier-spike-1.0.0.jar`; build metadata confirms Minecraft `1.21.11`, Loader `0.19.3`, Java `>=21`. Payload schema now carries `connectionId`, nonce, sequence and client build ID. Round trip remains `Not verified`.
- ProtocolLib server artifact `5.4.0` SHA-256: `d11a6cef959b052c5f11ca7820d55e4fd1204ce0d604a2c70a55a277075c5f29`.
- ProtocolLib `5.4.0` adapter compile: `Verified`.
- ProtocolLib exact Paper runtime: `Failed/Not suitable for current spike`; Paper remapper reported plugin-reloader incompatibility, disabled ProtocolLib, and disabled adapter after linkage failure. No production compatibility claim.
- PacketEvents server artifact `2.13.0+spigot` SHA-256: `6d9ece0d87ee727a79a20b7ffbd432021609c6f52bafcb654fc2d3e9b6f064c5`.

## Decision gate — research round 2026-08-19

### Verified

- Fabric official docs support registering custom payload types on play clientbound/serverbound paths; docs do not prove pre-login attestation.
- Paper official docs support registering outgoing/incoming plugin-message channels and sending raw bytes to a player after player connection exists.
- PacketEvents official docs show listener lifecycle/API and `2.13.0` compile/load evidence exists in this repo; live callback with a real client remains unverified.
- Current MVP has one raw UTF-8 pipe contract on both sides, but no real client/server round-trip test.

### Inferred

- Play-phase Paper plugin messaging is the lowest-risk compatibility path for Fabric client verifier.
- `REQUIRE_VERIFIED` must be a post-join quarantine policy until real login/configuration gateway evidence exists.
- PacketEvents should remain optional and unused in verifier MVP; adding packet hooks before callback/runtime tests increases failure surface.

### Unknown

- Whether this exact Paper `1.21.11` plugin-message path is decoded by the exact Fabric `1.21.11` client mod in a real session.
- PacketEvents callback thread/lifecycle behavior for the intended packet classes in this exact stack.
- Clean Paper shutdown and production-scale false-positive/rate behavior.

### Proposed decision for approval

1. Keep Fabric `1.21.11` as client platform.
2. Use play-phase challenge plus Paper quarantine; do not claim pre-login attestation.
3. Keep `OBSERVE` as default; no kick/ban.
4. Use one versioned raw wire contract for the compatibility spike, then replace delimiter parsing with shared strict codec before release.
5. Keep PacketEvents compile-only optional; do not register packet hooks until a separate callback spike passes.
6. Next implementation starts only after user approval: real client/server round-trip harness, strict codec tests, monotonic TTL, reload/lifecycle tests, then controlled Paper verification.

## Trạng thái

- Research source: complete for this round.
- Compatibility reproduction: partial; Paper and PacketEvents load pass, client round trip and ProtocolLib remain open.
- Stack decision: Fabric client and `OBSERVE` chốt; PacketEvents is current candidate, not final production decision. ProtocolLib comparison remains open.
- Anticheat MVP artifact `HybridAnticheat-0.1.0-mvp.jar` SHA-256: `7ac3af8644d9ad39f8eb3ae799639dc1fce6a9c6bd969c4962a91388aa810c42`.
- Anticheat MVP focused test + build: `Verified`; `./gradlew :anticheat-plugin:test :anticheat-plugin:build --console=plain` passed.
- Full disposable Gradle build: `Verified`; `./gradlew clean test build --console=plain` passed for root and all spike modules.
- Controlled Paper `1.21.11` + Java `21` + PacketEvents `2.13.0` + `HybridAnticheat`: `Verified` load/enable; log `enforcement=OBSERVE packet-hook=disabled packet-dependency=present` and `Done (22.193s)`.
- Wire contract review: Fabric payload codecs and Paper plugin-message adapter currently use identical raw UTF-8 pipe schema (`v1|...`); cross-runtime round trip remains `Not verified`.
- PacketEvents `2.13.0` is compile-only/optional boundary in MVP; no packet hook is registered. Packet callback remains `Not verified`.
- Real client response round trip: `Not verified`.
- Movement/combat heuristic runtime: `Not started`.
- Correctness review found session race, exact-TTL ambiguity, null response handling, misleading session count and unsafe config bounds; fixes applied and full build re-run pass.
- Correctness follow-up: `/hac reload` now preserves active verifier/evidence state; runtime limits require restart. Response path has 50 ms/player rate gate before deep parse. Quit removes matching session by `connectionId`. Decoder rejects malformed UTF-8.
- Approved implementation slice: strict `WireCodec`, canonical UUID/number/nonce/build validation, `MonotonicClock`, canonical round-trip tests. Fabric client artifact remains build-verified; real client/server round trip remains unverified.
- Independent correctness review: `Partial pass`. Core isolation, nonce/connection binding, bounded payload, replay handling and observe-only boundary verified; release blockers remain protocol integration, client round trip, PacketEvents callback, timeout/heartbeat, monotonic TTL, clean shutdown and production-scale false-positive behavior.
- Review remaining: real client round trip, packet callback with client, clean shutdown, movement/combat runtime, monotonic TTL clock and production-scale false-positive behavior remain `Unknown`.
- Production code: MVP slice exists; not release-approved.
- Production deploy/restart: not performed.
- Checkpoint: `docs/CHECKPOINT_2026-08-19.md` ghi trạng thái, decision, artifact hash, evidence và next steps.

## Sources

- https://docs.fabricmc.net/develop/networking
- https://neoforged.net/news/20.4networking-rework
- https://docs.papermc.io/velocity/server-compatibility
- https://github.com/retrooper/packetevents/releases
- https://github.com/dmulloy2/ProtocolLib/releases
- https://docs.papermc.io/paper/reference/global-configuration
- https://minecraft.wiki/w/Java_Edition_protocol/Packets
- https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7
- https://grim.ac

## Correction

`ANTICHEAT_DESIGN_SPEC_2026-08-19.md` trước đó được tạo khi context trỏ LivingNPC; file đã chuyển sang project riêng nhưng header cũ còn nhắc repo LivingNPC. Header đó là historical context, không phải project ownership. Cần sửa header trước khi dùng spec như artifact chính thức.

Không có quyết định stack nào được chốt từ spec đó.

## Model/RAG

- Architecture review: `cx/gpt-5.6-sol`.
- Implementation: chưa bắt đầu.
- Review: `cc/claude-sonnet-4-6` sau khi có artifact.
- Final gate: `cc/claude-opus-4-6`.
- Mọi claim tương lai phải gắn evidence level.

## Evidence hash policy

Chưa hash artifact/repository vì project chưa có source implementation và chưa có release artifact. Không được báo hash giả.

## Runtime policy

Không sửa/deploy/restart production. Controlled Paper test chỉ được thực hiện sau khi có project skeleton và user xác nhận môi trường test không phải production.

## Open decisions

- Client platform/loader: Unknown.
- Login gateway: Unknown.
- Packet adapter: Unknown.
- Enforcement: Unknown; mặc định an toàn là observe-only trong test.
- Privacy/allowed mods: Unknown.
- Project/plugin name: Unknown.

## Next artifact

`COMPATIBILITY_SPIKE_PLAN.md` sẽ mô tả reproduction commands/files sau khi xác nhận available build tooling và target Minecraft client artifacts; không viết implementation dựa trên assumption.
