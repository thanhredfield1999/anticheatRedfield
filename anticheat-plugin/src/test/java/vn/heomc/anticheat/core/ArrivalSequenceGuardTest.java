package vn.heomc.anticheat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArrivalSequenceGuardTest {
    @Test
    void acceptsStrictlyIncreasingSequence() {
        ArrivalSequenceGuard guard = new ArrivalSequenceGuard(8);

        assertEquals(ArrivalSequenceGuard.Result.ACCEPTED, guard.observe(1));
        assertEquals(ArrivalSequenceGuard.Result.ACCEPTED, guard.observe(2));
    }

    @Test
    void rejectsDuplicateAndOutOfOrderSequence() {
        ArrivalSequenceGuard guard = new ArrivalSequenceGuard(8);
        guard.observe(4);

        assertEquals(ArrivalSequenceGuard.Result.DUPLICATE, guard.observe(4));
        assertEquals(ArrivalSequenceGuard.Result.OUT_OF_ORDER, guard.observe(3));
    }

    @Test
    void rejectsSequenceOverflowAndKeepsLastAcceptedValue() {
        ArrivalSequenceGuard guard = new ArrivalSequenceGuard(8);
        guard.observe(Long.MAX_VALUE);

        assertEquals(ArrivalSequenceGuard.Result.OUT_OF_ORDER, guard.observe(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, guard.lastAccepted());
    }

    @Test
    void resetStartsNewBoundedSession() {
        ArrivalSequenceGuard guard = new ArrivalSequenceGuard(8);
        guard.observe(9);
        guard.reset();

        assertEquals(ArrivalSequenceGuard.Result.ACCEPTED, guard.observe(1));
        assertEquals(1, guard.lastAccepted());
    }
}
