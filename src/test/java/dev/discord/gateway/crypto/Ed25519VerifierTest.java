package dev.discord.gateway.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ed25519VerifierTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void validSignatureReturnsTrue() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Ed25519Verifier verifier = verifierFrom(keyPair);

        String timestamp = "1700000000";
        byte[] body = "{\"type\":2,\"id\":\"abc\"}".getBytes();
        String signature = sign(keyPair, timestamp, body);

        assertThat(verifier.verify(signature, timestamp, body)).isTrue();
    }

    @Test
    void invalidSignatureReturnsFalse() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Ed25519Verifier verifier = verifierFrom(keyPair);

        String bogusSignature = "aa".repeat(64); // 64 bytes, wrong value
        assertThat(verifier.verify(bogusSignature, "1700000000", "{}".getBytes())).isFalse();
    }

    @Test
    void tamperedBodyReturnsFalse() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Ed25519Verifier verifier = verifierFrom(keyPair);

        String timestamp = "1700000000";
        byte[] originalBody = "{\"type\":2}".getBytes();
        String signature = sign(keyPair, timestamp, originalBody);

        byte[] tamperedBody = "{\"type\":3}".getBytes();
        assertThat(verifier.verify(signature, timestamp, tamperedBody)).isFalse();
    }

    @Test
    void tamperedTimestampReturnsFalse() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Ed25519Verifier verifier = verifierFrom(keyPair);

        String timestamp = "1700000000";
        byte[] body = "{\"type\":2}".getBytes();
        String signature = sign(keyPair, timestamp, body);

        assertThat(verifier.verify(signature, "9999999999", body)).isFalse();
    }

    @Test
    void malformedHexSignatureReturnsFalse() throws Exception {
        KeyPair keyPair = generateKeyPair();
        Ed25519Verifier verifier = verifierFrom(keyPair);

        assertThat(verifier.verify("not-hex", "123", "{}".getBytes())).isFalse();
    }

    @Test
    void invalidPublicKeyThrows() {
        assertThatThrownBy(() -> new Ed25519Verifier("deadbeef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    // ---- helpers ----

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        return kpg.generateKeyPair();
    }

    private static Ed25519Verifier verifierFrom(KeyPair keyPair) {
        byte[] x509Encoded = keyPair.getPublic().getEncoded();
        // raw 32-byte key is the last 32 bytes of the X.509 encoding
        byte[] rawPublicKey = new byte[32];
        System.arraycopy(x509Encoded, x509Encoded.length - 32, rawPublicKey, 0, 32);
        return new Ed25519Verifier(HEX.formatHex(rawPublicKey));
    }

    private static String sign(KeyPair keyPair, String timestamp, byte[] body) throws Exception {
        byte[] tsBytes = timestamp.getBytes();
        byte[] message = new byte[tsBytes.length + body.length];
        System.arraycopy(tsBytes, 0, message, 0, tsBytes.length);
        System.arraycopy(body, 0, message, tsBytes.length, body.length);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        return HEX.formatHex(signer.sign());
    }
}
