package polimi.ascensore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real server (in-memory database) and talks to it over WebSockets, with access tokens signed
 * by a test key published in a local JWKS file, the way Supabase publishes its keys.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:integration",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class GameServerIntegrationTest {

    private static final KeyPair SIGNING_KEY = newSigningKey();

    @LocalServerPort
    private int port;

    private final List<Client> clients = new ArrayList<>();

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) throws Exception {
        ECPublicKey key = (ECPublicKey) SIGNING_KEY.getPublic();
        String jwks = """
                {"keys":[{"kty":"EC","crv":"P-256","kid":"test-key","alg":"ES256","x":"%s","y":"%s"}]}
                """.formatted(coordinate(key.getW().getAffineX()), coordinate(key.getW().getAffineY()));
        Path file = Files.createTempFile("jwks", ".json");
        Files.writeString(file, jwks);
        file.toFile().deleteOnExit();
        registry.add("supabase.jwks-url", () -> file.toUri().toString());
    }

    @AfterEach
    void closeClients() {
        clients.forEach(Client::abort);
    }

    @Test
    void newPlayerChoosesAUniqueNickname() throws Exception {
        Client alice = connect();
        alice.send(playerInfoRequest("uid-alice", ""));
        JsonObject missing = alice.await("PLAYER_INFO_RESPONSE");
        assertTrue(missing.get("needsNickname").getAsBoolean());

        alice.send(playerInfoRequest("uid-alice", "alice"));
        assertTrue(alice.await("PLAYER_INFO_RESPONSE").get("isLogged").getAsBoolean());

        Client bob = connect();
        bob.send(playerInfoRequest("uid-bob", "Alice"));
        assertEquals("NICKNAME_TAKEN", bob.await("PLAYER_INFO_RESPONSE").get("error").getAsString());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        Client client = connect();
        client.send("{\"commandType\":\"PLAYER_INFO_REQUEST\",\"executable\":{\"token\":\"not-a-jwt\",\"nickname\":\"x\"}}");

        JsonObject answer = client.await("PLAYER_INFO_RESPONSE");
        assertFalse(answer.get("isLogged").getAsBoolean());
        assertEquals("INVALID_TOKEN", answer.get("error").getAsString());
    }

    @Test
    void badCommandsDoNotStopTheServer() throws Exception {
        Client client = connect();
        client.send("not json at all");
        client.send("{\"commandType\":\"NOT_A_COMMAND\",\"executable\":{}}");
        client.send("{\"commandType\":\"PUT_CARD\",\"executable\":{\"seed\":\"CUPS\",\"value\":1}}");

        client.send(playerInfoRequest("uid-carol", "carol"));
        assertTrue(client.await("PLAYER_INFO_RESPONSE").get("isLogged").getAsBoolean());
    }

    @Test
    void twoPlayersStartAMatchAndOneReconnects() throws Exception {
        Client alice = loggedIn("uid-alice2", "alice2");
        Client bob = loggedIn("uid-bob2", "bob2");

        alice.send(command("JOIN_GAME_REQUEST", "{}"));
        bob.send(command("JOIN_GAME_REQUEST", "{}"));
        List<String> order = new ArrayList<>();
        alice.await("STARTING_GAME").getAsJsonArray("connectedPlayers").forEach(p -> order.add(p.getAsString()));
        assertEquals(1, alice.await("HAND_UPDATE").getAsJsonArray("cards").size());
        assertEquals(1, bob.await("HAND_UPDATE").getAsJsonArray("cards").size());

        // The first player in order bets
        Client first = order.get(0).equals("alice2") ? alice : bob;
        first.send(command("SET_BET", "{\"bet\":0}"));
        assertEquals(0, bob.await("SETTED_BET").get("bet").getAsInt());

        // Bob's connection drops, and he comes back on a new socket with the same account
        bob.abort();
        Client bobAgain = connect();
        bobAgain.send(playerInfoRequest("uid-bob2", ""));
        JsonObject welcomeBack = bobAgain.await("PLAYER_INFO_RESPONSE");
        assertTrue(welcomeBack.get("isLogged").getAsBoolean());
        assertTrue(welcomeBack.get("inMatch").getAsBoolean());
        assertEquals(order.size(), bobAgain.await("STARTING_GAME").getAsJsonArray("connectedPlayers").size());
        assertEquals(1, bobAgain.await("HAND_UPDATE").getAsJsonArray("cards").size());
        JsonObject info = bobAgain.await("INFO_AFTER_RECONNECTION");
        assertEquals(1, info.get("set").getAsInt());
        assertEquals(0, info.getAsJsonObject("bets").get(order.get(0)).getAsInt());
    }

    ///// Helpers /////

    private Client loggedIn(String uid, String nickname) throws Exception {
        Client client = connect();
        client.send(playerInfoRequest(uid, nickname));
        assertTrue(client.await("PLAYER_INFO_RESPONSE").get("isLogged").getAsBoolean());
        return client;
    }

    private Client connect() throws Exception {
        Client client = new Client();
        client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), client)
                .get(5, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    private static String playerInfoRequest(String uid, String nickname) {
        String token = Jwts.builder()
                .setHeaderParam("kid", "test-key")
                .setSubject(uid)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(SIGNING_KEY.getPrivate(), SignatureAlgorithm.ES256)
                .compact();
        return command("PLAYER_INFO_REQUEST", "{\"token\":\"" + token + "\",\"nickname\":\"" + nickname + "\"}");
    }

    private static String command(String type, String executable) {
        return "{\"commandType\":\"" + type + "\",\"executable\":" + executable + "}";
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

    /** A WebSocket client that queues the server's messages. */
    private static final class Client implements WebSocket.Listener {
        private final BlockingQueue<JsonObject> inbox = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        void send(String text) {
            socket.sendText(text, true).join();
        }

        // Next message of this type; earlier messages of other types are skipped
        JsonObject await(String messageType) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                JsonObject message = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && message.get("messageType").getAsString().equals(messageType)) {
                    JsonObject executable = message.getAsJsonObject("executable");
                    return executable == null ? new JsonObject() : executable;
                }
            }
            throw new AssertionError("No " + messageType + " received");
        }

        void abort() {
            socket.abort();
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                inbox.add(JsonParser.parseString(partial.toString()).getAsJsonObject());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }
}
