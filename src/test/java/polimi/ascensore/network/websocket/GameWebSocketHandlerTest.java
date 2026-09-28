package polimi.ascensore.network.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.GamePlayer;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.message.MessageType;
import polimi.ascensore.network.message.PlayerInfoResponse;
import polimi.ascensore.network.websocket.CommandDispatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameWebSocketHandlerTest {

    private CommandDispatcher commandDispatcher;
    private GameWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        commandDispatcher = mock(CommandDispatcher.class);
        handler = new GameWebSocketHandler(commandDispatcher, 30);
    }

    @Test
    void broadcastSkipsPlayersWithoutASessionAndReachesTheOthers() throws Exception {
        WebSocketSession alice = openSession("s-alice");
        handler.afterConnectionEstablished(alice);
        // Bob is inside his reconnection window: his old session is gone
        List<GamePlayer> players = List.of(
                new GamePlayer(new Player("uid-bob", "bob"), "s-bob-closed"),
                new GamePlayer(new Player("uid-alice", "alice"), "s-alice"));

        assertDoesNotThrow(() -> handler.forwardUpdateToAll(anyMessage(), players));

        verify(alice).sendMessage(any());
    }

    @Test
    void failedSendDoesNotAbortTheBroadcast() throws Exception {
        WebSocketSession broken = openSession("s-broken");
        doThrow(new IOException("broken pipe")).when(broken).sendMessage(any());
        WebSocketSession alice = openSession("s-alice");
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(alice);
        List<GamePlayer> players = List.of(
                new GamePlayer(new Player("uid-bob", "bob"), "s-broken"),
                new GamePlayer(new Player("uid-alice", "alice"), "s-alice"));

        assertDoesNotThrow(() -> handler.forwardUpdateToAll(anyMessage(), players));

        verify(alice).sendMessage(any());
    }

    @Test
    void closingTheOldSocketKeepsTheReconnectedSession() {
        handler.addNicknameToSessionIdNode("alice", "s-old");
        handler.addNicknameToSessionIdNode("alice", "s-new"); // reconnected before the old socket timed out

        handler.removeSession("s-old");

        assertTrue(handler.isCurrentSession("alice", "s-new"));
        assertFalse(handler.isCurrentSession("alice", "s-old"));
    }

    @Test
    void removingTheCurrentSessionForgetsTheNickname() {
        handler.addNicknameToSessionIdNode("alice", "s-1");

        handler.removeSession("s-1");

        assertFalse(handler.isCurrentSession("alice", "s-1"));
    }

    @Test
    void closedConnectionIsHandedToTheLobbyLoop() throws Exception {
        WebSocketSession session = openSession("s-1");

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(commandDispatcher).handleConnectionClosed("s-1");
    }

    @Test
    void pingIsAnsweredWithPongWithoutReachingTheGame() throws Exception {
        WebSocketSession session = openSession("s-1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session,
                new org.springframework.web.socket.TextMessage("{\"commandType\":\"PING\",\"executable\":{}}"));

        verify(session).sendMessage(argThat(m -> m.getPayload().toString().contains("PONG")));
        verify(commandDispatcher, never()).addCommandToList(any());
    }

    @Test
    void aSilentSessionIsClosedAsDead() throws Exception {
        WebSocketSession silent = openSession("s-silent");
        handler.afterConnectionEstablished(silent);
        handler.markAuthenticated("s-silent");

        handler.closeStaleSessions(System.nanoTime() + TimeUnit.SECONDS.toNanos(31));

        verify(silent).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    void aSocketThatDoesNotLogInIsClosed() throws Exception {
        WebSocketSession anonymous = openSession("s-anon");
        WebSocketSession player = openSession("s-player");
        handler.afterConnectionEstablished(anonymous);
        handler.afterConnectionEstablished(player);
        handler.markAuthenticated("s-player");

        // Both keep pinging, but only one ever presented a valid token
        long later = System.nanoTime() + GameWebSocketHandler.LOGIN_TIMEOUT_NANOS + TimeUnit.SECONDS.toNanos(1);
        handler.handleTextMessage(anonymous, PING);
        handler.handleTextMessage(player, PING);
        handler.closeStaleSessions(later);

        verify(anonymous).close(CloseStatus.POLICY_VIOLATION);
        verify(player, never()).close(any());
    }

    @Test
    void oneAddressCannotOpenConnectionsWithoutLimit() throws Exception {
        List<WebSocketSession> opened = new ArrayList<>();
        for (int i = 0; i <= GameWebSocketHandler.MAX_CONNECTIONS_PER_ADDRESS; i++) {
            WebSocketSession session = openSession("s-" + i, "203.0.113.7");
            handler.afterConnectionEstablished(session);
            opened.add(session);
        }
        WebSocketSession elsewhere = openSession("s-other", "198.51.100.1");
        handler.afterConnectionEstablished(elsewhere);

        verify(opened.get(opened.size() - 1)).close(CloseStatus.POLICY_VIOLATION);
        verify(opened.get(0), never()).close(any());
        verify(elsewhere, never()).close(any());

        // A connection from that address closes: there is room for one more
        handler.afterConnectionClosed(opened.get(0), CloseStatus.NORMAL);
        handler.afterConnectionClosed(opened.get(opened.size() - 1), CloseStatus.POLICY_VIOLATION);
        WebSocketSession again = openSession("s-again", "203.0.113.7");
        handler.afterConnectionEstablished(again);
        verify(again, never()).close(any());
    }

    @Test
    void floodingSessionIsClosed() throws Exception {
        WebSocketSession session = openSession("s-flood");
        org.springframework.web.socket.TextMessage command =
                new org.springframework.web.socket.TextMessage("{\"commandType\":\"JOIN_GAME_REQUEST\",\"executable\":{}}");

        for (int i = 0; i < 21; i++) {
            handler.handleTextMessage(session, command);
        }

        verify(commandDispatcher, times(20)).addCommandToList(any());
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void socketThatKeepsLoggingInIsClosed() throws Exception {
        WebSocketSession session = openSession("s-login");
        handler.afterConnectionEstablished(session);
        org.springframework.web.socket.TextMessage login = new org.springframework.web.socket.TextMessage(
                "{\"commandType\":\"PLAYER_INFO_REQUEST\",\"executable\":{\"token\":\"t\",\"nickname\":\"\"}}");

        for (int i = 0; i <= GameWebSocketHandler.MAX_LOGIN_ATTEMPTS; i++) {
            handler.handleTextMessage(session, login);
        }

        verify(commandDispatcher, times(GameWebSocketHandler.MAX_LOGIN_ATTEMPTS)).addCommandToList(any());
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    private static final org.springframework.web.socket.TextMessage PING =
            new org.springframework.web.socket.TextMessage("{\"commandType\":\"PING\",\"executable\":{}}");

    private static WebSocketSession openSession(String id, String address) throws IOException {
        WebSocketSession session = openSession(id);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ClientAddressInterceptor.CLIENT_ADDRESS, address);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private static WebSocketSession openSession(String id) throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        doNothing().when(session).sendMessage(any(WebSocketMessage.class));
        return session;
    }

    private static Message anyMessage() {
        return new Message(PlayerInfoResponse.loggedIn("alice", false), MessageType.PLAYER_INFO_RESPONSE);
    }
}
