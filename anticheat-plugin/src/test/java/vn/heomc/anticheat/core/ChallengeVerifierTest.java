package vn.heomc.anticheat.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengeVerifierTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONNECTION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void challengeStartsObserveSessionAndValidResponseVerifies() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 128, 32);
        Challenge challenge = verifier.open(PLAYER, CONNECTION);

        assertEquals(VerificationState.UNVERIFIED, verifier.state(PLAYER));
        VerificationResult result = verifier.accept(PLAYER,
                new Response(1, CONNECTION, challenge.nonce(), 1, "fabric-client"));

        assertEquals(Acceptance.ACCEPTED, result.acceptance());
        assertEquals(VerificationState.VERIFIED, verifier.state(PLAYER));
    }

    @Test
    void malformedResponseRejectedWithoutStateMutation() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 32, 32);
        verifier.open(PLAYER, CONNECTION);

        VerificationResult result = verifier.acceptWire(PLAYER, "v1|1|not-a-uuid|nonce|1".getBytes());

        assertEquals(Acceptance.MALFORMED, result.acceptance());
        assertEquals(VerificationState.UNVERIFIED, verifier.state(PLAYER));
    }

    @Test
    void malformedUtf8Rejected() {
        ChallengeVerifier verifier = new ChallengeVerifier(new MutableClock(1_000), 10_000, 128, 32);
        verifier.open(PLAYER, CONNECTION);

        assertEquals(Acceptance.MALFORMED, verifier.acceptWire(PLAYER,
                new byte[] {(byte) 0xC3, (byte) 0x28}).acceptance());
    }

    @Test
    void clientBuildLengthLimitIsEnforced() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 256, 4);
        Challenge challenge = verifier.open(PLAYER, CONNECTION);
        Response response = new Response(1, CONNECTION, challenge.nonce(), 1, "five5");

        assertEquals(Acceptance.MALFORMED, verifier.acceptWire(PLAYER,
                WireCodec.encodeResponse(response).getBytes(java.nio.charset.StandardCharsets.UTF_8)).acceptance());
        assertEquals(VerificationState.UNVERIFIED, verifier.state(PLAYER));
    }

    @Test
    void canonicalWireRoundTrip() throws Exception {
        Challenge challenge = new Challenge(PLAYER, CONNECTION, "nonce_123", 1_000);
        Response response = new Response(1, CONNECTION, challenge.nonce(), 1, "fabric-verifier-1.0.0");

        assertEquals("v1|" + CONNECTION + "|nonce_123", WireCodec.encodeChallenge(challenge));
        assertEquals(response, WireCodec.decodeResponse(
                WireCodec.encodeResponse(response).getBytes(java.nio.charset.StandardCharsets.UTF_8), 128));
    }

    @Test
    void nonCanonicalWireRejected() {
        assertThrows(WireCodec.WireFormatException.class, () -> WireCodec.decodeResponse(
                ("v1|01|" + CONNECTION + "|nonce_123|01|client").getBytes(), 128));
    }

    @Test
    void replayAndDuplicateResponsesRejected() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 128, 32);
        Challenge challenge = verifier.open(PLAYER, CONNECTION);
        Response response = new Response(1, CONNECTION, challenge.nonce(), 1, "client");

        assertEquals(Acceptance.ACCEPTED, verifier.accept(PLAYER, response).acceptance());
        assertEquals(Acceptance.DUPLICATE, verifier.accept(PLAYER, response).acceptance());
        assertEquals(Acceptance.REPLAY, verifier.accept(PLAYER,
                new Response(1, CONNECTION, challenge.nonce(), 0, "client")).acceptance());
    }

    @Test
    void expiredChallengeRejected() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 128, 32);
        Challenge challenge = verifier.open(PLAYER, CONNECTION);
        clock.advance(Duration.ofSeconds(11));

        VerificationResult result = verifier.accept(PLAYER,
                new Response(1, CONNECTION, challenge.nonce(), 1, "client"));

        assertEquals(Acceptance.EXPIRED, result.acceptance());
        assertEquals(VerificationState.EXPIRED, verifier.state(PLAYER));
    }

    @Test
    void nullResponseRejectedAndExactTtlExpires() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 128, 32);
        Challenge challenge = verifier.open(PLAYER, CONNECTION);

        assertEquals(Acceptance.MALFORMED, verifier.accept(PLAYER, null).acceptance());
        clock.advance(Duration.ofSeconds(10));
        assertEquals(Acceptance.EXPIRED, verifier.accept(PLAYER,
                new Response(1, CONNECTION, challenge.nonce(), 1, "client")).acceptance());
    }

    @Test
    void delayedQuitCannotCloseReplacementSession() {
        MutableClock clock = new MutableClock(1_000);
        ChallengeVerifier verifier = new ChallengeVerifier(clock, 10_000, 128, 32);
        verifier.open(PLAYER, CONNECTION);
        UUID replacement = UUID.randomUUID();
        Challenge challenge = verifier.open(PLAYER, replacement);

        verifier.close(PLAYER, CONNECTION);

        assertEquals(VerificationState.UNVERIFIED, verifier.state(PLAYER));
        assertEquals(Acceptance.ACCEPTED, verifier.accept(PLAYER,
                new Response(1, replacement, challenge.nonce(), 1, "client")).acceptance());
    }

    @Test
    void evidenceBufferIsBoundedAndRedactsNonce() {
        BoundedEvidenceBuffer buffer = new BoundedEvidenceBuffer(2);
        buffer.add(EvidenceRecord.of(PLAYER, "MALFORMED", "secret-nonce"));
        buffer.add(EvidenceRecord.of(PLAYER, "REPLAY", "another-secret"));
        buffer.add(EvidenceRecord.of(PLAYER, "EXPIRED", "third-secret"));

        assertEquals(2, buffer.snapshot().size());
        assertFalse(buffer.snapshot().getFirst().details().contains("secret"));
    }

    private static final class MutableClock implements Clock {
        private long now;
        private MutableClock(long now) { this.now = now; }
        @Override public long millis() { return now; }
        private void advance(Duration duration) { now += duration.toMillis(); }
    }
}
