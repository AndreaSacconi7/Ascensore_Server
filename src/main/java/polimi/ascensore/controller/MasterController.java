package polimi.ascensore.controller;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.auth.SupabaseAuthService;
import polimi.ascensore.model.MatchState;
import polimi.ascensore.model.Seed;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.websocket.GameWebSocketHandler;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.persistence.PlayerRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide state: who is logged in on which socket, matchmaking, and which match each player is in.
 * <p>
 * Every public method runs on the lobby loop (see {@link GameLoops}). Matches waiting for players belong
 * to the lobby too; once a match starts, the lobby only talks to it through the match's own loop.
 */
@Service
public class MasterController implements GameLifeCycleListener {

    private static final Logger log = LoggerFactory.getLogger(MasterController.class);

    // How long a player who dropped mid-match has to reconnect before leaving it
    private static final Duration DISCONNECT_TIMEOUT = Duration.ofSeconds(60);

    // After a restart, the player on turn gets this much extra time while everyone reconnects
    static final Duration RESTART_GRACE = Duration.ofSeconds(20);

    private static final String SESSION_PLAYER = "PLAYER";

    private static final CloseStatus SESSION_REPLACED_STATUS = new CloseStatus(4001, "Logged in on another device");

    private final GameLoops loops;

    private final GameSettings settings;

    private final SupabaseAuthService authService;

    private final PlayerRepository playerRepository;

    private final MatchStore matchStore;

    private GameWebSocketHandler sockets;

    // Matches still waiting for players, oldest first
    private final List<Match> openMatches = new LinkedList<>();

    // Player's Supabase id -> the match they are in (waiting or started). Written on the lobby only; the
    // database thread reads it during logins.
    private final Map<String, Match> playerGameMap = new ConcurrentHashMap<>();

    // Pending reconnection windows: player's Supabase id -> timer
    private final Map<String, PendingDisconnect> disconnectTimers = new HashMap<>();

    private final Random random = new SecureRandom();

    // A match and the loop it runs on once started
    private record Match(GameController game, SerialLoop loop) {
    }

    // One reconnection window. Compared by identity, so a timer that fired late cannot close a newer window.
    private static final class PendingDisconnect {
        private Runnable cancel = () -> { };
    }

    // Outcome of the blocking half of a login: the player, or why they cannot log in yet
    private record Login(Player player, String error, boolean needsNickname) {

        // The token was valid, even if the player still has to choose a nickname
        boolean authenticated() {
            return player != null || needsNickname;
        }
    }

