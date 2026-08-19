package vn.heomc.anticheat.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.Plugin;
import vn.heomc.anticheat.core.BoundedEvidenceBuffer;
import vn.heomc.anticheat.core.EvidenceRecord;
import vn.heomc.anticheat.core.MovementExemption;
import vn.heomc.anticheat.core.MovementSample;
import vn.heomc.anticheat.core.MovementPacketKind;
import vn.heomc.anticheat.core.MovementSimulator;
import vn.heomc.anticheat.core.MovementState;
import vn.heomc.anticheat.core.MovementValidationResult;
import vn.heomc.anticheat.core.MovementViolationBuffer;

/** Packet-to-core boundary. Packet thread chỉ giữ raw sample mới nhất, không gọi Bukkit/world API. */
final class MovementPacketAdapter {
    private record Pending(double x, double y, double z, float yaw, float pitch,
                           boolean onGround, MovementPacketKind packetKind) { }

    private final Plugin plugin;
    private final BoundedEvidenceBuffer evidence;
    private final AtomicLong serverTick;
    private final ConcurrentMap<UUID, MovementState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TeleportGate> awaitingTeleport = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> vehiclePlayers = new ConcurrentHashMap<>();
    private final Object sessionLock = new Object();
    private final double horizontalAllowance;
    private final double verticalAllowance;
    private final double maxCoordinate;
    private final long maxElapsedTicks;
    private final long teleportConfirmTimeoutTicks;
    private final long velocityGraceTicks;
    private final double maxViolationScore;
    private final double violationIncrement;
    private final double violationDecay;
    private PacketListenerAbstract listener;
    private long generation;
    private final AtomicLong packetsCaptured = new AtomicLong();
    private final AtomicLong samplesProcessed = new AtomicLong();
    private final AtomicLong signalsEmitted = new AtomicLong();
    private final AtomicLong flyingPacketsObserved = new AtomicLong();
    private final AtomicLong invalidPacketsRejected = new AtomicLong();
    private final AtomicLong teleportConfirmsObserved = new AtomicLong();
    private final AtomicLong vehiclePacketsObserved = new AtomicLong();
    private final AtomicLong movementSuppressedByTeleport = new AtomicLong();
    private final AtomicLong movementSuppressedByVehicle = new AtomicLong();
    private final AtomicLong movementStatusPacketsObserved = new AtomicLong();

    MovementPacketAdapter(Plugin plugin, BoundedEvidenceBuffer evidence, AtomicLong serverTick,
                          double horizontalAllowance, double verticalAllowance,
                          double maxCoordinate, long maxElapsedTicks,
                          long teleportConfirmTimeoutTicks,
                          long velocityGraceTicks,
                          double maxViolationScore, double violationIncrement,
                          double violationDecay) {
        this.plugin = plugin;
        this.evidence = evidence;
        this.serverTick = serverTick;
        this.horizontalAllowance = positiveFinite(horizontalAllowance, "horizontalAllowance");
        this.verticalAllowance = positiveFinite(verticalAllowance, "verticalAllowance");
        this.maxCoordinate = positiveFinite(maxCoordinate, "maxCoordinate");
        if (maxElapsedTicks < 1) throw new IllegalArgumentException("maxElapsedTicks must be positive");
        this.maxElapsedTicks = maxElapsedTicks;
        if (teleportConfirmTimeoutTicks < 1) throw new IllegalArgumentException("teleportConfirmTimeoutTicks must be positive");
        this.teleportConfirmTimeoutTicks = teleportConfirmTimeoutTicks;
        if (velocityGraceTicks < 1) throw new IllegalArgumentException("velocityGraceTicks must be positive");
        this.velocityGraceTicks = velocityGraceTicks;
        this.maxViolationScore = positiveFinite(maxViolationScore, "maxViolationScore");
        this.violationIncrement = positiveFinite(violationIncrement, "violationIncrement");
        if (!Double.isFinite(violationDecay) || violationDecay < 0) {
            throw new IllegalArgumentException("violationDecay must be finite and non-negative");
        }
        this.violationDecay = violationDecay;
    }

