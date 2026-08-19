package vn.heomc.anticheat.core;

import java.util.Objects;

/** Bounded per-session movement state. Không giữ Bukkit object. */
public final class MovementState {
    private final long sessionGeneration;
    private final MovementSimulator simulator;
    private final MovementViolationBuffer violations;
    private MovementSample previous;
    private MovementExemption exemption = MovementExemption.JOIN;
    private MovementExemptionWindow exemptionWindow;

    public MovementState(long sessionGeneration, MovementSimulator simulator,
                         MovementViolationBuffer violations) {
        if (sessionGeneration < 0) throw new IllegalArgumentException("sessionGeneration must be non-negative");
        this.sessionGeneration = sessionGeneration;
        this.simulator = Objects.requireNonNull(simulator, "simulator");
        this.violations = Objects.requireNonNull(violations, "violations");
    }

    public synchronized MovementValidationResult accept(MovementSample current,
                                                        double horizontalAllowancePerTick,
                                                        double verticalAllowancePerTick,
                                                        double maxCoordinateMagnitude,
                                                        long maxElapsedTicks,
                                                        MovementExemption nextExemption) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(nextExemption, "nextExemption");
        try {
            validateLimits(horizontalAllowancePerTick, verticalAllowancePerTick,
                    maxCoordinateMagnitude, maxElapsedTicks);
        } catch (IllegalArgumentException ex) {
            return new MovementValidationResult(MovementValidationResult.Status.INVALID_INPUT,
                    "simulation-limits", 0, 0);
        }
        if (!current.finitePosition() || !current.finiteRotation()
                || !withinCoordinateDomain(current, maxCoordinateMagnitude)) {
            return new MovementValidationResult(MovementValidationResult.Status.INVALID_INPUT,
                    "sample-domain", 0, 0);
        }
        if (previous == null) {
            previous = current;
            exemption = nextExemption;
            return new MovementValidationResult(MovementValidationResult.Status.ACCEPTED,
                    "initial-sample", 0, 0);
        }
        long elapsed;
        try {
            elapsed = Math.subtractExact(current.serverTick(), previous.serverTick());
        } catch (ArithmeticException ex) {
            return new MovementValidationResult(MovementValidationResult.Status.OUT_OF_ORDER,
                    "tick-overflow", 0, 0);
        }
        MovementExemption activeExemption = exemptionWindow != null && exemptionWindow.activeAt(current.serverTick())
                ? exemptionWindow.reason() : MovementExemption.NONE;
        MovementSimulationInput input;
        try {
            input = new MovementSimulationInput(previous, current, elapsed,
                    horizontalAllowancePerTick, verticalAllowancePerTick,
                    maxCoordinateMagnitude, maxElapsedTicks, activeExemption);
        } catch (IllegalArgumentException ex) {
            return new MovementValidationResult(MovementValidationResult.Status.INVALID_INPUT,
                    "simulation-input", 0, 0);
        }
        MovementValidationResult result = simulator.evaluate(input);
        violations.add(result);
        if (result.status() != MovementValidationResult.Status.INVALID_INPUT
                && result.status() != MovementValidationResult.Status.OUT_OF_ORDER) {
            previous = current;
            exemption = nextExemption;
        }
        return result;
    }

    public synchronized void reset(MovementExemption reason) {
        exemption = Objects.requireNonNull(reason, "reason");
        exemptionWindow = null;
        previous = null;
        violations.reset();
    }

    public synchronized void reset(MovementExemption reason, long startsAtTick, long durationTicks) {
        exemption = Objects.requireNonNull(reason, "reason");
        exemptionWindow = MovementExemptionWindow.of(reason, startsAtTick, durationTicks);
        previous = null;
        violations.reset();
    }

    public synchronized double decay(long elapsedTicks) {
        return violations.decay(elapsedTicks);
    }

    public synchronized void decayOneTick() {
        violations.decay(1);
    }

    public long sessionGeneration() { return sessionGeneration; }
    public synchronized MovementSample previous() { return previous; }
    public synchronized MovementExemption exemption() { return exemption; }
    public double violationScore() { return violations.score(); }

    private static void validateLimits(double horizontal, double vertical, double coordinate, long ticks) {
        if (!Double.isFinite(horizontal) || horizontal <= 0 || !Double.isFinite(vertical) || vertical <= 0
                || !Double.isFinite(coordinate) || coordinate <= 0 || ticks < 1) {
            throw new IllegalArgumentException("invalid movement limits");
        }
    }

    private static boolean withinCoordinateDomain(MovementSample sample, double maximum) {
        return Math.abs(sample.x()) <= maximum && Math.abs(sample.y()) <= maximum
                && Math.abs(sample.z()) <= maximum;
    }
}

