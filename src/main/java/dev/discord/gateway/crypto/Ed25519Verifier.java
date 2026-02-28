package dev.discord.gateway.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Verifies Discord Ed25519 request signatures using Java's built-in EdDSA support (JDK 15+).
 * No external crypto libraries required — fully GraalVM native-image compatible.
 */
public class Ed25519Verifier {

    /** DER prefix for an Ed25519 public key encoded as X.509 SubjectPublicKeyInfo. */
    private static final byte[] ED25519_DER_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static final HexFormat HEX = HexFormat.of();

    private final PublicKey publicKey;

    public Ed25519Verifier(String hexPublicKey) {
        try {
            byte[] rawKey = HEX.parseHex(hexPublicKey);
            if (rawKey.length != 32) {
                throw new IllegalArgumentException(
                        "Ed25519 public key must be 32 bytes, got " + rawKey.length);
            }

            byte[] derEncoded = new byte[ED25519_DER_PREFIX.length + rawKey.length];
            System.arraycopy(ED25519_DER_PREFIX, 0, derEncoded, 0, ED25519_DER_PREFIX.length);
            System.arraycopy(rawKey, 0, derEncoded, ED25519_DER_PREFIX.length, rawKey.length);

            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(derEncoded));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", e);
        }
    }

    /**
     * Verifies a Discord interaction request signature.
     *
     * @param hexSignature hex-encoded Ed25519 signature from X-Signature-Ed25519 header
     * @param timestamp    raw timestamp string from X-Signature-Timestamp header
     * @param body         raw request body bytes
     * @return true if the signature is valid
     */
    public boolean verify(String hexSignature, String timestamp, byte[] body) {
        try {
            byte[] signatureBytes = HEX.parseHex(hexSignature);
            byte[] timestampBytes = timestamp.getBytes(StandardCharsets.UTF_8);

            byte[] message = new byte[timestampBytes.length + body.length];
            System.arraycopy(timestampBytes, 0, message, 0, timestampBytes.length);
            System.arraycopy(body, 0, message, timestampBytes.length, body.length);

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(message);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
