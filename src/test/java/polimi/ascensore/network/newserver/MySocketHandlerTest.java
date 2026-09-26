package polimi.ascensore.network.newserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.GamePlayer;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.message.MessageType;
import polimi.ascensore.network.message.PlayerInfoResponse;
import polimi.ascensore.network.server.MasterServer;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MySocketHandlerTest {

    private MasterServer masterServer;
    private MySocketHandler handler;

    @BeforeEach
    void setUp() {
        masterServer = mock(MasterServer.class);
        handler = new MySocketHandler(masterServer);
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
    void closedConnectionIsHandedToTheCommandLoop() throws Exception {
        WebSocketSession session = openSession("s-1");

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(masterServer).handleConnectionClosed("s-1");
    }

    private static WebSocketSession openSession(String id) throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        doNothing().when(session).sendMessage(any(WebSocketMessage.class));
        return session;
    }

    private static Message anyMessage() {
        return new Message(PlayerInfoResponse.loggedIn("alice"), MessageType.PLAYER_INFO_RESPONSE);
    }
}
