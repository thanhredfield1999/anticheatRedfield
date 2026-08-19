package vn.heomc.anticheat.core;

import java.time.Instant;
import java.util.UUID;

public record EvidenceRecord(int schemaVersion, long timestampMillis, UUID playerId, String code, String details) {
    public static EvidenceRecord of(UUID playerId, String code, String ignoredSensitiveValue) {
        return new EvidenceRecord(1, Instant.now().toEpochMilli(), playerId, code, "redacted");
    }
}
