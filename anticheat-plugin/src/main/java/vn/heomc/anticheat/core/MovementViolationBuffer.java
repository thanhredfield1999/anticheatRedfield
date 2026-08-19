package vn.heomc.anticheat.core;

public final class MovementViolationBuffer {
    private final double maxScore;
    private final double increment;
    private final double decayPerTick;
    private double score;

    public MovementViolationBuffer(double maxScore, double increment, double decayPerTick) {
        if (!Double.isFinite(maxScore) || maxScore <= 0 || !Double.isFinite(increment) || increment <= 0
                || !Double.isFinite(decayPerTick) || decayPerTick < 0) {
            throw new IllegalArgumentException("invalid violation limits");
        }
        this.maxScore = maxScore;
        this.increment = increment;
        this.decayPerTick = decayPerTick;
    }

    public synchronized double add(MovementValidationResult result) {
        if (result == null) throw new NullPointerException("result");
        if (result.status() == MovementValidationResult.Status.EXCESSIVE_DELTA) {
            double next = increment > maxScore - score ? maxScore : score + increment;
            score = Math.min(maxScore, next);
        }
        return score;
    }

    public synchronized double decay(long elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        if (elapsedTicks > 0 && decayPerTick > 0) {
            double decay = decayPerTick > Double.MAX_VALUE / elapsedTicks
                    ? Double.MAX_VALUE : decayPerTick * elapsedTicks;
            score = Math.max(0, score - decay);
        }
        return score;
    }

    public synchronized double score() {
        return score;
    }

    public synchronized void reset() {
        score = 0;
    }
}
