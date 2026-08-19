package vn.heomc.anticheat.core;

public record MovementValidationResult(Status status, String reason, double horizontalDistance, double verticalDistance) {
    public enum Status { ACCEPTED, INVALID_INPUT, EXCESSIVE_DELTA, OUT_OF_ORDER }

    public MovementValidationResult {
        if (status == null || reason == null) {
            throw new NullPointerException("status and reason must not be null");
        }
        if (reason.isBlank() || !Double.isFinite(horizontalDistance) || horizontalDistance < 0
                || !Double.isFinite(verticalDistance) || verticalDistance < 0) {
            throw new IllegalArgumentException("invalid movement result");
        }
    }
}
