package polimi.ascensore.auth;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SupabaseAuthServiceTest {

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
}
