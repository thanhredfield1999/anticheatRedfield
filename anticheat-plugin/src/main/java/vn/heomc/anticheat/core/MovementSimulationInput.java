package vn.heomc.anticheat.core;

import java.util.Objects;

public record MovementSimulationInput(
        MovementSample previous,
        MovementSample current,
        long elapsedTicks,
        double horizontalAllowancePerTick,
        double verticalAllowancePerTick,
        double maxCoordinateMagnitude,
        long maxElapsedTicks,
        MovementExemption exemption) {
    public MovementSimulationInput {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(exemption, "exemption");
        if (elapsedTicks < 1 || !Double.isFinite(horizontalAllowancePerTick)
                || horizontalAllowancePerTick <= 0 || !Double.isFinite(verticalAllowancePerTick)
                || verticalAllowancePerTick <= 0 || !Double.isFinite(maxCoordinateMagnitude)
                || maxCoordinateMagnitude <= 0 || maxElapsedTicks < 1 || elapsedTicks > maxElapsedTicks) {
            throw new IllegalArgumentException("invalid simulation input");
        }
    }

    public MovementSimulationInput(MovementSample previous, MovementSample current, long elapsedTicks,
                                   double horizontalAllowancePerTick, double verticalAllowancePerTick,
                                   MovementExemption exemption) {
        this(previous, current, elapsedTicks, horizontalAllowancePerTick, verticalAllowancePerTick,
                30_000_000.0, 20, exemption);
    }
}

