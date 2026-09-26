package polimi.ascensore.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.newserver.MySocketHandler;
import polimi.ascensore.network.newserver.PlayerRepository;
import polimi.ascensore.network.newserver.SupabaseAuthService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MasterControllerTest {

    private CommandLoop commandLoop;
    private MasterController controller;
    private MySocketHandler sockets;
    private SupabaseAuthService authService;
    private PlayerRepository playerRepository;

    @BeforeEach
    void setUp() {
        commandLoop = new CommandLoop();
        controller = new MasterController(commandLoop);
        sockets = mock(MySocketHandler.class);
        authService = mock(SupabaseAuthService.class);
        playerRepository = mock(PlayerRepository.class);
        controller.setSocketHandler(sockets);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "playerRepository", playerRepository);
    }

    @AfterEach
    void tearDown() {
        commandLoop.shutdown();
    }

    @Test
    void commandsFromASessionThatIsNotLoggedInAreIgnored() {
        // No session registered: getSession returns null, so there is no player behind the command
        assertDoesNotThrow(() -> controller.putCard(Seed.CUPS, 1, "s-anon"));
        assertDoesNotThrow(() -> controller.setBet(1, "s-anon"));
        assertDoesNotThrow(() -> controller.addPlayerToGame("s-anon"));
        assertDoesNotThrow(() -> controller.logout("s-anon"));
        assertDoesNotThrow(() -> controller.handleConnectionClosed("s-anon"));
    }

    @Test
    void cardFromAPlayerWhoIsNotInAGameIsIgnored() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        assertDoesNotThrow(() -> controller.putCard(Seed.CUPS, 1, "s-1"));
    }

    @Test
    void invalidTokenGetsANegativeAnswer() {
        when(authService.validateAndGetUserId("bad-token")).thenReturn(null);

        controller.fetchPlayerInfo("s-1", "bad-token", "alice");

        verify(sockets).sendMessageToClient(argThat(MasterControllerTest::isRejectedLogin), eq("s-1"));
    }

    @Test
    void newPlayerWithoutANicknameGetsANegativeAnswer() {
        when(authService.validateAndGetUserId("token")).thenReturn("uid-new");
        when(playerRepository.findBySupabaseUid("uid-new")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> controller.fetchPlayerInfo("s-1", "token", null));

        verify(sockets).sendMessageToClient(argThat(MasterControllerTest::isRejectedLogin), eq("s-1"));
        verify(playerRepository, never()).save(any());
    }

    @Test
    void joiningTwiceDoesNotPutThePlayerInTwoGames() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        controller.addPlayerToGame("s-1");
        controller.addPlayerToGame("s-1");

        verify(sockets, times(1)).sendMessageToClient(any(Message.class), eq("s-1"));
    }

    @Test
    void droppedConnectionOpensAReconnectionWindow() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        controller.addPlayerToGame("s-1");
        when(sockets.isCurrentSession("alice", "s-1")).thenReturn(true);

        controller.handleConnectionClosed("s-1");

        assertTrue(controller.hasPendingDisconnect("uid-alice"));
        verify(sockets).removeSession("s-1");
    }

    @Test
    void oldSocketClosingAfterAReconnectionDoesNotStartTheTimer() {
        loggedIn("s-old", new Player("uid-alice", "alice"));
        controller.addPlayerToGame("s-old");
        // Alice already reconnected on a new socket, so s-old is no longer her current session
        when(sockets.isCurrentSession("alice", "s-old")).thenReturn(false);

        controller.handleConnectionClosed("s-old");

        assertFalse(controller.hasPendingDisconnect("uid-alice"));
        verify(sockets).removeSession("s-old");
    }

    private void loggedIn(String sessionId, Player player) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("PLAYER", player);
        when(session.getAttributes()).thenReturn(attributes);
        when(sockets.getSession(sessionId)).thenReturn(session);
        when(sockets.isCurrentSession(anyString(), anyString())).thenReturn(true);
    }

    private static boolean isRejectedLogin(Message message) {
        return message.toJson().contains("\"isLogged\":false");
    }
}
