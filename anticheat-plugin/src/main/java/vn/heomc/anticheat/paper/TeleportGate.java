package vn.heomc.anticheat.paper;

/** Bounded server-teleport correlation. Pure state, no Bukkit access. */
record TeleportGate(int teleportId, long expiresAtTick) {
    boolean activeAt(long tick) {
        return tick <= expiresAtTick;
    }

    boolean matches(int candidateId, long tick) {
        return activeAt(tick) && teleportId == candidateId;
    }
}
