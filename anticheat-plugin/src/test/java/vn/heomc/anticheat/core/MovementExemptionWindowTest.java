package vn.heomc.anticheat.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementExemptionWindowTest {
    @Test
    void staysActiveOnlyThroughBoundedExpiryTick() {
        MovementExemptionWindow window = MovementExemptionWindow.of(MovementExemption.VELOCITY, 10, 3);

        assertFalse(window.activeAt(9));
        assertTrue(window.activeAt(10));
        assertTrue(window.activeAt(12));
        assertFalse(window.activeAt(13));
    }

    @Test
    void saturatesExpiryWithoutOverflow() {
        MovementExemptionWindow window = MovementExemptionWindow.of(
                MovementExemption.KNOCKBACK, Long.MAX_VALUE - 1, 10);

        assertTrue(window.activeAt(Long.MAX_VALUE));
    }
}

