package vn.heomc.anticheat.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Canonical v1 play-phase wire contract. */
public final class WireCodec {
    private static final String NONCE = "[A-Za-z0-9_-]+";
    private static final String BUILD = "[A-Za-z0-9._-]{1,128}";

    private WireCodec() {}

    public static String encodeChallenge(Challenge challenge) {
        return "v1|" + canonicalUuid(challenge.connectionId()) + "|" + requireNonce(challenge.nonce());
    }

    public static String encodeResponse(Response response) {
        return "v1|" + response.protocol() + "|" + canonicalUuid(response.connectionId()) + "|"
                + requireNonce(response.nonce()) + "|" + response.sequence() + "|"
                + requireBuild(response.clientBuildId());
    }

    public static Response decodeResponse(byte[] wire, int maxWireBytes) throws WireFormatException {
        return decodeResponse(wire, maxWireBytes, 128);
    }

    public static Response decodeResponse(byte[] wire, int maxWireBytes, int maxClientBuildLength)
            throws WireFormatException {
        if (wire == null || wire.length == 0 || wire.length > maxWireBytes) {
            throw new WireFormatException("payload");
        }
        if (maxClientBuildLength < 1) throw new WireFormatException("build-limit");
        final String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(wire)).toString();
        } catch (CharacterCodingException ex) {
            throw new WireFormatException("utf8");
        }
        String[] fields = decoded.split("\\|", -1);
        if (fields.length != 6 || !fields[0].equals("v1")) throw new WireFormatException("schema");
        try {
            int protocol = Integer.parseInt(fields[1]);
            UUID connectionId = parseCanonicalUuid(fields[2]);
            String nonce = requireNonce(fields[3]);
            long sequence = parseCanonicalLong(fields[4]);
            String build = requireBuild(fields[5], maxClientBuildLength);
            return new Response(protocol, connectionId, nonce, sequence, build);
        } catch (IllegalArgumentException ex) {
            throw new WireFormatException("field");
        }
    }

    private static UUID parseCanonicalUuid(String value) {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) throw new IllegalArgumentException("uuid");
        return uuid;
    }

    private static long parseCanonicalLong(String value) {
        long parsed = Long.parseLong(value);
        if (!Long.toString(parsed).equals(value)) throw new IllegalArgumentException("sequence");
        return parsed;
    }

    private static String canonicalUuid(UUID value) {
        if (value == null) throw new IllegalArgumentException("uuid");
        return value.toString();
    }

    private static String requireNonce(String value) {
        if (value == null || !value.matches(NONCE)) throw new IllegalArgumentException("nonce");
        return value;
    }

    private static String requireBuild(String value) {
        return requireBuild(value, 128);
    }

    private static String requireBuild(String value, int maxLength) {
        if (value == null || value.length() > maxLength || !value.matches(BUILD)) {
            throw new IllegalArgumentException("build");
        }
        return value;
    }

    public static final class WireFormatException extends Exception {
        public WireFormatException(String reason) { super(reason); }
    }
}