    public MasterController(GameLoops loops, GameSettings settings, SupabaseAuthService authService,
                            PlayerRepository playerRepository, MatchStore matchStore) {
        this.loops = loops;
        this.settings = settings;
        this.authService = authService;
        this.playerRepository = playerRepository;
        this.matchStore = matchStore;
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
     * <p>
     * Checking the token and reading the database happen on the database thread, then the login completes
     * back on the lobby: a slow database delays logins, never the matches in progress.
     */
    public void fetchPlayerInfo(String sessionId, String token, String requestedNickname) {
        loops.database().execute(() -> {
            Login login;
            try {
                login = identify(token, requestedNickname);
            } catch (RuntimeException e) {
                log.error("Login on session {} failed", sessionId, e);
                login = null;
            }
            Login result = login;
            loops.lobby().execute(() -> completeLogin(sessionId, result));
        });
    }

    // On the database thread
    private Login identify(String token, String requestedNickname) {
        String supabaseUid = authService.validateAndGetUserId(token);
        if (supabaseUid == null) {
            return new Login(null, PlayerInfoResponse.INVALID_TOKEN, false);
        }

        Player player = playerRepository.findBySupabaseUid(supabaseUid).orElse(null);

        // A player in the middle of a match keeps their name until the match ends
        if (!checkIfPlayerInGame(supabaseUid) && (player == null || !NicknamePolicy.isValid(player.getNickname()))) {
            String problem = nicknameProblem(requestedNickname);
            if (problem != null) {
                return new Login(null, problem, true);
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
        return new Login(player, null, false);
    }

    // Back on the lobby
    private void completeLogin(String sessionId, Login login) {
        if (login == null) {
            // Database or key server unreachable: the client reconnects and tries again
            sockets.closeSession(sessionId, CloseStatus.SERVER_ERROR);
            return;
        }
        if (login.authenticated()) {
            sockets.markAuthenticated(sessionId);
        }
        if (login.player() == null) {
            reply(sessionId, login.needsNickname()
                    ? PlayerInfoResponse.nicknameRequired(login.error())
                    : PlayerInfoResponse.rejected(login.error()));
            return;
        }
        WebSocketSession session = sockets.getSession(sessionId);
        if (session == null) {
            // The socket closed while the player was being looked up
            return;
        }

        Player player = login.player();
        Match match = playerGameMap.get(player.getSupabaseUid());
        if (match != null && openMatches.contains(match)) {
            // Logged in again (on another device) while waiting for a match: back to the menu
            leaveMatch(player.getSupabaseUid(), player.getNickname());
            match = null;
        }

        replaceOtherSession(player, sessionId);
        sockets.addNicknameToSessionIdNode(player.getNickname(), sessionId);
        session.getAttributes().put(SESSION_PLAYER, player);
        reply(sessionId, PlayerInfoResponse.loggedIn(player.getNickname(), match != null));

        if (match != null) {
            handlePlayerReconnection(player, match, sessionId);
        }
    }

    // One account, one device: logging in again closes the socket the player was using until now
    private void replaceOtherSession(Player player, String newSessionId) {
        String oldSessionId = sockets.currentSessionOf(player.getNickname());
        if (oldSessionId == null || oldSessionId.equals(newSessionId)) {
            return;
        }
        log.info("{} logged in on another device, closing session {}", player.getNickname(), oldSessionId);
        WebSocketSession old = sockets.getSession(oldSessionId);
        if (old != null) {
            // Commands still in flight from the old socket are ignored from now on
            old.getAttributes().remove(SESSION_PLAYER);
        }
        sockets.sendMessageToClient(new Message(new SessionReplaced(), MessageType.SESSION_REPLACED), oldSessionId);
        sockets.closeSession(oldSessionId, SESSION_REPLACED_STATUS);
    }

    // Why this nickname cannot be used, or null if it can
    private String nicknameProblem(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return PlayerInfoResponse.NICKNAME_MISSING;
        }
        if (!NicknamePolicy.isValid(nickname)) {
            return PlayerInfoResponse.NICKNAME_INVALID;
        }
        // Every login runs on the single database thread, so two players cannot claim the same name at once
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
            leaveMatch(player.getSupabaseUid(), player.getNickname());
        }
        sockets.removeSession(clientSessionId);
    }

    ///// MATCHMAKING AND MOVES /////

    public void addPlayerToGame(String sessionId, Integer requestedPlayers) {
        Player player = getPlayerBySession(sessionId);
        if (player == null) {
            log.warn("JOIN_GAME_REQUEST ignored: session {} is not logged in", sessionId);
            return;
        }
        if (checkIfPlayerInGame(player.getSupabaseUid())) {
            log.warn("JOIN_GAME_REQUEST ignored: {} is already in a match", player.getNickname());
            return;
        }

        int size = settings.matchSize(requestedPlayers);
        Match match = joinOpenMatch(player, sessionId, size);
        playerGameMap.put(player.getSupabaseUid(), match);
        reply(sessionId, new JoinGameResponse(true, player.getNickname(), size));
        log.info("{} joined a {}-player match ({}/{})", player.getNickname(), size,
                match.game().getNumPlayersInGame(), size);

        if (match.game().isFull()) {
            openMatches.remove(match);
            // From now on the match runs on its own loop
            match.loop().execute(match.game()::startGame);
        } else {
            match.game().broadcastWaitingRoom();
        }
    }

    /**
     * Leaves matchmaking, or the match in progress for good.
     */
    public void leaveGame(String sessionId) {
        Player player = getPlayerBySession(sessionId);
        if (player != null && checkIfPlayerInGame(player.getSupabaseUid())) {
            log.info("{} left their match", player.getNickname());
            leaveMatch(player.getSupabaseUid(), player.getNickname());
        }
    }

    // Seats the player in the oldest match of that size still waiting for players, or opens a new one
    private Match joinOpenMatch(Player player, String sessionId, int size) {
        Iterator<Match> it = openMatches.iterator();
        while (it.hasNext()) {
            Match match = it.next();
            if (match.game().getPlayersPerMatch() != size) {
                continue;
            }
            try {
                match.game().addPlayerToGame(player, sessionId);
                return match;
            } catch (CannotAddPlayerNowException e) {
                // Already started: it should not have been listed as open
                it.remove();
            }
        }
        SerialLoop loop = loops.newMatchLoop();
        GameController game = new GameController(this, sockets, size, settings.maxHandSize(), random,
                clockOn(loop), settings.turnTime(), matchStore);
        Match match = new Match(game, loop);
        openMatches.add(match);
        try {
            game.addPlayerToGame(player, sessionId);
        } catch (CannotAddPlayerNowException e) {
            throw new IllegalStateException("A new match refused its first player", e);
        }
        return match;
    }

    public void putCard(Seed seed, int value, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        Match match = gameOf(player);
        if (match == null) {
            log.warn("PUT_CARD ignored: session {} is not in a match", sessionId);
            return;
        }
        String nickname = player.getNickname();
        match.loop().execute(() -> match.game().putCard(seed, value, nickname));
    }

    public void setBet(int bet, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        Match match = gameOf(player);
        if (match == null) {
            log.warn("SET_BET ignored: session {} is not in a match", sessionId);
            return;
        }
        String nickname = player.getNickname();
        match.loop().execute(() -> match.game().setBet(bet, nickname));
    }

    // The match this player is in, or null if not logged in or not playing
    private Match gameOf(Player player) {
        return player == null ? null : playerGameMap.get(player.getSupabaseUid());
    }

    public boolean checkIfPlayerInGame(String supabaseUid) {
        return playerGameMap.containsKey(supabaseUid);
    }

    // Turn deadlines of a match: they fire on its own loop
    private TurnClock clockOn(SerialLoop loop) {
        return (delay, action) -> loops.schedule(delay, loop, action);
    }

    // Called on the match's loop: the bookkeeping moves to the lobby

    @Override
    public void onPlayerRemoved(GameController gameController, String supabaseUid) {
        loops.lobby().execute(() -> {
            Match match = playerGameMap.get(supabaseUid);
            if (match != null && match.game() == gameController) {
                playerGameMap.remove(supabaseUid);
            }
        });
    }

    @Override
    public void onGameEnded(GameController gameController) {
        loops.lobby().execute(() -> {
            playerGameMap.values().removeIf(match -> match.game() == gameController);
            log.info("Match ended, {} players still in a match", playerGameMap.size());
        });
    }

    ///// RESTART /////

    /**
     * Brings back the matches that were in progress when the server stopped, as saved after their last
     * move. Runs on the lobby at startup, before players can connect: each player then has the usual
     * reconnection window, and the player on turn some extra time.
     */
    public void restoreMatches() {
        List<MatchState> saved = matchStore.loadAll();
        for (MatchState state : saved) {
            try {
                SerialLoop loop = loops.newMatchLoop();
                GameController game = GameController.restore(state, this, sockets, random, clockOn(loop),
                        settings.turnTime(), matchStore);
                Match match = new Match(game, loop);
                for (MatchState.Seat seat : state.seats()) {
                    playerGameMap.put(seat.supabaseUid(), match);
                    onPlayerDisconnected(seat.supabaseUid(), seat.nickname());
                }
                loop.execute(() -> game.resume(RESTART_GRACE));
            } catch (RuntimeException e) {
                log.error("Could not restore match {}, dropping it", state.id(), e);
                matchStore.delete(state.id());
            }
        }
        if (!saved.isEmpty()) {
            log.info("Restored {} matches, {} players can reconnect", saved.size(), playerGameMap.size());
        }
    }

    /**
     * Lets the moves already queued finish, then writes the last snapshots before the process exits.
     */
    @PreDestroy
    public void shutdown() {
        loops.shutdown();
        matchStore.flush();
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
                leaveMatch(player.getSupabaseUid(), player.getNickname());
            } else {
                onPlayerDisconnected(player.getSupabaseUid(), player.getNickname());
            }
        }
        sockets.removeSession(sessionId);
    }

    private void onPlayerDisconnected(String supabaseUid, String nickname) {
        log.info("{} disconnected, waiting {}s for a reconnection", nickname, DISCONNECT_TIMEOUT.toSeconds());

        PendingDisconnect pending = new PendingDisconnect();
        pending.cancel = loops.schedule(DISCONNECT_TIMEOUT, loops.lobby(),
                () -> onDisconnectTimeout(supabaseUid, nickname, pending));

        PendingDisconnect previous = disconnectTimers.put(supabaseUid, pending);
        if (previous != null) {
            previous.cancel.run();
        }
    }

    private void onDisconnectTimeout(String supabaseUid, String nickname, PendingDisconnect pending) {
        // The timer may have fired just before the player reconnected: then its window is already closed
        if (!disconnectTimers.remove(supabaseUid, pending)) {
            return;
        }
        log.info("{} did not reconnect in time", nickname);
        leaveMatch(supabaseUid, nickname);
    }

    private void handlePlayerReconnection(Player player, Match match, String sessionId) {
        PendingDisconnect pending = disconnectTimers.remove(player.getSupabaseUid());
        if (pending != null) {
            pending.cancel.run();
        }
        log.info("{} reconnected to their match", player.getNickname());
        String nickname = player.getNickname();
        match.loop().execute(() -> match.game().sendAllDataAfterReconnection(nickname, sessionId));
    }

    // Removes the player from their match for good: a waiting match just frees the seat, a started one
    // goes on without them (or ends, if they were one of the last two)
    private void leaveMatch(String supabaseUid, String nickname) {
        PendingDisconnect pending = disconnectTimers.remove(supabaseUid);
        if (pending != null) {
            pending.cancel.run();
        }
        Match match = playerGameMap.remove(supabaseUid);
        if (match == null) {
            // The match already ended while the player was away
            return;
        }
        if (openMatches.contains(match)) {
            match.game().removeWaitingPlayer(nickname);
            if (match.game().getNumPlayersInGame() == 0) {
                openMatches.remove(match);
            } else {
                match.game().broadcastWaitingRoom();
            }
            return;
        }
        match.loop().execute(() -> match.game().playerExitGame(nickname));
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
