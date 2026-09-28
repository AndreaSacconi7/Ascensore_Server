package polimi.ascensore.network.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import polimi.ascensore.controller.GameNotifier;
import polimi.ascensore.model.GamePlayer;
import polimi.ascensore.model.PlayerState;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.CommandType;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.message.MessageType;
import polimi.ascensore.network.message.Pong;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The WebSocket endpoint. Parses incoming commands and hands them to the {@link CommandDispatcher};
 * delivers outgoing messages to players' sessions.
 * <p>
 * Callbacks run on the container's threads, sends run on the lobby and match loops (and PONGs on the container
 * threads), hence the concurrent maps and the thread-safe session wrapper.
 * <p>
 * Heartbeat: clients send PING every few seconds. A session silent for longer than the idle timeout is a
 * dead connection the network never reported (phone in a pocket, Wi-Fi gone), and is closed, which starts
 * the normal reconnection window.
 * <p>
 * Nothing is served before logging in, and a socket that does not present a valid token within
 * {@link #LOGIN_TIMEOUT_NANOS} is closed: anonymous connections cannot pile up, nor keep an idle server
 * awake. Each network address may hold only a few connections at a time.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler implements GameNotifier {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    // A human player sends a handful of commands per minute; anything far above that is a broken or hostile client
    private static final int MAX_COMMANDS_PER_WINDOW = 20;

    // Clients identify right after connecting; the margin covers a slow token refresh
    static final long LOGIN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15);

    // PLAYER_INFO_REQUESTs on one socket: the login, then one per nickname tried. Every one costs a token check
    // and a database lookup, so a socket that keeps sending them is flooding the database
    static final int MAX_LOGIN_ATTEMPTS = 10;

    // A household or a classroom behind one address still fits; a script opening sockets in a loop does not
    static final int MAX_CONNECTIONS_PER_ADDRESS = 10;
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5);

    // A slow client gets its messages buffered up to these limits, then its session is closed, instead of
    // blocking a loop in a socket write
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 256 * 1024;

    private static final String PONG = new Message(new Pong(), MessageType.PONG).toJson();

    // sessionId -> session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // nickname -> sessionId the player is currently using
    private final Map<String, String> nicknameToSessionId = new ConcurrentHashMap<>();

    // sessionId -> commands received in the current rate window
    private final Map<String, RateWindow> rates = new ConcurrentHashMap<>();

    // sessionId -> PLAYER_INFO_REQUESTs received
    private final Map<String, Integer> loginAttempts = new ConcurrentHashMap<>();

    // sessionId -> System.nanoTime() of the last message received
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    // sessionId -> System.nanoTime() of the connection, until the session presents a valid token
    private final Map<String, Long> awaitingLogin = new ConcurrentHashMap<>();

    // network address -> open connections
    private final Map<String, Integer> connectionsPerAddress = new ConcurrentHashMap<>();

    private final long idleTimeoutNanos;

    private final ScheduledExecutorService idleSweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "idle-sessions");
        thread.setDaemon(true);
        return thread;
    });

    private final CommandDispatcher commandDispatcher;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Command.class, new CommandDeserializer())
            .create();

    private static final class RateWindow {
        private long start = System.nanoTime();
        private int count;
    }

    public GameWebSocketHandler(CommandDispatcher commandDispatcher,
                                @Value("${ascensore.idle-timeout-seconds:30}") long idleTimeoutSeconds) {
        this.commandDispatcher = commandDispatcher;
        this.idleTimeoutNanos = TimeUnit.SECONDS.toNanos(idleTimeoutSeconds);
        commandDispatcher.setSocketHandler(this);
    }

    @PostConstruct
    void startIdleSweeper() {
        idleSweeper.scheduleWithFixedDelay(() -> closeStaleSessions(System.nanoTime()), 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopIdleSweeper() {
        idleSweeper.shutdownNow();
    }

    // Visible for tests
    void closeStaleSessions(long now) {
        lastSeen.forEach((sessionId, seen) -> {
            if (now - seen > idleTimeoutNanos) {
                log.info("Session {} silent for too long, closing it", sessionId);
                lastSeen.remove(sessionId);
                awaitingLogin.remove(sessionId);
                closeSession(sessionId, CloseStatus.SESSION_NOT_RELIABLE);
            }
        });
        awaitingLogin.forEach((sessionId, connected) -> {
            if (now - connected > LOGIN_TIMEOUT_NANOS) {
                log.info("Session {} did not log in, closing it", sessionId);
                awaitingLogin.remove(sessionId);
                closeSession(sessionId, CloseStatus.POLICY_VIOLATION);
            }
        });
    }

    /**
     * The session presented a valid token: it is no longer subject to the login deadline. Called on the lobby
     * loop, also when the player still has to choose a nickname.
     */
    public void markAuthenticated(String sessionId) {
        awaitingLogin.remove(sessionId);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (!withinRateLimit(session.getId())) {
            log.warn("Session {} exceeded {} commands in {}s, closing it", session.getId(),
                    MAX_COMMANDS_PER_WINDOW, TimeUnit.NANOSECONDS.toSeconds(WINDOW_NANOS));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        lastSeen.put(session.getId(), System.nanoTime());
        try {
            Command command = gson.fromJson(message.getPayload(), Command.class);
            if (command.getCommandType() == CommandType.PING) {
                send(getSession(session.getId()), new TextMessage(PONG), session.getId());
                return;
            }
            if (command.getCommandType() == CommandType.PLAYER_INFO_REQUEST
                    && loginAttempts.merge(session.getId(), 1, Integer::sum) > MAX_LOGIN_ATTEMPTS) {
                log.warn("Session {} tried to log in more than {} times, closing it", session.getId(),
                        MAX_LOGIN_ATTEMPTS);
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            // Payloads are not logged: PLAYER_INFO_REQUEST carries the player's access token
            log.debug("Received {} from {}", command.getCommandType(), session.getId());
            command.setClientSessionId(session.getId());
            commandDispatcher.addCommandToList(command);
        } catch (RuntimeException e) {
            log.warn("Unreadable command from {}: {}", session.getId(), e.getMessage());
        }
    }

    private boolean withinRateLimit(String sessionId) {
        RateWindow window = rates.computeIfAbsent(sessionId, id -> new RateWindow());
        synchronized (window) {
            long now = System.nanoTime();
            if (now - window.start > WINDOW_NANOS) {
                window.start = now;
                window.count = 0;
            }
            return ++window.count <= MAX_COMMANDS_PER_WINDOW;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String address = addressOf(session);
        if (address != null
                && connectionsPerAddress.merge(address, 1, Integer::sum) > MAX_CONNECTIONS_PER_ADDRESS) {
            log.warn("Too many connections from {}, refusing {}", address, session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        long now = System.nanoTime();
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
        lastSeen.put(session.getId(), now);
        awaitingLogin.put(session.getId(), now);
        log.debug("Connection opened: {}", session.getId());
    }

    private static String addressOf(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        return attributes == null ? null : (String) attributes.get(ClientAddressInterceptor.CLIENT_ADDRESS);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String address = addressOf(session);
        if (address != null) {
            connectionsPerAddress.computeIfPresent(address, (key, count) -> count > 1 ? count - 1 : null);
        }
        awaitingLogin.remove(session.getId());
        rates.remove(session.getId());
        loginAttempts.remove(session.getId());
        lastSeen.remove(session.getId());
        // Game-side cleanup (reconnection timer, session removal) runs on the lobby loop
        commandDispatcher.handleConnectionClosed(session.getId());
        log.debug("Connection closed: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        log.warn("Transport error on {}: {}", session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void forwardUpdateToAll(Message message, List<GamePlayer> playersInGame) {
        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        log.debug("Broadcasting {}", msg.getPayload());
        for (GamePlayer p : playersInGame) {
            if (p.getPlayerState() != PlayerState.EXIT) {
                send(getSession(p.getSessionId()), msg, p.getNickname());
            }
        }
    }

    @Override
    public void forwardUpdateToSingleClient(Message message, String nickname) {
        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        log.debug("Sending to {}: {}", nickname, msg.getPayload());
        String sessionId = nickname == null ? null : nicknameToSessionId.get(nickname);
        send(getSession(sessionId), msg, nickname);
    }

    public void sendMessageToClient(Message message, String sessionId) {
        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        log.debug("Sending to session {}: {}", sessionId, msg.getPayload());
        send(getSession(sessionId), msg, sessionId);
    }

    /**
     * Sends to one recipient and never throws: a player who is mid-reconnection has no open session,
     * and failing on them would abort the broadcast (and the game update) for everyone else.
     */
    private void send(WebSocketSession session, WebSocketMessage<String> msg, String recipient) {
        if (session == null || !session.isOpen()) {
            log.debug("No open session for {}, message skipped", recipient);
            return;
        }
        try {
            session.sendMessage(msg);
        } catch (IOException | RuntimeException e) {
            // Includes a client too slow to keep up: the session wrapper closes it
            log.warn("Could not send to {}: {}", recipient, e.getMessage());
        }
    }

    public void addNicknameToSessionIdNode(String nickname, String sessionId) {
        nicknameToSessionId.put(nickname, sessionId);
    }

    /**
     * True if {@code sessionId} is the socket the player is currently using. After a reconnection the
     * player's old socket can report its close late, and that close must not affect the new session.
     */
    public boolean isCurrentSession(String nickname, String sessionId) {
        return nickname != null && sessionId.equals(nicknameToSessionId.get(nickname));
    }

    /**
     * The socket the player is currently using, or null.
     */
    public String currentSessionOf(String nickname) {
        return nickname == null ? null : nicknameToSessionId.get(nickname);
    }

    public void closeSession(String sessionId, CloseStatus status) {
        WebSocketSession session = getSession(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException e) {
            log.warn("Could not close session {}: {}", sessionId, e.getMessage());
        }
    }

    public WebSocketSession getSession(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    // Called on the lobby loop, on logout and when a socket closes
    public void removeSession(String sessionId) {
        // Only drop the nickname mapping if it still points here: after a reconnection it points to the new session
        nicknameToSessionId.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
        sessions.remove(sessionId);
    }
}
