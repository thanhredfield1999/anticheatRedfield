package vn.heomc.anticheat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MovementStateTest {
    @Test
    void firstSampleInitializesWithoutFlag() {
        MovementState state = state();

        MovementValidationResult result = state.accept(sample(10, 0), 0.8, 0.9,
                30_000_000, 20, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.ACCEPTED, result.status());
        assertEquals("initial-sample", result.reason());
        assertEquals(0, state.violationScore());
    }

    @Test
    void excessiveMovementUpdatesBoundedScoreAndPreviousSample() {
        MovementState state = state();
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        MovementValidationResult result = state.accept(sample(11, 2), 0.8, 0.9,
                30_000_000, 20, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.EXCESSIVE_DELTA, result.status());
        assertEquals(1, state.violationScore());
        assertEquals(11, state.previous().serverTick());
    }

    @Test
    void resetClearsPreviousScoreAndNextSampleIsSafe() {
        MovementState state = state();
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);
        state.accept(sample(11, 2), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);
        state.reset(MovementExemption.TELEPORT);

        assertNull(state.previous());
        assertEquals(0, state.violationScore());
        assertEquals(MovementExemption.TELEPORT, state.exemption());
        assertEquals(MovementValidationResult.Status.ACCEPTED,
                state.accept(sample(100, 100), 0.8, 0.9, 30_000_000, 20,
                        MovementExemption.NONE).status());
    }

    @Test
    void velocityWindowExemptsEachSampleUntilExpiry() {
        MovementState state = state();
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);
        state.reset(MovementExemption.VELOCITY, 11, 3);

        assertEquals("initial-sample", state.accept(sample(11, 100), 0.8, 0.9,
                30_000_000, 20, MovementExemption.NONE).reason());
        assertEquals("exempt-velocity", state.accept(sample(12, 200), 0.8, 0.9,
                30_000_000, 20, MovementExemption.NONE).reason());
        assertEquals("exempt-velocity", state.accept(sample(13, 300), 0.8, 0.9,
                30_000_000, 20, MovementExemption.NONE).reason());
        assertEquals("delta", state.accept(sample(14, 400), 0.8, 0.9,
                30_000_000, 20, MovementExemption.NONE).reason());
    }

    @Test
    void invalidSimulationInputDoesNotThrowOrFlagAsDelta() {
        MovementState state = state();
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        MovementValidationResult result = state.accept(sample(11, 1), 0, 0.9,
                30_000_000, 20, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertEquals("simulation-limits", result.reason());
        assertEquals(0, state.violationScore());
    }

    @Test
    void decayReducesViolationScore() {
        MovementState state = new MovementState(1, new MovementSimulator(),
                new MovementViolationBuffer(10, 1, 0.05));
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);
        state.accept(sample(11, 2), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        assertEquals(1, state.violationScore());
        state.decayOneTick();
        assertEquals(0.95, state.violationScore(), 1.0e-9);
    }

    @Test
    void invalidFirstSampleDoesNotBecomeState() {
        MovementState state = state();

        MovementValidationResult result = state.accept(
                new MovementSample(30_000_001, 64, 0, 0, 0, 10),
                0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertNull(state.previous());
    }

    @Test
    void invalidLaterSampleDoesNotAdvancePreviousState() {
        MovementState state = state();
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        MovementValidationResult result = state.accept(
                new MovementSample(30_000_001, 64, 0, 0, 0, 11),
                0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertEquals(10, state.previous().serverTick());
    }

    @Test
    void outOfOrderSampleDoesNotAdvanceExemption() {
        MovementState state = state();
        state.accept(sample(10, 0), 0.8, 0.9, 30_000_000, 20, MovementExemption.NONE);

        MovementValidationResult result = state.accept(sample(9, 0), 0.8, 0.9,
                30_000_000, 20, MovementExemption.TELEPORT);

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertEquals(MovementExemption.NONE, state.exemption());
    }

    private static MovementState state() {
        return new MovementState(1, new MovementSimulator(),
                new MovementViolationBuffer(10, 1, 0));
    }

    private static MovementSample sample(long tick, double x) {
        return new MovementSample(x, 64, 0, 0, 0, tick);
    }
}
