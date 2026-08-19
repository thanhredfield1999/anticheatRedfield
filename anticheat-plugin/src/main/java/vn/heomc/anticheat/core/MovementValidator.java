package vn.heomc.anticheat.core;

import java.util.Objects;

/** Pure-Java baseline. Chưa mô phỏng physics hoặc world state. */
public final class MovementValidator {
    private final double maxHorizontalPerTick;
    private final double maxVerticalPerTick;
    private final double maxCoordinateMagnitude;
    private final long maxTickGap;

    public MovementValidator(double maxHorizontalPerTick, double maxVerticalPerTick) {
        this(maxHorizontalPerTick, maxVerticalPerTick, 30_000_000.0, 20);
    }

    public MovementValidator(double maxHorizontalPerTick, double maxVerticalPerTick,
                             double maxCoordinateMagnitude, long maxTickGap) {
        if (!Double.isFinite(maxHorizontalPerTick) || maxHorizontalPerTick <= 0
                || !Double.isFinite(maxVerticalPerTick) || maxVerticalPerTick <= 0
                || !Double.isFinite(maxCoordinateMagnitude) || maxCoordinateMagnitude <= 0
                || maxTickGap < 1) {
            throw new IllegalArgumentException("movement limits must be finite and positive");
        }
        this.maxHorizontalPerTick = maxHorizontalPerTick;
        this.maxVerticalPerTick = maxVerticalPerTick;
        this.maxCoordinateMagnitude = maxCoordinateMagnitude;
        this.maxTickGap = maxTickGap;
    }

    public MovementValidationResult validate(MovementSample previous, MovementSample current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (!previous.finitePosition() || !previous.finiteRotation()
                || !current.finitePosition() || !current.finiteRotation()) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "non-finite-sample", 0, 0);
        }
        if (!withinCoordinateDomain(previous) || !withinCoordinateDomain(current)) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "coordinate-domain", 0, 0);
        }
        if (current.serverTick() <= previous.serverTick()) {
            return result(MovementValidationResult.Status.OUT_OF_ORDER, "tick-order", 0, 0);
        }
        long ticks = current.serverTick() - previous.serverTick();
        if (ticks > maxTickGap) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "tick-gap", 0, 0);
        }
        double horizontal = current.horizontalDistanceTo(previous);
        double vertical = current.verticalDistanceTo(previous);
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "delta-domain", 0, 0);
        }
        if (horizontal / ticks > maxHorizontalPerTick || vertical / ticks > maxVerticalPerTick) {
            return result(MovementValidationResult.Status.EXCESSIVE_DELTA, "delta", horizontal, vertical);
        }
        return result(MovementValidationResult.Status.ACCEPTED, "baseline", horizontal, vertical);
    }

    private boolean withinCoordinateDomain(MovementSample sample) {
        return Math.abs(sample.x()) <= maxCoordinateMagnitude
                && Math.abs(sample.y()) <= maxCoordinateMagnitude
                && Math.abs(sample.z()) <= maxCoordinateMagnitude;
    }

    private MovementValidationResult result(MovementValidationResult.Status status, String reason,
                                            double horizontal, double vertical) {
        return new MovementValidationResult(status, reason, horizontal, vertical);
    }
}
