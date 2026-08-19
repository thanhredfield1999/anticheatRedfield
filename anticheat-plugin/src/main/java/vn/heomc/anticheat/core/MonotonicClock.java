package vn.heomc.anticheat.core;

/** Monotonic elapsed-time source for TTL decisions. */
public final class MonotonicClock implements Clock {
    @Override
    public long millis() {
        return System.nanoTime() / 1_000_000L;
    }
}
