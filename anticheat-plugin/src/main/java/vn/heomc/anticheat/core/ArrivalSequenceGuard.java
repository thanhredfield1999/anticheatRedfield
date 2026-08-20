package vn.heomc.anticheat.core;

/** Bounded monotonic packet-arrival sequence per connection. */
public final class ArrivalSequenceGuard {
    public enum Result { ACCEPTED, DUPLICATE, OUT_OF_ORDER }

    private final int maxAccepted;
    private int accepted;
    private long lastAccepted;
    private boolean initialized;

    public ArrivalSequenceGuard(int maxAccepted) {
        if (maxAccepted < 1) throw new IllegalArgumentException("maxAccepted must be positive");
        this.maxAccepted = maxAccepted;
    }

    public synchronized Result observe(long sequence) {
        if (accepted >= maxAccepted) return Result.OUT_OF_ORDER;
        if (!initialized) {
            initialized = true;
            lastAccepted = sequence;
            accepted++;
            return Result.ACCEPTED;
        }
        if (sequence == lastAccepted) return Result.DUPLICATE;
        if (sequence < lastAccepted) return Result.OUT_OF_ORDER;
        lastAccepted = sequence;
        accepted++;
        return Result.ACCEPTED;
    }

    public synchronized void reset() {
        initialized = false;
        accepted = 0;
        lastAccepted = 0;
    }

    public synchronized long lastAccepted() {
        return lastAccepted;
    }
}
