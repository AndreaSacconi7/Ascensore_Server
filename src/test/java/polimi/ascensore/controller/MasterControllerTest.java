package polimi.ascensore.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.MatchState;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.message.JoinGameResponse;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.message.MessageType;
import polimi.ascensore.network.message.PlayerInfoResponse;
import polimi.ascensore.network.message.WaitingRoomUpdate;
import polimi.ascensore.network.websocket.GameWebSocketHandler;
import polimi.ascensore.persistence.PlayerRepository;
import polimi.ascensore.auth.SupabaseAuthService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MasterControllerTest {

    private GameLoops loops;
    private MasterController controller;
    private GameWebSocketHandler sockets;
    private SupabaseAuthService authService;
    private PlayerRepository playerRepository;
    private final Map<String, MatchState> savedMatches = new HashMap<>();
    private final MatchStore store = new MatchStore() {
        @Override
        public void save(MatchState state) {
            savedMatches.put(state.id(), state);
        }

        @Override
        public void delete(String matchId) {
            savedMatches.remove(matchId);
        }

        @Override
        public List<MatchState> loadAll() {
            return List.copyOf(savedMatches.values());
        }
    };

    @BeforeEach
    void setUp() {
        loops = GameLoops.direct();
        sockets = mock(GameWebSocketHandler.class);
        authService = mock(SupabaseAuthService.class);
        playerRepository = mock(PlayerRepository.class);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sockets.isCurrentSession(anyString(), anyString())).thenReturn(true);
        controller = new MasterController(loops, new GameSettings(2, 10, 30), authService, playerRepository, store);
        controller.setSocketHandler(sockets);
    }

    @AfterEach
    void tearDown() {
        loops.shutdown();
    }

    ///// Commands from sessions that are not playing /////

    @Test
    void commandsFromASessionThatIsNotLoggedInAreIgnored() {
        assertDoesNotThrow(() -> controller.putCard(Seed.CUPS, 1, "s-anon"));
        assertDoesNotThrow(() -> controller.setBet(1, "s-anon"));
        assertDoesNotThrow(() -> controller.addPlayerToGame("s-anon", null));
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

    @Test
    void playerSharingTheirNicknameWithAnotherAccountMustChooseANewOne() {
        Player legacy = new Player("uid-old", "alice");
        when(authService.validateAndGetUserId("token")).thenReturn("uid-old");
        when(playerRepository.findBySupabaseUid("uid-old")).thenReturn(Optional.of(legacy));
        when(playerRepository.countByNicknameIgnoreCase("alice")).thenReturn(2L);
        session("s-1");

        controller.fetchPlayerInfo("s-1", "token", "");
        verifyNicknameRequired("s-1", PlayerInfoResponse.NICKNAME_MISSING);

        controller.fetchPlayerInfo("s-1", "token", "alice_2");
        assertEquals("alice_2", legacy.getNickname());
        verify(playerRepository).save(legacy);
        assertEquals(legacy, controller.getPlayerBySession("s-1"));
    }

    @Test
    void nicknameRefusedByTheDatabaseIsTaken() {
        newAccount("uid-new");
        when(playerRepository.save(any())).thenThrow(new DataIntegrityViolationException("player_nickname_lower_key"));
        session("s-1");

        controller.fetchPlayerInfo("s-1", "token", "alice");

        verifyNicknameRequired("s-1", PlayerInfoResponse.NICKNAME_TAKEN);
        assertNull(controller.getPlayerBySession("s-1"));
    }

    @Test
    void aSocketAlreadyLoggedInCannotLogInAgain() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        controller.fetchPlayerInfo("s-1", "token", "");

        verify(authService, never()).validateAndGetUserId(any());
        verify(sockets, never()).sendMessageToClient(any(), eq("s-1"));
    }

    @Test
    void loginsBeyondWhatTheDatabaseCanQueueAreTurnedAway() {
        // The database thread is stuck: logins pile up behind it
        List<Runnable> database = new ArrayList<>();
        loops.shutdown();
        loops = new GameLoops(Runnable::run, database::add);
        controller = new MasterController(loops, new GameSettings(2, 10, 30), authService, playerRepository, store);
        controller.setSocketHandler(sockets);
        for (int i = 0; i < MasterController.MAX_PENDING_LOGINS; i++) {
            controller.fetchPlayerInfo("s-" + i, "token", "");
        }
        verify(sockets, never()).closeSession(any(), any());

        controller.fetchPlayerInfo("s-late", "token", "");
        verify(sockets).closeSession("s-late", CloseStatus.SERVICE_OVERLOAD);
        assertEquals(MasterController.MAX_PENDING_LOGINS, database.size());

        // Once the database catches up there is room again
        database.remove(0).run();
        controller.fetchPlayerInfo("s-retry", "token", "");
        assertEquals(MasterController.MAX_PENDING_LOGINS, database.size());
        verify(sockets, never()).closeSession(eq("s-retry"), any());
    }

    @Test
    void loggingOutClosesTheSocket() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        controller.logout("s-1");

        verify(sockets).closeSession("s-1", CloseStatus.NORMAL);
        verify(sockets).removeSession("s-1");
    }

    @Test
    void loggingInOnAnotherDeviceClosesTheFirstOne() {
        Player alice = new Player("uid-alice", "alice");
        Map<String, Object> oldAttributes = session("s-phone");
        oldAttributes.put("PLAYER", alice);
        when(sockets.currentSessionOf("alice")).thenReturn("s-phone");
        when(authService.validateAndGetUserId("token")).thenReturn("uid-alice");
        when(playerRepository.findBySupabaseUid("uid-alice")).thenReturn(Optional.of(alice));
        session("s-laptop");

        controller.fetchPlayerInfo("s-laptop", "token", "");

        verify(sockets).sendMessageToClient(argThat(m -> m.getMessageType() == MessageType.SESSION_REPLACED), eq("s-phone"));
        verify(sockets).closeSession(eq("s-phone"), any());
        assertNull(controller.getPlayerBySession("s-phone"), "late commands from the old device are ignored");
        assertEquals(alice, controller.getPlayerBySession("s-laptop"));
    }

    ///// Matchmaking /////

    @Test
    void joiningTwiceDoesNotPutThePlayerInTwoMatches() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        controller.addPlayerToGame("s-1", null);
        controller.addPlayerToGame("s-1", null);

        verify(sockets, times(1)).sendMessageToClient(argThat(m -> m.getExecutable() instanceof JoinGameResponse), eq("s-1"));
    }

    @Test
    void secondPlayerStartsTheMatch() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));

        controller.addPlayerToGame("s-1", null);
        controller.addPlayerToGame("s-2", null);

        verify(sockets, atLeastOnce()).forwardUpdateToAll(
                argThat(m -> m.getMessageType() == MessageType.STARTING_GAME), any());
    }

    @Test
    void leavingAWaitingMatchFreesTheSeatWithoutATimer() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));
        loggedIn("s-3", new Player("uid-carol", "carol"));
        controller.addPlayerToGame("s-1", null);

        controller.handleConnectionClosed("s-1");
        assertFalse(controller.hasPendingDisconnect("uid-alice"));
        assertFalse(controller.checkIfPlayerInGame("uid-alice"));

        // The next two players get a clean match, which starts with just them
        controller.addPlayerToGame("s-2", null);
        controller.addPlayerToGame("s-3", null);
        verify(sockets, atLeastOnce()).forwardUpdateToAll(
                argThat(m -> m.getMessageType() == MessageType.STARTING_GAME),
                argThat(players -> players.size() == 2));
    }

    @Test
    void playersChooseTheMatchSizeAndSizesDoNotMix() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));
        loggedIn("s-3", new Player("uid-carol", "carol"));

        controller.addPlayerToGame("s-1", 3);
        controller.addPlayerToGame("s-2", 2);
        verify(sockets, never()).forwardUpdateToAll(argThat(m -> m.getMessageType() == MessageType.STARTING_GAME), any());

        controller.addPlayerToGame("s-3", 3);
        verify(sockets, never()).forwardUpdateToAll(argThat(m -> m.getMessageType() == MessageType.STARTING_GAME), any());

        loggedIn("s-4", new Player("uid-dave", "dave"));
        controller.addPlayerToGame("s-4", 3);
        verify(sockets).forwardUpdateToAll(argThat(m -> m.getMessageType() == MessageType.STARTING_GAME),
                argThat(players -> players.size() == 3));
    }

    @Test
    void invalidMatchSizeFallsBackToTheDefault() {
        loggedIn("s-1", new Player("uid-alice", "alice"));

        controller.addPlayerToGame("s-1", 7);

        verify(sockets).sendMessageToClient(argThat(m -> m.getExecutable() instanceof JoinGameResponse r
                && r.getPlayersPerMatch() == 2), eq("s-1"));
    }

    @Test
    void waitingPlayersSeeWhoJoinsAndLeaves() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));
        controller.addPlayerToGame("s-1", 4);
        controller.addPlayerToGame("s-2", 4);
        verify(sockets).forwardUpdateToAll(argThat(m -> m.getExecutable() instanceof WaitingRoomUpdate u
                && u.getPlayers().equals(List.of("alice", "bob")) && u.getPlayersPerMatch() == 4), any());

        controller.leaveGame("s-2");

        assertFalse(controller.checkIfPlayerInGame("uid-bob"));
        // Once when Alice joined alone, once when Bob left
        verify(sockets, times(2)).forwardUpdateToAll(argThat(m -> m.getExecutable() instanceof WaitingRoomUpdate u
                && u.getPlayers().equals(List.of("alice"))), any());
    }

    @Test
    void leavingATwoPlayerMatchEndsItForBoth() {
        startedMatch();

        controller.leaveGame("s-1");

        assertFalse(controller.checkIfPlayerInGame("uid-alice"));
        assertFalse(controller.checkIfPlayerInGame("uid-bob"), "the match ended, so Bob is free to play again");
        verify(sockets).forwardUpdateToAll(argThat(m -> m.getMessageType() == MessageType.END_GAME), any());
    }

    @Test
    void leavingABiggerMatchLetsTheOthersPlayOn() {
        loggedIn("s-1", new Player("uid-alice", "alice"));
        loggedIn("s-2", new Player("uid-bob", "bob"));
        loggedIn("s-3", new Player("uid-carol", "carol"));
        controller.addPlayerToGame("s-1", 3);
        controller.addPlayerToGame("s-2", 3);
        controller.addPlayerToGame("s-3", 3);

        controller.leaveGame("s-2");

        assertFalse(controller.checkIfPlayerInGame("uid-bob"));
        assertTrue(controller.checkIfPlayerInGame("uid-alice"));
        assertTrue(controller.checkIfPlayerInGame("uid-carol"));
        verify(sockets).forwardUpdateToAll(argThat(m -> m.getMessageType() == MessageType.PLAYER_EXIT_GAME), any());
        verify(sockets, never()).forwardUpdateToAll(argThat(m -> m.getMessageType() == MessageType.END_GAME), any());
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

    @Test
    void loggingInAgainWhileWaitingForAMatchGoesBackToTheMenu() {
        Player alice = new Player("uid-alice", "alice");
        loggedIn("s-phone", alice);
        controller.addPlayerToGame("s-phone", 3);
        when(sockets.currentSessionOf("alice")).thenReturn("s-phone");
        when(authService.validateAndGetUserId("token")).thenReturn("uid-alice");
        when(playerRepository.findBySupabaseUid("uid-alice")).thenReturn(Optional.of(alice));
        session("s-laptop");

        controller.fetchPlayerInfo("s-laptop", "token", "");

        assertFalse(controller.checkIfPlayerInGame("uid-alice"));
        verify(sockets).sendMessageToClient(argThat(m -> answer(m).isLogged() && !answer(m).isInMatch()),
                eq("s-laptop"));
    }

    @Test
    void unreachableDatabaseClosesTheSocketSoTheClientRetries() {
        when(authService.validateAndGetUserId("token")).thenReturn("uid-alice");
        when(playerRepository.findBySupabaseUid("uid-alice")).thenThrow(new RuntimeException("connection refused"));

        controller.fetchPlayerInfo("s-1", "token", "");

        verify(sockets).closeSession(eq("s-1"), any());
        verify(sockets, never()).sendMessageToClient(any(), eq("s-1"));
    }

    ///// Restart /////

    @Test
    void startedMatchesAreSavedAndMatchmakingIsNot() {
        loggedIn("s-3", new Player("uid-carol", "carol"));
        controller.addPlayerToGame("s-3", 3);
        assertTrue(savedMatches.isEmpty(), "a match waiting for players is not saved");

        startedMatch();

        assertEquals(1, savedMatches.size());
    }

    @Test
    void afterARestartPlayersFindTheirMatchAndPickUpWhereTheyLeft() {
        Player alice = startedMatch();
        loops.shutdown();

        // A new server process, same database
        clearInvocations(sockets);
        loops = GameLoops.direct();
        controller = new MasterController(loops, new GameSettings(2, 10, 30), authService, playerRepository, store);
        controller.setSocketHandler(sockets);
        controller.restoreMatches();

        assertTrue(controller.checkIfPlayerInGame("uid-alice"));
        assertTrue(controller.checkIfPlayerInGame("uid-bob"));
        assertTrue(controller.hasPendingDisconnect("uid-alice"), "players get the usual window to come back");

        when(authService.validateAndGetUserId("token")).thenReturn("uid-alice");
        when(playerRepository.findBySupabaseUid("uid-alice")).thenReturn(Optional.of(alice));
        session("s-1-new");
        controller.fetchPlayerInfo("s-1-new", "token", "");

        assertFalse(controller.hasPendingDisconnect("uid-alice"));
        verify(sockets).sendMessageToClient(argThat(m -> answer(m).isLogged() && answer(m).isInMatch()),
                eq("s-1-new"));
        verify(sockets).forwardUpdateToSingleClient(
                argThat(m -> m.getMessageType() == MessageType.INFO_AFTER_RECONNECTION), eq("alice"));
    }

    @Test
    void theEndOfARestoredMatchClearsItsSnapshot() {
        startedMatch();
        controller = new MasterController(loops, new GameSettings(2, 10, 30), authService, playerRepository, store);
        controller.setSocketHandler(sockets);
        controller.restoreMatches();
        loggedIn("s-2", new Player("uid-bob", "bob"));

        controller.leaveGame("s-2");

        assertTrue(savedMatches.isEmpty());
        assertFalse(controller.checkIfPlayerInGame("uid-alice"));
    }

    // Alice (s-1) and Bob (s-2) in a started match
    private Player startedMatch() {
        Player alice = new Player("uid-alice", "alice");
        loggedIn("s-1", alice);
        loggedIn("s-2", new Player("uid-bob", "bob"));
        controller.addPlayerToGame("s-1", null);
        controller.addPlayerToGame("s-2", null);
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
