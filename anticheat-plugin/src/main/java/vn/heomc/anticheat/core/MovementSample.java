package vn.heomc.anticheat.core;

import java.util.Objects;

/** Immutable movement input; không giữ Bukkit object. */
public record MovementSample(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long serverTick,
        boolean onGround,
        MovementPacketKind packetKind) {
    public MovementSample {
        if (serverTick < 0) {
            throw new IllegalArgumentException("serverTick must be non-negative");
        }
        Objects.requireNonNull(packetKind, "packetKind");
    }

    public MovementSample(double x, double y, double z, float yaw, float pitch, long serverTick) {
        this(x, y, z, yaw, pitch, serverTick, false, MovementPacketKind.POSITION_AND_ROTATION);
    }

    public boolean finitePosition() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public boolean finiteRotation() {
        return Float.isFinite(yaw) && Float.isFinite(pitch) && pitch >= -90.0f && pitch <= 90.0f;
    }

    public double horizontalDistanceTo(MovementSample other) {
        Objects.requireNonNull(other, "other");
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.hypot(dx, dz);
    }

    public double verticalDistanceTo(MovementSample other) {
        Objects.requireNonNull(other, "other");
        return Math.abs(y - other.y);
    }
}
