package vn.heomc.anticheat.core;

import java.util.UUID;

public record Challenge(UUID playerId, UUID connectionId, String nonce, long issuedAtMillis) {}
