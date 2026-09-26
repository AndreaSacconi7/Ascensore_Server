package polimi.ascensore.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.message.JoinGameResponse;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.message.PlayerInfoResponse;
import polimi.ascensore.network.websocket.GameWebSocketHandler;
import polimi.ascensore.persistence.PlayerRepository;
import polimi.ascensore.auth.SupabaseAuthService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MasterControllerTest {

    private CommandLoop commandLoop;
    private MasterController controller;
    private GameWebSocketHandler sockets;
    private SupabaseAuthService authService;
    private PlayerRepository playerRepository;

    @BeforeEach
    void setUp() {
        commandLoop = new CommandLoop();
        sockets = mock(GameWebSocketHandler.class);
        authService = mock(SupabaseAuthService.class);
        playerRepository = mock(PlayerRepository.class);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sockets.isCurrentSession(anyString(), anyString())).thenReturn(true);
        controller = new MasterController(commandLoop, new GameSettings(2, 10), authService, playerRepository);
        controller.setSocketHandler(sockets);
    }

    @AfterEach
    void tearDown() {
        commandLoop.shutdown();
    }

    ///// Commands from sessions that are not playing /////

    @Test
    void commandsFromASessionThatIsNotLoggedInAreIgnored() {
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

    ///// Login and nicknames /////

    @Test
    void invalidTokenIsRejected() {
        when(authService.validateAndGetUserId("bad-token")).thenReturn(null);

        controller.fetchPlayerInfo("s-1", "bad-token", "alice");

        verify(sockets).sendMessageToClient(argThat(m -> PlayerInfoResponse.INVALID_TOKEN.equals(answer(m).getError())
                && !answer(m).isLogged()), eq("s-1"));
    }

    @Test
    void newPlayerIsAskedForANickname() {
        newAccount("uid-new");

        controller.fetchPlayerInfo("s-1", "token", "");

        verifyNicknameRequired("s-1", PlayerInfoResponse.NICKNAME_MISSING);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void invalidNicknameIsRefused() {
        newAccount("uid-new");

        controller.fetchPlayerInfo("s-1", "token", "alice@example.com");

        verifyNicknameRequired("s-1", PlayerInfoResponse.NICKNAME_INVALID);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void takenNicknameIsRefused() {
        newAccount("uid-new");
        when(playerRepository.existsByNicknameIgnoreCase("Alice")).thenReturn(true);

        controller.fetchPlayerInfo("s-1", "token", "Alice");

        verifyNicknameRequired("s-1", PlayerInfoResponse.NICKNAME_TAKEN);
    }

    @Test
    void validNicknameCreatesThePlayerAndLogsIn() {
        newAccount("uid-new");
        session("s-1");

        controller.fetchPlayerInfo("s-1", "token", "alice_99");

        verify(playerRepository).save(argThat(p -> p.getNickname().equals("alice_99")));
        verify(sockets).sendMessageToClient(argThat(m -> answer(m).isLogged()
                && answer(m).getNickname().equals("alice_99")), eq("s-1"));
        assertEquals("alice_99", controller.getPlayerBySession("s-1").getNickname());
    }

    @Test
    void playerWhoseNicknameIsAnEmailMustChooseANewOne() {
        Player legacy = new Player("uid-old", "alice@example.com");
        when(authService.validateAndGetUserId("token")).thenReturn("uid-old");
        when(playerRepository.findBySupabaseUid("uid-old")).thenReturn(Optional.of(legacy));
        session("s-1");

        controller.fetchPlayerInfo("s-1", "token", "");
        verifyNicknameRequired("s-1", PlayerInfoResponse.NICKNAME_MISSING);

        controller.fetchPlayerInfo("s-1", "token", "alice");
        assertEquals("alice", legacy.getNickname());
        verify(playerRepository).save(legacy);
    }

    ///// Matchmaking /////

    @Test
    void joiningTwiceDoesNotPutThePlayerInTwoMatches() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        controller.addPlayerToGame("s-1");
        controller.addPlayerToGame("s-1");

        verify(sockets, times(1)).sendMessageToClient(argThat(m -> m.getExecutable() instanceof JoinGameResponse), eq("s-1"));
    }

    @Test
    void secondPlayerStartsTheMatch() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));

        controller.addPlayerToGame("s-1");
        controller.addPlayerToGame("s-2");

        verify(sockets, atLeastOnce()).forwardUpdateToAll(
                argThat(m -> m.getMessageType() == polimi.ascensore.network.message.MessageType.STARTING_GAME), any());
    }

    @Test
    void leavingAWaitingMatchFreesTheSeatWithoutATimer() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));
        loggedIn("s-3", new Player("uid-carol", "carol"));
        controller.addPlayerToGame("s-1");

        controller.handleConnectionClosed("s-1");
        assertFalse(controller.hasPendingDisconnect("uid-alice"));
        assertFalse(controller.checkIfPlayerInGame("uid-alice"));

        // The next two players get a clean match, which starts with just them
        controller.addPlayerToGame("s-2");
        controller.addPlayerToGame("s-3");
        verify(sockets, atLeastOnce()).forwardUpdateToAll(
                argThat(m -> m.getMessageType() == polimi.ascensore.network.message.MessageType.STARTING_GAME),
                argThat(players -> players.size() == 2));
    }

    ///// Disconnection during a match /////

    @Test
    void droppedConnectionDuringAMatchOpensAReconnectionWindow() {
        startedMatch();

        controller.handleConnectionClosed("s-1");

        assertTrue(controller.hasPendingDisconnect("uid-alice"));
        assertTrue(controller.checkIfPlayerInGame("uid-alice"));
        verify(sockets).removeSession("s-1");
    }

    @Test
    void oldSocketClosingAfterAReconnectionDoesNotStartTheTimer() {
        startedMatch();
        // Alice already reconnected on a new socket, so s-1 is no longer her current session
        when(sockets.isCurrentSession("alice", "s-1")).thenReturn(false);

        controller.handleConnectionClosed("s-1");

        assertFalse(controller.hasPendingDisconnect("uid-alice"));
        verify(sockets).removeSession("s-1");
    }

    @Test
    void reconnectingClosesTheWindow() {
        Player alice = startedMatch();
        controller.handleConnectionClosed("s-1");
        when(authService.validateAndGetUserId("token")).thenReturn("uid-alice");
        when(playerRepository.findBySupabaseUid("uid-alice")).thenReturn(Optional.of(alice));
        session("s-1b");

        controller.fetchPlayerInfo("s-1b", "token", "");

        assertFalse(controller.hasPendingDisconnect("uid-alice"));
        assertTrue(controller.checkIfPlayerInGame("uid-alice"));
    }

    // Alice (s-1) and Bob (s-2) in a started match
    private Player startedMatch() {
        Player alice = new Player("uid-alice", "alice");
        loggedIn("s-1", alice);
        loggedIn("s-2", new Player("uid-bob", "bob"));
        controller.addPlayerToGame("s-1");
        controller.addPlayerToGame("s-2");
        return alice;
    }

    private void newAccount(String uid) {
        when(authService.validateAndGetUserId("token")).thenReturn(uid);
        when(playerRepository.findBySupabaseUid(uid)).thenReturn(Optional.empty());
    }

    private Map<String, Object> session(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);
        when(sockets.getSession(sessionId)).thenReturn(session);
        return attributes;
    }

    private void loggedIn(String sessionId, Player player) {
        session(sessionId).put("PLAYER", player);
    }

    private void verifyNicknameRequired(String sessionId, String error) {
        verify(sockets).sendMessageToClient(argThat(m -> answer(m).needsNickname()
                && error.equals(answer(m).getError())), eq(sessionId));
    }

    private static PlayerInfoResponse answer(Message message) {
        return message.getExecutable() instanceof PlayerInfoResponse r ? r : PlayerInfoResponse.rejected("NOT_AN_ANSWER");
    }
}
