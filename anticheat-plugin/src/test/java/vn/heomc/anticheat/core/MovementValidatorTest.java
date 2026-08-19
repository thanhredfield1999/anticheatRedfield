package vn.heomc.anticheat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MovementValidatorTest {
    private final MovementValidator validator = new MovementValidator(0.8, 0.9);

    @Test
    void normalizedContextPreservesOnGroundAndPacketKind() {
        MovementSample sample = new MovementSample(0, 64, 0, 0, 0, 10, true,
                MovementPacketKind.POSITION);

        assertEquals(true, sample.onGround());
        assertEquals(MovementPacketKind.POSITION, sample.packetKind());
    }

    @Test
    void acceptsFiniteMovementWithinPerTickBaseline() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 10),
                new MovementSample(0.5, 64.4, 0, 10, 5, 11));

        assertEquals(MovementValidationResult.Status.ACCEPTED, result.status());
    }

    @Test
    void rejectsExcessiveHorizontalDelta() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 10),
                new MovementSample(2, 64, 0, 0, 0, 11));

        assertEquals(MovementValidationResult.Status.EXCESSIVE_DELTA, result.status());
        assertEquals("delta", result.reason());
    }

    @Test
    void rejectsNonFiniteInput() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 10),
                new MovementSample(Double.NaN, 64, 0, 0, 0, 11));

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
    }

    @Test
    void rejectsOutOfOrderTicks() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 10),
                new MovementSample(0, 64, 0, 0, 0, 10));

        assertEquals(MovementValidationResult.Status.OUT_OF_ORDER, result.status());
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new MovementValidator(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MovementValidator(1, Double.NaN));
    }

    @Test
    void rejectsExtremeTickGapInsteadOfOverflowingAllowance() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 0),
                new MovementSample(1, 64, 0, 0, 0, Long.MAX_VALUE));

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertEquals("tick-gap", result.reason());
    }

    @Test
    void rejectsCoordinateOutsideConfiguredDomain() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 10),
                new MovementSample(30_000_001, 64, 0, 0, 0, 11));

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
        assertEquals("coordinate-domain", result.reason());
    }

    @Test
    void rejectsPitchOutsideProtocolDomain() {
        MovementValidationResult result = validator.validate(
                new MovementSample(0, 64, 0, 0, 0, 10),
                new MovementSample(0, 64, 0, 0, 90.1f, 11));

        assertEquals(MovementValidationResult.Status.INVALID_INPUT, result.status());
    }

    @Test
    void rejectsInvalidResultInvariant() {
        assertThrows(IllegalArgumentException.class,
                () -> new MovementValidationResult(MovementValidationResult.Status.ACCEPTED, "", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MovementValidationResult(MovementValidationResult.Status.ACCEPTED, "x", -1, 0));
    }
}
