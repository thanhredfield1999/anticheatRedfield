package vn.heomc.anticheat.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeleportGateTest {
    @Test
    void expiresAtBoundedTick() {
        TeleportGate gate = new TeleportGate(7, 40);

        assertTrue(gate.matches(7, 40));
        assertFalse(gate.matches(7, 41));
        assertFalse(gate.matches(8, 40));
    }
}