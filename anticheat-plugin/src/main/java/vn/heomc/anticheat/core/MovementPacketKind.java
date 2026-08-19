package vn.heomc.anticheat.core;

/** Packet category after protocol normalization; not cheat proof. */
public enum MovementPacketKind {
    POSITION,
    POSITION_AND_ROTATION,
    ROTATION,
    STATUS_ONLY
}