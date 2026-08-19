package vn.heomc.anticheat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MovementSimulatorTest {
    private final MovementSimulator simulator = new MovementSimulator();

    @Test
    void rejectsElapsedTickMismatch() {
        MovementSimulationInput input = new MovementSimulationInput(
                sample(10, 0, 64, 0), sample(11, 1, 64, 0), 2, 0.8, 0.9, MovementExemption.NONE);

        MovementValidationResult result = simulator.evaluate(input);

        assertEquals(MovementValidationResult.Status.OUT_OF_ORDER, result.status());
        assertEquals("elapsed-tick-mismatch", result.reason());
    }

    @Test
    void exemptionAcceptsLargeDeltaAsNonFlagSignal() {
        MovementSimulationInput input = new MovementSimulationInput(
                sample(10, 0, 64, 0), sample(11, 100, 100, 0), 1, 0.8, 0.9,
                MovementExemption.TELEPORT);

        MovementValidationResult result = simulator.evaluate(input);

        assertEquals(MovementValidationResult.Status.ACCEPTED, result.status());
        assertEquals("exempt-teleport", result.reason());
    }

    @Test
    void excessiveDeltaProducesObserveSignal() {
        MovementSimulationInput input = new MovementSimulationInput(
                sample(10, 0, 64, 0), sample(11, 2, 64, 0), 1, 0.8, 0.9, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.EXCESSIVE_DELTA,
                simulator.evaluate(input).status());
    }

    @Test
    void normalFallSpeedIsAccepted() {
        MovementSimulationInput input = new MovementSimulationInput(
                sample(10, 0, 64, 0), sample(11, 0, 60.1, 0), 1, 0.8, 4.0, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.ACCEPTED,
                simulator.evaluate(input).status());
    }

    @Test
    void inputRequiresPositiveFiniteLimits() {
        assertThrows(IllegalArgumentException.class, () -> new MovementSimulationInput(
                sample(1, 0, 64, 0), sample(2, 0, 64, 0), 1, 0, 1, MovementExemption.NONE));
        assertThrows(IllegalArgumentException.class, () -> new MovementSimulationInput(
                sample(1, 0, 64, 0), sample(2, 0, 64, 0), Long.MAX_VALUE, 1, 1, MovementExemption.NONE));
        assertThrows(NullPointerException.class, () -> new MovementSimulationInput(
                null, sample(2, 0, 64, 0), 1, 1, 1, MovementExemption.NONE));
    }

    @Test
    void exemptionDoesNotBypassCoordinateDomain() {
        MovementSimulationInput input = new MovementSimulationInput(
                sample(10, 0, 64, 0), sample(11, 30_000_001, 64, 0), 1, 0.8, 0.9,
                30_000_000, 20, MovementExemption.TELEPORT);

        MovementValidationResult result = simulator.evaluate(input);

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertEquals("coordinate-domain", result.reason());
    }

    @Test
    void violationDecaySaturatesForHugeTickCount() {
        MovementViolationBuffer buffer = new MovementViolationBuffer(2, 1, Double.MAX_VALUE);
        MovementValidationResult flag = new MovementValidationResult(
                MovementValidationResult.Status.EXCESSIVE_DELTA, "delta", 2, 0);
        buffer.add(flag);

        assertEquals(0, buffer.decay(Long.MAX_VALUE));
    }

    @Test
    void violationBufferIsBoundedAndDecays() {
        MovementViolationBuffer buffer = new MovementViolationBuffer(2, 1, 0.25);
        MovementValidationResult flag = new MovementValidationResult(
                MovementValidationResult.Status.EXCESSIVE_DELTA, "delta", 2, 0);

        assertEquals(1, buffer.add(flag));
        assertEquals(2, buffer.add(flag));
        assertEquals(2, buffer.add(flag));
        assertEquals(1.5, buffer.decay(2));
        assertThrows(IllegalArgumentException.class, () -> buffer.decay(-1));
    }

    @Test
    void violationScoreSaturatesWithoutFloatingPointOverflow() {
        MovementViolationBuffer buffer = new MovementViolationBuffer(Double.MAX_VALUE, Double.MAX_VALUE, 0);
        MovementValidationResult flag = new MovementValidationResult(
                MovementValidationResult.Status.EXCESSIVE_DELTA, "delta", 2, 0);

        assertEquals(Double.MAX_VALUE, buffer.add(flag));
        assertEquals(Double.MAX_VALUE, buffer.add(flag));
    }

    private static MovementSample sample(long tick, double x, double y, double z) {
        return new MovementSample(x, y, z, 0, 0, tick);
    }
}
