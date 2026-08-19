package vn.heomc.anticheat.core;

import java.util.UUID;

public record Response(int protocol, UUID connectionId, String nonce, long sequence, String clientBuildId) {}
