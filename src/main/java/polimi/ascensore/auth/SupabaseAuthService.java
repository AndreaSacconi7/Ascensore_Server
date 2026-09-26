package polimi.ascensore.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Verifies Supabase access tokens (ES256 JWTs) against the project's public signing keys, fetched from
 * its JWKS endpoint. The server never trusts a player id sent by the client: it takes it from the token.
 */
@Service
public class SupabaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthService.class);

    // A token signed with an unknown key triggers a refetch (key rotation), at most this often
    private static final long MIN_REFRESH_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(FETCH_TIMEOUT).build();

    private final String jwksUrl;

    // Key id (kid) -> public key
    private volatile Map<String, PublicKey> keys = Map.of();

    private long lastFetch = System.nanoTime() - MIN_REFRESH_INTERVAL_NANOS;

    public SupabaseAuthService(@Value("${supabase.jwks-url}") String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    // Loads the keys at startup so the first login does not wait for them
    @PostConstruct
    void loadKeys() {
        refreshKeys();
    }

    /**
     * @return the Supabase user id (the token's subject), or null if the token is missing, expired,
     * or not signed by one of the project's keys
     */
    public String validateAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String cleanToken = token.replace("\"", "").trim();
        if (cleanToken.startsWith("Bearer ")) {
            cleanToken = cleanToken.substring("Bearer ".length()).trim();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        public Key resolveSigningKey(JwsHeader header, Claims claims) {
                            return keyFor(header.getKeyId());
                        }
                    })
                    .build()
                    .parseClaimsJws(cleanToken)
                    .getBody();
            return claims.getSubject();
        } catch (RuntimeException e) {
            log.info("Token rejected: {}", e.getMessage());
            return null;
        }
    }

    private PublicKey keyFor(String keyId) {
        PublicKey key = keys.get(keyId);
        if (key == null) {
            refreshKeys();
            key = keys.get(keyId);
        }
        if (key == null) {
            throw new IllegalArgumentException("Unknown signing key " + keyId);
        }
        return key;
    }

    private synchronized void refreshKeys() {
        if (System.nanoTime() - lastFetch < MIN_REFRESH_INTERVAL_NANOS) {
            return;
        }
        lastFetch = System.nanoTime();
        try {
            keys = fetchKeys();
            log.info("Loaded {} Supabase signing key(s)", keys.size());
        } catch (Exception e) {
            log.error("Could not load Supabase signing keys from {}: {}", jwksUrl, e.getMessage());
        }
    }

    private Map<String, PublicKey> fetchKeys() throws Exception {
        JsonNode root = new ObjectMapper().readTree(readJwks());
        Map<String, PublicKey> fetched = new HashMap<>();
        for (JsonNode key : root.get("keys")) {
            if ("EC".equals(key.path("kty").asText()) && "P-256".equals(key.path("crv").asText())) {
                fetched.put(key.path("kid").asText(), ecPublicKey(key.get("x").asText(), key.get("y").asText()));
            }
        }
        return Map.copyOf(fetched);
    }

    /**
     * Downloads the key set with connect and read timeouts: the fetch can run on the command loop (first
     * login, key rotation), and an unresponsive endpoint must not stall every match.
     */
    private String readJwks() throws Exception {
        URI uri = URI.create(jwksUrl);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            // Local key file (tests)
            try (var in = uri.toURL().openStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri).timeout(FETCH_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    // Builds a P-256 public key from the JWK's base64url-encoded curve point
    private static PublicKey ecPublicKey(String xBase64, String yBase64) throws Exception {
        BigInteger x = new BigInteger(1, Base64.getUrlDecoder().decode(xBase64));
        BigInteger y = new BigInteger(1, Base64.getUrlDecoder().decode(yBase64));

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);

        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve));
    }
}
