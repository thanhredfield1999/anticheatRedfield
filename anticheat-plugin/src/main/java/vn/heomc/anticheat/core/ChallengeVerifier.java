package vn.heomc.anticheat.core;


import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChallengeVerifier {
    private final Clock clock;
    private final long ttlMillis;
    private final int maxWireBytes;
    private final int maxClientBuildLength;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChallengeVerifier(Clock clock, long ttlMillis, int maxWireBytes, int maxClientBuildLength) {
        if (ttlMillis < 1 || maxWireBytes < 1 || maxClientBuildLength < 1) throw new IllegalArgumentException("limits must be positive");
        this.clock = clock; this.ttlMillis = ttlMillis; this.maxWireBytes = maxWireBytes; this.maxClientBuildLength = maxClientBuildLength;
    }

    public Challenge open(UUID playerId, UUID connectionId) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Challenge challenge = new Challenge(playerId, connectionId, nonce, clock.millis());
        sessions.put(playerId, new Session(challenge));
        return challenge;
    }

    public VerificationState state(UUID playerId) { Session s = sessions.get(playerId); return s == null ? VerificationState.CLOSED : s.state; }

    public VerificationResult acceptWire(UUID playerId, byte[] wire) {
        if (wire == null || wire.length == 0 || wire.length > maxWireBytes) return reject(Acceptance.MALFORMED, "payload");
        try {
            return accept(playerId, WireCodec.decodeResponse(wire, maxWireBytes, maxClientBuildLength));
        } catch (WireCodec.WireFormatException ex) {
            return reject(Acceptance.MALFORMED, ex.getMessage());
        }
    }

    public VerificationResult accept(UUID playerId, Response response) {
        if (response == null) return reject(Acceptance.MALFORMED, "response");
        Session s = sessions.get(playerId);
        if (s == null || s.state == VerificationState.CLOSED) return reject(Acceptance.REJECTED, "session");
        synchronized (s) {
            if (s.state == VerificationState.VERIFIED) {
                return response.sequence() == s.lastSequence
                        ? reject(Acceptance.DUPLICATE, "verified")
                        : reject(Acceptance.REPLAY, "sequence");
            }
            if (clock.millis() - s.challenge.issuedAtMillis() >= ttlMillis) {
                s.state = VerificationState.EXPIRED;
                return reject(Acceptance.EXPIRED, "challenge");
            }
            if (response.protocol() != 1 || !s.challenge.connectionId().equals(response.connectionId())
                    || !s.challenge.nonce().equals(response.nonce())) {
                s.state = VerificationState.FAILED;
                return reject(Acceptance.REJECTED, "binding");
            }
            if (response.sequence() <= s.lastSequence) return reject(Acceptance.REPLAY, "sequence");
            s.lastSequence = response.sequence();
            s.state = VerificationState.VERIFIED;
            return new VerificationResult(Acceptance.ACCEPTED, "observe-only");
        }
    }

    public void close(UUID playerId) { Session s = sessions.get(playerId); if (s != null) s.state = VerificationState.CLOSED; }
    public void close(UUID playerId, UUID connectionId) {
        sessions.computeIfPresent(playerId, (ignored, s) -> {
            synchronized (s) {
                if (s.challenge.connectionId().equals(connectionId)) return null;
            }
            return s;
        });
    }
    public UUID connectionId(UUID playerId) { Session s = sessions.get(playerId); return s == null ? null : s.challenge.connectionId(); }
    public int sessionCount() { return sessions.size(); }
    public void clear() { sessions.clear(); }
    private VerificationResult reject(Acceptance a, String reason) { return new VerificationResult(a, reason); }
    private static final class Session { final Challenge challenge; VerificationState state = VerificationState.UNVERIFIED; long lastSequence; Session(Challenge c) { challenge = c; } }
}
