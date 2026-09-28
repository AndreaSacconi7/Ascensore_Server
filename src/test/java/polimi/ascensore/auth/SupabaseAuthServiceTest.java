package polimi.ascensore.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SupabaseAuthServiceTest {

    private static final KeyPair PROJECT_KEY = newSigningKey();

    @Test
    void tokenSignedByTheProjectGivesItsUser() throws Exception {
        SupabaseAuthService auth = authWithProjectKey();

        assertEquals("uid-alice", auth.validateAndGetUserId(token("test-key", PROJECT_KEY, 60_000)));
        assertEquals("uid-alice", auth.validateAndGetUserId("Bearer " + token("test-key", PROJECT_KEY, 60_000)));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        assertNull(authWithProjectKey().validateAndGetUserId(token("test-key", PROJECT_KEY, -60_000)));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        assertNull(authWithProjectKey().validateAndGetUserId(token("test-key", newSigningKey(), 60_000)));
    }

    @Test
    void tokenFromAnUnknownKeyIsRejected() throws Exception {
        assertNull(authWithProjectKey().validateAndGetUserId(token("other-key", PROJECT_KEY, 60_000)));
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        String unsigned = Jwts.builder().header().keyId("test-key").and().subject("uid-alice").compact();

        assertNull(authWithProjectKey().validateAndGetUserId(unsigned));
    }

    @Test
    void tokenSignedWithASharedSecretIsRejected() throws Exception {
        // HS256 under the project's key id: the public key must never be used as an HMAC secret
        String hmac = Jwts.builder()
                .header().keyId("test-key").and()
                .subject("uid-alice")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(new byte[32]), Jwts.SIG.HS256)
                .compact();

        assertNull(authWithProjectKey().validateAndGetUserId(hmac));
    }

    @Test
    void anUnresponsiveKeyServerDoesNotHangLogin() throws Exception {
        // Accepts connections and never answers
        try (ServerSocket silent = new ServerSocket(0)) {
            List<Socket> held = new ArrayList<>();
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        held.add(silent.accept());
                    }
                } catch (Exception ignored) {
                    // closed at the end of the test
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            SupabaseAuthService auth = new SupabaseAuthService("http://127.0.0.1:" + silent.getLocalPort() + "/jwks");

            assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
                auth.loadKeys();
                assertNull(auth.validateAndGetUserId("eyJhbGciOiJFUzI1NiIsImtpZCI6Im5ldyJ9.e30.c2ln"));
            });
        }
    }

    // Publishes PROJECT_KEY as "test-key" in a local JWKS file, the way Supabase publishes its keys
    private static SupabaseAuthService authWithProjectKey() throws Exception {
        ECPublicKey key = (ECPublicKey) PROJECT_KEY.getPublic();
        String jwks = """
                {"keys":[{"kty":"EC","crv":"P-256","kid":"test-key","alg":"ES256","x":"%s","y":"%s"}]}
                """.formatted(coordinate(key.getW().getAffineX()), coordinate(key.getW().getAffineY()));
        Path file = Files.createTempFile("jwks", ".json");
        Files.writeString(file, jwks);
        file.toFile().deleteOnExit();
        SupabaseAuthService auth = new SupabaseAuthService(file.toUri().toString());
        auth.loadKeys();
        return auth;
    }

    private static String token(String keyId, KeyPair signer, long expiresInMillis) {
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject("uid-alice")
                .expiration(new Date(System.currentTimeMillis() + expiresInMillis))
                .signWith(signer.getPrivate(), Jwts.SIG.ES256)
                .compact();
    }

    private static KeyPair newSigningKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // JWK coordinates are unsigned, big-endian, 32 bytes for P-256, base64url without padding
    private static String coordinate(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] fixed = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, fixed, 32 - length, length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fixed);
    }
}
