package vn.heomc.anticheat.core;

import java.util.Objects;

/** Bounded tick window for server-caused movement. */
public record MovementExemptionWindow(MovementExemption reason, long startsAtTick, long expiresAtTick) {
    public MovementExemptionWindow {
        Objects.requireNonNull(reason, "reason");
        if (reason == MovementExemption.NONE) throw new IllegalArgumentException("reason must be an exemption");
        if (startsAtTick < 0 || expiresAtTick < startsAtTick) {
            throw new IllegalArgumentException("invalid exemption window");
        }
    }

    public static MovementExemptionWindow of(MovementExemption reason, long startsAtTick, long durationTicks) {
        if (durationTicks < 1) throw new IllegalArgumentException("durationTicks must be positive");
        long expiresAtTick = startsAtTick > Long.MAX_VALUE - (durationTicks - 1)
                ? Long.MAX_VALUE : startsAtTick + durationTicks - 1;
        return new MovementExemptionWindow(reason, startsAtTick, expiresAtTick);
    }

    public boolean activeAt(long tick) {
        return tick >= startsAtTick && tick <= expiresAtTick;
    }
}

