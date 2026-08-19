package vn.heomc.anticheat.core;

import java.util.Objects;

/** Observe-only simulation boundary. Không kết luận cheating. */
public final class MovementSimulator {
    public MovementValidationResult evaluate(MovementSimulationInput input) {
        Objects.requireNonNull(input, "input");
        MovementSample previous = input.previous();
        MovementSample current = input.current();
        if (!previous.finitePosition() || !previous.finiteRotation()
                || !current.finitePosition() || !current.finiteRotation()) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "non-finite-sample", 0, 0);
        }
        if (!withinCoordinateDomain(previous, input.maxCoordinateMagnitude())
                || !withinCoordinateDomain(current, input.maxCoordinateMagnitude())) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "coordinate-domain", 0, 0);
        }
        if (current.serverTick() <= previous.serverTick()) {
            return result(MovementValidationResult.Status.OUT_OF_ORDER, "tick-order", 0, 0);
        }
        long ticks = current.serverTick() - previous.serverTick();
        if (ticks != input.elapsedTicks()) {
            return result(MovementValidationResult.Status.OUT_OF_ORDER, "elapsed-tick-mismatch", 0, 0);
        }
        double horizontal = current.horizontalDistanceTo(previous);
        double vertical = current.verticalDistanceTo(previous);
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) {
            return result(MovementValidationResult.Status.INVALID_INPUT, "delta-domain", 0, 0);
        }
        if (input.exemption() != MovementExemption.NONE) {
            return result(MovementValidationResult.Status.ACCEPTED,
                    "exempt-" + input.exemption().name().toLowerCase(), horizontal, vertical);
        }
        if (horizontal / ticks > input.horizontalAllowancePerTick()
                || vertical / ticks > input.verticalAllowancePerTick()) {
            return result(MovementValidationResult.Status.EXCESSIVE_DELTA, "delta", horizontal, vertical);
        }
        return result(MovementValidationResult.Status.ACCEPTED, "baseline", horizontal, vertical);
    }

    private boolean withinCoordinateDomain(MovementSample sample, double maximum) {
        return Math.abs(sample.x()) <= maximum && Math.abs(sample.y()) <= maximum
                && Math.abs(sample.z()) <= maximum;
    }

    private MovementValidationResult result(MovementValidationResult.Status status, String reason,
                                            double horizontal, double vertical) {
        return new MovementValidationResult(status, reason, horizontal, vertical);
    }
}