    void start() {
        if (listener != null) return;
        listener = new PacketListenerAbstract() {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                try {
                    if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
                        WrapperPlayClientPlayerPosition packet = new WrapperPlayClientPlayerPosition(event);
                        capture(event.getUser(), packet.getPosition(), null, null,
                                packet.isOnGround(), MovementPacketKind.POSITION);
                    } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                        WrapperPlayClientPlayerPositionAndRotation packet =
                                new WrapperPlayClientPlayerPositionAndRotation(event);
                        capture(event.getUser(), packet.getPosition(), packet.getYaw(), packet.getPitch(),
                                packet.isOnGround(), MovementPacketKind.POSITION_AND_ROTATION);
                    } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
                        WrapperPlayClientPlayerRotation packet = new WrapperPlayClientPlayerRotation(event);
                        captureRotation(event.getUser(), packet.getYaw(), packet.getPitch(), packet.isOnGround());
                    } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING) {
                        captureFlying(event.getUser(), new WrapperPlayClientPlayerFlying(event));
                    } else if (event.getPacketType() == PacketType.Play.Client.TELEPORT_CONFIRM) {
                        observeTeleportConfirm(event.getUser(), new WrapperPlayClientTeleportConfirm(event));
                    } else if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
                        observeVehicleMove(new WrapperPlayClientVehicleMove(event));
                    }
                } catch (RuntimeException ex) {
                    rejectPacket(event.getUser(), "MOVEMENT_PACKET_DECODE_ERROR");
                }
            }

            @Override public void onPacketSend(PacketSendEvent event) {
                if (event.isCancelled()
                        || event.getPacketType() != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) return;
                try {
                    User user = event.getUser();
                    if (user == null || user.getUUID() == null) return;
                    synchronized (sessionLock) {
                        if (!states.containsKey(user.getUUID())) return;
                        WrapperPlayServerPlayerPositionAndLook packet =
                                new WrapperPlayServerPlayerPositionAndLook(event);
                        long nowTick = serverTick.get();
                        awaitingTeleport.put(user.getUUID(), new TeleportGate(packet.getTeleportId(),
                                saturatedAdd(nowTick, teleportConfirmTimeoutTicks)));
                        pending.remove(user.getUUID());
                    }
                } catch (RuntimeException ex) {
                    rejectPacket(event.getUser(), "TELEPORT_PACKET_DECODE_ERROR");
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    void stop() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
        synchronized (sessionLock) {
            pending.clear();
            awaitingTeleport.clear();
            vehiclePlayers.clear();
            states.clear();
        }
    }

    void open(UUID playerId) {
        synchronized (sessionLock) {
            closeUnlocked(playerId);
            states.put(playerId, newState());
        }
    }

    void close(UUID playerId) {
        synchronized (sessionLock) {
            closeUnlocked(playerId);
        }
    }

    private void closeUnlocked(UUID playerId) {
        pending.remove(playerId);
        awaitingTeleport.remove(playerId);
        vehiclePlayers.remove(playerId);
        states.remove(playerId);
    }

    void setVehicleMode(UUID playerId, boolean active) {
        if (active) vehiclePlayers.put(playerId, Boolean.TRUE);
        else vehiclePlayers.remove(playerId);
        pending.remove(playerId);
        MovementState state = states.get(playerId);
        if (state != null) state.reset(MovementExemption.VEHICLE);
    }

    void reset(UUID playerId, MovementExemption exemption) {
        pending.remove(playerId);
        MovementState state = states.get(playerId);
        if (state != null) state.reset(exemption);
    }

    void resetWithWindow(UUID playerId, MovementExemption exemption) {
        pending.remove(playerId);
        MovementState state = states.get(playerId);
        if (state != null) state.reset(exemption, serverTick.get(), velocityGraceTicks);
    }

    void resetAndClearTeleport(UUID playerId, MovementExemption exemption) {
        awaitingTeleport.remove(playerId);
        reset(playerId, exemption);
    }

    void processTick() {
        long tick = serverTick.get();
        awaitingTeleport.forEach((playerId, gate) -> {
            if (!gate.activeAt(tick)) awaitingTeleport.remove(playerId, gate);
        });
        states.values().forEach(MovementState::decayOneTick);
        pending.forEach((playerId, raw) -> {
            if (!pending.remove(playerId, raw)) return;
            MovementState state = states.get(playerId);
            if (state == null) return;
            samplesProcessed.incrementAndGet();
            MovementSample previous = state.previous();
            float yaw = raw.yaw();
            float pitch = raw.pitch();
            if (previous != null && !Float.isFinite(yaw)) yaw = previous.yaw();
            if (previous != null && !Float.isFinite(pitch)) pitch = previous.pitch();
            MovementValidationResult result;
            try {
                MovementSample sample = new MovementSample(raw.x(), raw.y(), raw.z(), yaw, pitch, tick,
                        raw.onGround(), raw.packetKind());
                result = state.accept(sample, horizontalAllowance, verticalAllowance,
                        maxCoordinate, maxElapsedTicks, MovementExemption.NONE);
            } catch (RuntimeException ex) {
                addEvidence(playerId, "MOVEMENT_ADAPTER_ERROR", "bounded");
                return;
            }
            if (result.status() != MovementValidationResult.Status.ACCEPTED) {
                signalsEmitted.incrementAndGet();
                addEvidence(playerId, "MOVEMENT_" + result.status().name(), result.reason());
            }
        });
    }

    int stateCount() { return states.size(); }
    long packetsCaptured() { return packetsCaptured.get(); }
    long samplesProcessed() { return samplesProcessed.get(); }
    long signalsEmitted() { return signalsEmitted.get(); }
    long flyingPacketsObserved() { return flyingPacketsObserved.get(); }
    long invalidPacketsRejected() { return invalidPacketsRejected.get(); }
    long teleportConfirmsObserved() { return teleportConfirmsObserved.get(); }
    long vehiclePacketsObserved() { return vehiclePacketsObserved.get(); }
    long movementSuppressedByTeleport() { return movementSuppressedByTeleport.get(); }
    long movementSuppressedByVehicle() { return movementSuppressedByVehicle.get(); }
    long movementStatusPacketsObserved() { return movementStatusPacketsObserved.get(); }

    private void capture(User user, Vector3d position, Float yaw, Float pitch,
                         boolean onGround, MovementPacketKind packetKind) {
        if (user == null || user.getUUID() == null || position == null) return;
        if (teleportGateActive(user.getUUID())) {
            movementSuppressedByTeleport.incrementAndGet();
            return;
        }
        if (vehiclePlayers.containsKey(user.getUUID())) {
            movementSuppressedByVehicle.incrementAndGet();
            return;
        }
        if (!Double.isFinite(position.getX()) || !Double.isFinite(position.getY())
                || !Double.isFinite(position.getZ())) {
            invalidPacketsRejected.incrementAndGet();
            addEvidence(user.getUUID(), "MOVEMENT_INVALID_INPUT", "position");
            return;
        }
        if ((yaw != null && (!Float.isFinite(yaw)))
                || (pitch != null && (!Float.isFinite(pitch) || pitch < -90.0f || pitch > 90.0f))) {
            invalidPacketsRejected.incrementAndGet();
            addEvidence(user.getUUID(), "MOVEMENT_INVALID_INPUT", "rotation");
            return;
        }
        capturePending(user.getUUID(), position.getX(), position.getY(), position.getZ(), yaw, pitch,
                onGround, packetKind);
    }

    private void captureRotation(User user, float yaw, float pitch, boolean onGround) {
        if (user == null || user.getUUID() == null) return;
        if (teleportGateActive(user.getUUID())) {
            movementSuppressedByTeleport.incrementAndGet();
            return;
        }
        if (vehiclePlayers.containsKey(user.getUUID())) {
            movementSuppressedByVehicle.incrementAndGet();
            return;
        }
        MovementState state = states.get(user.getUUID());
        MovementSample previous = state == null ? null : state.previous();
        if (previous == null) return;
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90.0f || pitch > 90.0f) {
            invalidPacketsRejected.incrementAndGet();
            addEvidence(user.getUUID(), "MOVEMENT_INVALID_INPUT", "rotation");
            return;
        }
        capturePending(user.getUUID(), previous.x(), previous.y(), previous.z(), yaw, pitch,
                onGround, MovementPacketKind.ROTATION);
    }

    private void capturePending(UUID playerId, double x, double y, double z, Float yaw, Float pitch,
                                boolean onGround, MovementPacketKind packetKind) {
        Pending old = pending.get(playerId);
        float resolvedYaw = yaw != null ? yaw : old == null ? 0 : old.yaw();
        float resolvedPitch = pitch != null ? pitch : old == null ? 0 : old.pitch();
        pending.put(playerId, new Pending(x, y, z, resolvedYaw, resolvedPitch, onGround, packetKind));
        packetsCaptured.incrementAndGet();
    }

    private void captureFlying(User user, WrapperPlayClientPlayerFlying packet) {
        if (user == null || user.getUUID() == null || packet == null) return;
        movementStatusPacketsObserved.incrementAndGet();
        flyingPacketsObserved.incrementAndGet();
    }

    private void observeTeleportConfirm(User user, WrapperPlayClientTeleportConfirm packet) {
        if (user == null || user.getUUID() == null || packet == null) return;
        teleportConfirmsObserved.incrementAndGet();
        TeleportGate expected = awaitingTeleport.get(user.getUUID());
        long tick = serverTick.get();
        if (expected != null && expected.matches(packet.getTeleportId(), tick)) {
            awaitingTeleport.remove(user.getUUID(), expected);
        } else if (expected != null && expected.activeAt(tick)) {
            addEvidence(user.getUUID(), "TELEPORT_CONFIRM_MISMATCH", "bounded");
        } else if (expected != null) {
            awaitingTeleport.remove(user.getUUID(), expected);
        }
    }

    private void observeVehicleMove(WrapperPlayClientVehicleMove packet) {
        if (packet != null) vehiclePacketsObserved.incrementAndGet();
    }

    private MovementState newState() {
        return new MovementState(++generation, new MovementSimulator(),
                new MovementViolationBuffer(maxViolationScore, violationIncrement, violationDecay));
    }

    private void addEvidence(UUID playerId, String code, String details) {
        evidence.add(EvidenceRecord.of(playerId, code, details));
    }

    private void rejectPacket(User user, String code) {
        invalidPacketsRejected.incrementAndGet();
        if (user != null && user.getUUID() != null) addEvidence(user.getUUID(), code, "bounded");
    }

    private boolean teleportGateActive(UUID playerId) {
        TeleportGate gate = awaitingTeleport.get(playerId);
        long tick = serverTick.get();
        if (gate == null) return false;
        if (gate.activeAt(tick)) return true;
        awaitingTeleport.remove(playerId, gate);
        return false;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static double positiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}

// Core remains independent from PacketEvents/Paper. Physics and world snapshots are next phase.
