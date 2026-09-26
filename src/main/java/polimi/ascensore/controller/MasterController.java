package polimi.ascensore.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.Seed;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.websocket.GameWebSocketHandler;
import polimi.ascensore.persistence.PlayerRepository;
import polimi.ascensore.auth.SupabaseAuthService;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server-wide state: who is logged in on which socket, matchmaking, and which match each player is in.
 * Every public method runs on the {@link CommandLoop}.
 */
@Service
public class MasterController implements GameLifeCycleListener {

    private static final Logger log = LoggerFactory.getLogger(MasterController.class);

    // How long a player who dropped mid-match has to reconnect before leaving it
    private static final int DISCONNECT_TIMEOUT_SECONDS = 60;

    private static final String SESSION_PLAYER = "PLAYER";

    private final CommandLoop commandLoop;

    private final GameSettings settings;

    private final SupabaseAuthService authService;

    private final PlayerRepository playerRepository;

    private GameWebSocketHandler sockets;

    // Matches still waiting for players, oldest first
    private final List<GameController> openMatches = new LinkedList<>();

    // Player's Supabase id -> the match they are in (waiting or started)
    private final Map<String, GameController> playerGameMap = new HashMap<>();

    // Pending reconnection windows: player's Supabase id -> timer
    private final Map<String, PendingDisconnect> disconnectTimers = new HashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "reconnection-timers");
        thread.setDaemon(true);
        return thread;
    });

    private final Random random = new SecureRandom();

    // One reconnection window. Compared by identity, so a timer that fired late cannot close a newer window.
    private static final class PendingDisconnect {
        private ScheduledFuture<?> timer;
    }

    public MasterController(CommandLoop commandLoop, GameSettings settings, SupabaseAuthService authService,
                            PlayerRepository playerRepository) {
        this.commandLoop = commandLoop;
        this.settings = settings;
        this.authService = authService;
        this.playerRepository = playerRepository;
    }

    public void setSocketHandler(GameWebSocketHandler gameWebSocketHandler) {
        this.sockets = gameWebSocketHandler;
    }

    ///// LOGIN /////

    /**
     * First message on every socket: identifies the player from their Supabase token.
     * <p>
     * A player without a valid public nickname (a new account, or an old one whose nickname was its email
     * address) is asked to choose one; the client then repeats this request with {@code requestedNickname}.
     */
    public void fetchPlayerInfo(String sessionId, String token, String requestedNickname) {
        String supabaseUid = authService.validateAndGetUserId(token);
        if (supabaseUid == null) {
            reply(sessionId, PlayerInfoResponse.rejected(PlayerInfoResponse.INVALID_TOKEN));
            return;
        }

        Player player = playerRepository.findBySupabaseUid(supabaseUid).orElse(null);
        boolean playerInGame = checkIfPlayerInGame(supabaseUid);

        // A player in the middle of a match keeps their name until the match ends
        if (!playerInGame && (player == null || !NicknamePolicy.isValid(player.getNickname()))) {
            String problem = nicknameProblem(requestedNickname);
            if (problem != null) {
                reply(sessionId, PlayerInfoResponse.nicknameRequired(problem));
                return;
            }
            if (player == null) {
                player = new Player(supabaseUid, requestedNickname);
                log.info("New player {}", requestedNickname);
            } else {
                player.setNickname(requestedNickname);
                log.info("Player {} chose a public nickname", requestedNickname);
            }
            player = playerRepository.save(player);
        }

        sockets.addNicknameToSessionIdNode(player.getNickname(), sessionId);
        sockets.getSession(sessionId).getAttributes().put(SESSION_PLAYER, player);
        reply(sessionId, PlayerInfoResponse.loggedIn(player.getNickname(), playerInGame));

        if (playerInGame) {
            handlePlayerReconnection(player, sessionId);
        }
    }

    // Why this nickname cannot be used, or null if it can
    private String nicknameProblem(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return PlayerInfoResponse.NICKNAME_MISSING;
        }
        if (!NicknamePolicy.isValid(nickname)) {
            return PlayerInfoResponse.NICKNAME_INVALID;
        }
        // Checked on the command loop, so two players cannot claim the same name at once
        if (playerRepository.existsByNicknameIgnoreCase(nickname)) {
            return PlayerInfoResponse.NICKNAME_TAKEN;
        }
        return null;
    }

    public Player getPlayerBySession(String sessionId) {
        WebSocketSession session = sockets.getSession(sessionId);
        return session == null ? null : (Player) session.getAttributes().get(SESSION_PLAYER);
    }

    public void logout(String clientSessionId) {
        Player player = getPlayerBySession(clientSessionId);
        if (player != null && checkIfPlayerInGame(player.getSupabaseUid())) {
            // Leaving on purpose: no reconnection window
            leaveMatch(player);
        }
        sockets.removeSession(clientSessionId);
    }

    ///// MATCHMAKING AND MOVES /////

    public void addPlayerToGame(String sessionId) {
        Player player = getPlayerBySession(sessionId);
        if (player == null) {
            log.warn("JOIN_GAME_REQUEST ignored: session {} is not logged in", sessionId);
            return;
        }
        if (checkIfPlayerInGame(player.getSupabaseUid())) {
            log.warn("JOIN_GAME_REQUEST ignored: {} is already in a match", player.getNickname());
            return;
        }

        GameController match = joinOpenMatch(player, sessionId);
        playerGameMap.put(player.getSupabaseUid(), match);
        reply(sessionId, new JoinGameResponse(true, player.getNickname()));
        log.info("{} joined a match ({}/{})", player.getNickname(), match.getNumPlayersInGame(),
                settings.playersPerMatch());

        if (match.getNumPlayersInGame() == settings.playersPerMatch()) {
            openMatches.remove(match);
            match.startGame();
        }
    }

    // Seats the player in the oldest match still waiting for players, or opens a new one
    private GameController joinOpenMatch(Player player, String sessionId) {
        Iterator<GameController> it = openMatches.iterator();
        while (it.hasNext()) {
            GameController match = it.next();
            try {
                match.addPlayerToGame(player, sessionId);
                return match;
            } catch (CannotAddPlayerNowException e) {
                // Already started: it should not have been listed as open
                it.remove();
            }
        }
        GameController match = new GameController(this, sockets, settings.maxHandSize(), random);
        openMatches.add(match);
        try {
            match.addPlayerToGame(player, sessionId);
        } catch (CannotAddPlayerNowException e) {
            throw new IllegalStateException("A new match refused its first player", e);
        }
        return match;
    }

    public void putCard(Seed seed, int value, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        GameController match = gameOf(player);
        if (match == null) {
            log.warn("PUT_CARD ignored: session {} is not in a match", sessionId);
            return;
        }
        match.putCard(seed, value, player.getNickname());
    }

    public void setBet(int bet, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        GameController match = gameOf(player);
        if (match == null) {
            log.warn("SET_BET ignored: session {} is not in a match", sessionId);
            return;
        }
        match.setBet(bet, player.getNickname());
    }

    // The match this player is in, or null if not logged in or not playing
    private GameController gameOf(Player player) {
        return player == null ? null : playerGameMap.get(player.getSupabaseUid());
    }

    public boolean checkIfPlayerInGame(String supabaseUid) {
        return playerGameMap.containsKey(supabaseUid);
    }

    @Override
    public void onGameEnded(GameController gameController) {
        playerGameMap.values().removeIf(match -> match == gameController);
        log.info("Match ended, {} players still in a match", playerGameMap.size());
    }

    ///// DISCONNECTION AND RECONNECTION /////

    /**
     * Runs once a socket has closed, after every command that socket had sent.
     */
    public void handleConnectionClosed(String sessionId) {
        Player player = getPlayerBySession(sessionId);
        // Only the player's current socket counts: if they already reconnected on a new socket, the old
        // one closing late must not start a timer that would kick them out.
        if (player != null
                && sockets.isCurrentSession(player.getNickname(), sessionId)
                && checkIfPlayerInGame(player.getSupabaseUid())) {
            if (openMatches.contains(gameOf(player))) {
                // Nothing to resume in a match that has not started: free the seat now
                leaveMatch(player);
            } else {
                onPlayerDisconnected(player.getSupabaseUid());
            }
        }
        sockets.removeSession(sessionId);
    }

    private void onPlayerDisconnected(String playerUuid) {
        log.info("Player {} disconnected, waiting {}s for a reconnection", playerUuid, DISCONNECT_TIMEOUT_SECONDS);

        // The timer thread only enqueues the expiry: the cleanup itself runs on the command loop
        PendingDisconnect pending = new PendingDisconnect();
        pending.timer = scheduler.schedule(
                () -> commandLoop.submit(() -> onDisconnectTimeout(playerUuid, pending)),
                DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        PendingDisconnect previous = disconnectTimers.put(playerUuid, pending);
        if (previous != null) {
            previous.timer.cancel(false);
        }
    }

    private void onDisconnectTimeout(String playerUuid, PendingDisconnect pending) {
        // The timer may have fired just before the player reconnected: then its window is already closed
        if (!disconnectTimers.remove(playerUuid, pending)) {
            return;
        }
        log.info("Player {} did not reconnect in time", playerUuid);
        playerRepository.findBySupabaseUid(playerUuid).ifPresentOrElse(
                this::leaveMatch,
                () -> log.error("Player {} not found in the database", playerUuid));
    }

    private void handlePlayerReconnection(Player player, String sessionId) {
        PendingDisconnect pending = disconnectTimers.remove(player.getSupabaseUid());
        if (pending != null) {
            pending.timer.cancel(false);
        }
        log.info("{} reconnected to their match", player.getNickname());
        playerGameMap.get(player.getSupabaseUid()).sendAllDataAfterReconnection(player.getNickname(), sessionId);
    }

    // Removes the player from their match for good: a waiting match just frees the seat, a started one ends
    private void leaveMatch(Player player) {
        PendingDisconnect pending = disconnectTimers.remove(player.getSupabaseUid());
        if (pending != null) {
            pending.timer.cancel(false);
        }
        GameController match = playerGameMap.remove(player.getSupabaseUid());
        if (match == null) {
            // The match already ended while the player was away
            return;
        }
        if (openMatches.contains(match)) {
            match.removeWaitingPlayer(player.getNickname());
            if (match.getNumPlayersInGame() == 0) {
                openMatches.remove(match);
            }
            return;
        }
        match.playerExitGame(player.getNickname());
    }

    private void reply(String sessionId, PlayerInfoResponse response) {
        sockets.sendMessageToClient(new Message(response, MessageType.PLAYER_INFO_RESPONSE), sessionId);
    }

    private void reply(String sessionId, JoinGameResponse response) {
        sockets.sendMessageToClient(new Message(response, MessageType.JOIN_GAME_RESPONSE), sessionId);
    }

    // Visible for tests
    boolean hasPendingDisconnect(String playerUuid) {
        return disconnectTimers.containsKey(playerUuid);
    }
}
