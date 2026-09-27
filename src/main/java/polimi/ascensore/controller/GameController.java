package polimi.ascensore.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.*;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.InvalidCard;
import polimi.ascensore.model.exception.PlayerNickNameDoesNotExist;
import polimi.ascensore.network.message.*;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runs one match: validates the players' moves against the rules, advances turns, tricks and sets,
 * and tells every player what changed.
 * <p>
 * While the match waits for players it belongs to the lobby loop; once started, it is only ever called
 * from its own match loop. After every move the new state goes to the {@link MatchStore}, so the match
 * can be resumed if the server restarts.
 */
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    // Final score reported for a player who left the match
    private static final int EXIT_SCORE = -500;

    // Clients keep a finished trick on screen this long before showing the next turn: added to that turn
    static final Duration RESULT_DISPLAY = Duration.ofSeconds(3);

    // A player whose turns time out this many times in a row is taken out of the match
    static final int MAX_TIMEOUTS_IN_A_ROW = 3;

    private final String id;

    private final Game game;

    private final GameNotifier notifier;

    private final GameLifeCycleListener gameLifeCycleListener;

    // Players needed to start: the match starts as soon as it is full
    private final int playersPerMatch;

    private final TurnClock clock;

    // Time to bet or play; zero disables the limit
    private final Duration turnTime;

    // Identifies the current turn, so a deadline that fires after the player acted is ignored
    private int turnSeq;

    private Runnable cancelTurnTimer = () -> { };

    private long turnDeadlineNanos;

    // nickname -> turns that timed out in a row
    private final Map<String, Integer> timeoutsInARow = new HashMap<>();

    private final MatchStore store;

    private boolean started;

    private boolean ended;

    public GameController(GameLifeCycleListener gameLifeCycleListener, GameNotifier notifier,
                          int playersPerMatch, int maxHandSize, Random random, TurnClock clock, Duration turnTime,
                          MatchStore store) {
        this(UUID.randomUUID().toString(), new Game(maxHandSize, random), gameLifeCycleListener, notifier,
                playersPerMatch, clock, turnTime, store);
    }

    private GameController(String id, Game game, GameLifeCycleListener gameLifeCycleListener, GameNotifier notifier,
                           int playersPerMatch, TurnClock clock, Duration turnTime, MatchStore store) {
        this.id = id;
        this.game = game;
        this.notifier = notifier;
        this.gameLifeCycleListener = gameLifeCycleListener;
        this.playersPerMatch = playersPerMatch;
        this.clock = clock;
        this.turnTime = turnTime;
        this.store = store;
    }

    /**
     * A match that was in progress when the server stopped, as it was after its last saved move.
     * Its clock stays stopped until {@link #resume(Duration)}.
     */
    public static GameController restore(MatchState state, GameLifeCycleListener gameLifeCycleListener,
                                         GameNotifier notifier, Random random, TurnClock clock, Duration turnTime,
                                         MatchStore store) {
        GameController controller = new GameController(state.id(), Game.restore(state, random), gameLifeCycleListener,
                notifier, state.playersPerMatch(), clock, turnTime, store);
        controller.timeoutsInARow.putAll(state.timeoutsInARow());
        controller.started = true;
        return controller;
    }

    /**
     * Restarts the clock of a restored match: the player on turn gets a full turn plus {@code grace}, time
     * for everyone to reconnect after the restart.
     */
    public void resume(Duration grace) {
        for (GamePlayer p : playOrder()) {
            if (p.getPlayerState() == PlayerState.BET || p.getPlayerState() == PlayerState.PUT) {
                giveTurn(p, p.getPlayerState(), grace);
                return;
            }
        }
        log.warn("Restored match {} has nobody on turn, ending it", id);
        endGameResult();
    }

    public String getId() {
        return id;
    }

    public int getPlayersPerMatch() {
        return playersPerMatch;
    }

    public boolean isFull() {
        return game.getPlayers().size() >= playersPerMatch;
    }

    /**
     * Tells everyone waiting in this match who is in and how many players it needs.
     */
    public void broadcastWaitingRoom() {
        List<String> nicknames = game.getPlayers().stream().map(GamePlayer::getNickname).toList();
        broadcast(new WaitingRoomUpdate(playersPerMatch, nicknames), MessageType.WAITING_ROOM_UPDATE);
    }

    public void addPlayerToGame(Player player, String sessionId) throws CannotAddPlayerNowException {
        game.addPlayer(player, sessionId);
    }

    public int getNumPlayersInGame() {
        return game.getPlayers().size();
    }

    /**
     * Frees a seat in a match that has not started yet.
     */
    public void removeWaitingPlayer(String nickname) {
        game.getPlayers().removeIf(p -> p.getNickname().equals(nickname));
    }

    public void startGame() {
        started = true;
        game.startGame();
        game.distributeCards();

        log.info("Match started: {} players, hands 1..{}..1", game.getPlayers().size(), game.getMaxHandSize());
        broadcast(new StartingGame(nicknamesInPlayOrder(), game.getMaxHandSize()), MessageType.STARTING_GAME);
        notifyDistributedCards();

        for (GamePlayer p : game.getPlayers()) {
            p.updateState(PlayerState.WAIT);
            broadcast(new PlayerStateUpdate(PlayerState.WAIT, p.getNickname()), MessageType.PLAYER_STATE_UPDATE);
        }
        giveTurn(playOrder().get(0), PlayerState.BET);
        save();
    }

    public void setBet(int bet, String nickname) {
        setBet(bet, nickname, false);
        save();
    }

    private void setBet(int bet, String nickname, boolean automatic) {
        GamePlayer player = findPlayer(nickname);
        if (player == null) {
            return;
        }
        if (player.getPlayerState() != PlayerState.BET) {
            notifyError(nickname, "Non è il tuo turno di scommettere");
            return;
        }
        boolean isLastBettor = game.getBetsPlaced() == game.getPlayers().size() - 1;
        int otherBetsTotal = game.getPlayers().stream().mapToInt(GamePlayer::getBet).sum();
        if (!GameRules.isValidBet(bet, game.getSet(), isLastBettor, otherBetsTotal)) {
            notifyError(nickname, "Scommessa non valida: da 0 a " + game.getSet()
                    + ", e l'ultimo non può rendere il totale uguale alle carte in mano");
            return;
        }

        if (!automatic) {
            timeoutsInARow.remove(nickname);
        }
        player.updateBet(bet);
        game.registerBet();
        broadcast(new SettedBetUpdate(nickname, bet), MessageType.SETTED_BET);
        endTurn(player);
        nextBettor();
    }

    private void nextBettor() {
        if (game.getBetsPlaced() < game.getPlayers().size()) {
            giveTurn(playOrder().get(game.getBetsPlaced()), PlayerState.BET);
        } else {
            // Everyone has bet: the first player in order leads the first trick
            giveTurn(playOrder().get(0), PlayerState.PUT);
        }
    }

    public void putCard(Seed seed, int value, String nickname) {
        putCard(seed, value, nickname, false);
        save();
    }

    private void putCard(Seed seed, int value, String nickname, boolean automatic) {
        GamePlayer player = findPlayer(nickname);
        if (player == null) {
            return;
        }
        if (player.getPlayerState() != PlayerState.PUT) {
            notifyError(nickname, "Non è il tuo turno");
            return;
        }
        Card card = player.getHand().stream()
                .filter(c -> c.getSeed() == seed && c.getValue() == value)
                .findFirst()
                .orElse(null);
        if (card == null) {
            notifyError(nickname, "Non hai questa carta");
            return;
        }
        List<Card> trick = game.getTableCard().getPlayedCards();
        Card leadCard = trick.isEmpty() ? null : trick.get(0);
        if (!GameRules.isValidCard(card, player.getHand(), leadCard)) {
            notifyError(nickname, "Devi rispondere al seme della prima carta");
            return;
        }

        if (!automatic) {
            timeoutsInARow.remove(nickname);
        }
        if (leadCard == null && game.isPeakSet()) {
            // Peak set: no briscola was dealt, the card leading each trick sets it
            game.getTableCard().setBriscola(card);
            broadcast(new BriscolaUpdate(card), MessageType.BRISCOLA_UPDATE);
        }
        trick.add(card);
        try {
            player.removeCardFromHand(seed, value);
        } catch (InvalidCard e) {
            throw new IllegalStateException("Card validated but missing from hand", e);
        }
        broadcast(new PlayedCardUpdate(card, nickname), MessageType.PLAYED_CARD);
        endTurn(player);

        if (trick.size() < game.getPlayers().size()) {
            giveTurn(playOrder().get(trick.size()), PlayerState.PUT);
        } else {
            completeTrick();
        }
    }

    /**
     * A player leaves for good (on purpose, or not back in time after a disconnection). The match goes on
     * without them while at least two players remain: their bet stops counting, their card leaves the
     * current trick, and if it was their turn it passes on. With one player left, the match ends.
     */
    public void playerExitGame(String nickname) {
        exitGame(nickname);
        save();
    }

    private void exitGame(String nickname) {
        GamePlayer leaver = findPlayer(nickname);
        if (leaver == null) {
            return;
        }
        boolean wasOnTurn = leaver.getPlayerState() == PlayerState.BET || leaver.getPlayerState() == PlayerState.PUT;
        boolean betting = game.getBetsPlaced() < game.getPlayers().size();
        int position = playOrder().indexOf(leaver);
        List<Card> trick = game.getTableCard().getPlayedCards();

        if (betting && position < game.getBetsPlaced()) {
            game.unregisterBet();
        }
        boolean removedLeadCard = false;
        if (!betting && position < trick.size()) {
            trick.remove(position);
            removedLeadCard = position == 0;
        }
        game.removePlayer(leaver);
        log.info("{} left the match, {} players remain", nickname, game.getPlayers().size());
        broadcast(new PlayerExitGame(nickname), MessageType.PLAYER_EXIT_GAME);

        if (game.getPlayers().size() < 2) {
            endGameResult();
            return;
        }
        if (removedLeadCard && game.isPeakSet()) {
            // In the peak set the lead card is the briscola: it is now the next card, if any
            Card briscola = trick.isEmpty() ? null : trick.get(0);
            game.getTableCard().setBriscola(briscola);
            broadcast(new BriscolaUpdate(briscola), MessageType.BRISCOLA_UPDATE);
        }

        if (betting) {
            if (wasOnTurn || game.getBetsPlaced() == game.getPlayers().size()) {
                nextBettor();
            }
        } else if (trick.size() == game.getPlayers().size()) {
            // Everyone still playing has played: the trick is complete without the leaver's card
            completeTrick();
        } else if (wasOnTurn) {
            giveTurn(playOrder().get(trick.size()), PlayerState.PUT);
        }
    }

    /**
     * Rebuilds the table for a player who reconnected on a new socket.
     */
    public void sendAllDataAfterReconnection(String nickname, String sessionId) {
        GamePlayer player = findPlayer(nickname);
        if (player == null) {
            return;
        }
        player.setSessionId(sessionId);

        Map<String, Integer> scores = new LinkedHashMap<>();
        Map<String, Integer> bets = new LinkedHashMap<>();
        Map<String, Integer> roundsWon = new LinkedHashMap<>();
        for (GamePlayer p : playOrder()) {
            scores.put(p.getNickname(), p.getScore());
            bets.put(p.getNickname(), p.getBet());
            roundsWon.put(p.getNickname(), p.getRoundsWon());
        }
        // Cards on the table were played in play order, starting from the first player
        Map<String, Card> playedCards = new LinkedHashMap<>();
        List<Card> trick = game.getTableCard().getPlayedCards();
        for (int i = 0; i < trick.size(); i++) {
            playedCards.put(playOrder().get(i).getNickname(), trick.get(i));
        }

        sendTo(nickname, new StartingGame(nicknamesInPlayOrder(), game.getMaxHandSize()), MessageType.STARTING_GAME);
        sendTo(nickname, new BriscolaUpdate(game.getTableCard().getBriscola()), MessageType.BRISCOLA_UPDATE);
        sendTo(nickname, new HandUpdate(player.getHand()), MessageType.HAND_UPDATE);
        sendTo(nickname, new InfoAfterReconnection(game.getSet(), game.getRound(), game.getSetsPlayed(),
                game.getMaxHandSize(), scores, bets, roundsWon,
                playedCards), MessageType.INFO_AFTER_RECONNECTION);
        // Every player's state, so the client knows whose turn it is (including its own) and how long is left
        for (GamePlayer p : playOrder()) {
            sendTo(nickname, new PlayerStateUpdate(p.getPlayerState(), p.getNickname(), turnMillisLeft(p),
                    turnTime.toMillis()), MessageType.PLAYER_STATE_UPDATE);
        }
    }

    private void completeTrick() {
        List<Card> trick = game.getTableCard().getPlayedCards();
        Card briscola = game.getTableCard().getBriscola();
        int winnerIndex = GameRules.trickWinnerIndex(trick, briscola == null ? null : briscola.getSeed());
        GamePlayer winner = playOrder().get(winnerIndex);
        winner.updateRoundsWon();
        game.updateRound();

        if (game.getRound() == game.getSet()) {
            completeSet();
            return;
        }

        // Next trick: the winner leads
        game.getTableCard().resetPlayedCard();
        game.getTableCard().updatePlayerListOrder(winner);
        if (game.isPeakSet()) {
            game.getTableCard().setBriscola(null);
            broadcast(new BriscolaUpdate(null), MessageType.BRISCOLA_UPDATE);
        }

        Map<String, Integer> nextPlayerOrderAndTaken = new LinkedHashMap<>();
        for (GamePlayer p : playOrder()) {
            nextPlayerOrderAndTaken.put(p.getNickname(), p.getRoundsWon());
        }
        broadcast(new EndRoundUpdate(game.getRound(), nextPlayerOrderAndTaken), MessageType.END_ROUND);
        giveTurn(playOrder().get(0), PlayerState.PUT, RESULT_DISPLAY);
    }

    private void completeSet() {
        for (GamePlayer p : game.getPlayers()) {
            p.updateScore();
        }
        if (game.isLastSet()) {
            endGameResult();
            return;
        }

        game.nextSet();
        game.getTableCard().resetPlayedCard();
        for (GamePlayer p : game.getPlayers()) {
            p.resetBet();
            p.resetRoundsWon();
        }
        // Who bets first rotates around the table, one seat per set
        GamePlayer firstBettor = game.getPlayers().get(game.getSetsPlayed() % game.getPlayers().size());
        game.getTableCard().updatePlayerListOrder(firstBettor);
        game.getDeck().shuffleDeck();
        game.distributeCards();

        Map<String, Integer> nextPlayerOrderAndScore = new LinkedHashMap<>();
        for (GamePlayer p : playOrder()) {
            nextPlayerOrderAndScore.put(p.getNickname(), p.getScore());
        }
        broadcast(new EndSetUpdate(game.getSet(), game.getSetsPlayed(), nextPlayerOrderAndScore), MessageType.END_SET);
        notifyDistributedCards();
        giveTurn(playOrder().get(0), PlayerState.BET, RESULT_DISPLAY);
    }

    private void endGameResult() {
        ended = true;
        cancelTurnTimer.run();
        store.delete(id);
        Map<String, Integer> resultAndScore = new LinkedHashMap<>();
        for (GamePlayer p : game.endGame()) {
            resultAndScore.put(p.getNickname(), p.getPlayerState() == PlayerState.EXIT ? EXIT_SCORE : p.getScore());
        }
        // The master controller forgets this match before the players hear it ended, so that they can join
        // another one straight away
        gameLifeCycleListener.onGameEnded(this);
        broadcast(new EndGame(resultAndScore), MessageType.END_GAME);
    }

    private void notifyDistributedCards() {
        for (GamePlayer p : game.getPlayers()) {
            sendTo(p.getNickname(), new HandUpdate(p.getHand()), MessageType.HAND_UPDATE);
        }
        broadcast(new BriscolaUpdate(game.getTableCard().getBriscola()), MessageType.BRISCOLA_UPDATE);
    }

    private void giveTurn(GamePlayer player, PlayerState state) {
        giveTurn(player, state, Duration.ZERO);
    }

    // [extra] covers the time clients spend showing the previous trick before this turn appears
    private void giveTurn(GamePlayer player, PlayerState state, Duration extra) {
        player.updateState(state);
        cancelTurnTimer.run();
        int seq = ++turnSeq;
        long millisLeft = 0;
        if (!turnTime.isZero()) {
            Duration limit = turnTime.plus(extra);
            millisLeft = limit.toMillis();
            turnDeadlineNanos = System.nanoTime() + limit.toNanos();
            String nickname = player.getNickname();
            cancelTurnTimer = clock.schedule(limit, () -> {
                onTurnTimeout(seq, nickname);
                save();
            });
        }
        broadcast(new PlayerStateUpdate(state, player.getNickname(), millisLeft, turnTime.toMillis()),
                MessageType.PLAYER_STATE_UPDATE);
    }

    /**
     * The player on turn ran out of time: the server bets or plays for them (the lowest valid bet, the
     * weakest valid card). After too many timeouts in a row they are taken out, so an absent player
     * cannot hold the match hostage.
     */
    private void onTurnTimeout(int seq, String nickname) {
        GamePlayer player = findPlayer(nickname);
        if (seq != turnSeq || player == null) {
            return;
        }
        int timeouts = timeoutsInARow.merge(nickname, 1, Integer::sum);
        if (timeouts >= MAX_TIMEOUTS_IN_A_ROW) {
            log.info("{} let {} turns in a row time out, removing them", nickname, timeouts);
            // Freed for matchmaking before they hear about it, so they can join another match at once
            gameLifeCycleListener.onPlayerRemoved(this, player.getSupabaseId());
            // Tell the player before removing them: once out they no longer receive the match's messages
            sendTo(nickname, new PlayerExitGame(nickname), MessageType.PLAYER_EXIT_GAME);
            exitGame(nickname);
            return;
        }
        if (player.getPlayerState() == PlayerState.BET) {
            int bet = lowestValidBet(player);
            notifyError(nickname, "Tempo scaduto: ho scommesso " + bet + " per te");
            setBet(bet, nickname, true);
        } else if (player.getPlayerState() == PlayerState.PUT) {
            Card card = weakestValidCard(player);
            notifyError(nickname, "Tempo scaduto: ho giocato una carta per te");
            putCard(card.getSeed(), card.getValue(), nickname, true);
        }
    }

    private int lowestValidBet(GamePlayer player) {
        boolean isLastBettor = game.getBetsPlaced() == game.getPlayers().size() - 1;
        int otherBetsTotal = game.getPlayers().stream().mapToInt(GamePlayer::getBet).sum();
        for (int bet = 0; bet <= game.getSet(); bet++) {
            if (GameRules.isValidBet(bet, game.getSet(), isLastBettor, otherBetsTotal)) {
                return bet;
            }
        }
        throw new IllegalStateException("No valid bet for " + player.getNickname());
    }

    // Least likely to take the trick: not a briscola if possible, then the lowest strength
    private Card weakestValidCard(GamePlayer player) {
        List<Card> trick = game.getTableCard().getPlayedCards();
        Card lead = trick.isEmpty() ? null : trick.get(0);
        Card briscola = game.getTableCard().getBriscola();
        return player.getHand().stream()
                .filter(c -> GameRules.isValidCard(c, player.getHand(), lead))
                .min(Comparator.comparing((Card c) -> briscola != null && c.getSeed() == briscola.getSeed())
                        .thenComparing(Card::getValueForComparison))
                .orElseThrow();
    }

    private long turnMillisLeft(GamePlayer player) {
        boolean onTurn = player.getPlayerState() == PlayerState.BET || player.getPlayerState() == PlayerState.PUT;
        if (!onTurn || turnTime.isZero()) {
            return 0;
        }
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(turnDeadlineNanos - System.nanoTime()));
    }

    // Hands the state after this move to the store, which writes it in the background
    private void save() {
        if (started && !ended) {
            store.save(snapshot());
        }
    }

    MatchState snapshot() {
        return new MatchState(MatchState.VERSION, id, playersPerMatch, game.getMaxHandSize(), game.getSetsPlayed(),
                game.getRound(), game.getBetsPlaced(),
                game.getPlayers().stream().map(GamePlayer::toSeat).toList(),
                game.getLeftPlayers().stream().map(GamePlayer::toSeat).toList(),
                nicknamesInPlayOrder(),
                List.copyOf(game.getTableCard().getPlayedCards()),
                game.getTableCard().getBriscola(),
                Map.copyOf(timeoutsInARow));
    }

    private void endTurn(GamePlayer player) {
        player.updateState(PlayerState.WAIT);
        broadcast(new PlayerStateUpdate(PlayerState.WAIT, player.getNickname()), MessageType.PLAYER_STATE_UPDATE);
    }

    private GamePlayer findPlayer(String nickname) {
        try {
            return game.getPlayerByNickName(nickname);
        } catch (PlayerNickNameDoesNotExist e) {
            log.warn("Player {} is not in this match", nickname);
            return null;
        }
    }

    private List<GamePlayer> playOrder() {
        return game.getTableCard().getPlayerListOrder();
    }

    private List<String> nicknamesInPlayOrder() {
        return playOrder().stream().map(GamePlayer::getNickname).toList();
    }

    private void notifyError(String nickname, String text) {
        sendTo(nickname, new TextMessage(text), MessageType.TEXT_MESSAGE);
    }

    private void broadcast(ExecutableInClient executable, MessageType messageType) {
        notifier.forwardUpdateToAll(new Message(executable, messageType), game.getPlayers());
    }

    private void sendTo(String nickname, ExecutableInClient executable, MessageType messageType) {
        notifier.forwardUpdateToSingleClient(new Message(executable, messageType), nickname);
    }

    // Visible for tests
    Game getGame() {
        return game;
    }
}
