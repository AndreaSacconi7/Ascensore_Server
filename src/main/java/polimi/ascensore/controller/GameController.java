package polimi.ascensore.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.*;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.InvalidCard;
import polimi.ascensore.model.exception.PlayerNickNameDoesNotExist;
import polimi.ascensore.network.message.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs one match: validates the players' moves against the rules, advances turns, tricks and sets,
 * and tells every player what changed. Only ever called from the command loop.
 */
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    // Final score reported for a player who left the match
    private static final int EXIT_SCORE = -500;

    private final Game game;

    private final GameNotifier notifier;

    private final GameLifeCycleListener gameLifeCycleListener;

    public GameController(GameLifeCycleListener gameLifeCycleListener, GameNotifier notifier,
                          int maxHandSize, Random random) {
        this.game = new Game(maxHandSize, random);
        this.notifier = notifier;
        this.gameLifeCycleListener = gameLifeCycleListener;
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
    }

    public void setBet(int bet, String nickname) {
        GamePlayer player = findPlayer(nickname);
        if (player == null) {
            return;
        }
        if (player.getPlayerState() != PlayerState.BET) {
            notifyError(nickname, "It is not your turn to bet");
            return;
        }
        boolean isLastBettor = game.getBetsPlaced() == game.getPlayers().size() - 1;
        int otherBetsTotal = game.getPlayers().stream().mapToInt(GamePlayer::getBet).sum();
        if (!GameRules.isValidBet(bet, game.getSet(), isLastBettor, otherBetsTotal)) {
            notifyError(nickname, "Invalid bet: bets go from 0 to " + game.getSet()
                    + " and the last bet cannot make the total equal the number of tricks");
            return;
        }

        player.updateBet(bet);
        game.registerBet();
        broadcast(new SettedBetUpdate(nickname, bet), MessageType.SETTED_BET);
        endTurn(player);

        if (game.getBetsPlaced() < game.getPlayers().size()) {
            giveTurn(playOrder().get(game.getBetsPlaced()), PlayerState.BET);
        } else {
            // Everyone has bet: the first player in order leads the first trick
            giveTurn(playOrder().get(0), PlayerState.PUT);
        }
    }

    public void putCard(Seed seed, int value, String nickname) {
        GamePlayer player = findPlayer(nickname);
        if (player == null) {
            return;
        }
        if (player.getPlayerState() != PlayerState.PUT) {
            notifyError(nickname, "It is not your turn to play");
            return;
        }
        Card card = player.getHand().stream()
                .filter(c -> c.getSeed() == seed && c.getValue() == value)
                .findFirst()
                .orElse(null);
        if (card == null) {
            notifyError(nickname, "You do not have that card");
            return;
        }
        List<Card> trick = game.getTableCard().getPlayedCards();
        Card leadCard = trick.isEmpty() ? null : trick.get(0);
        if (!GameRules.isValidCard(card, player.getHand(), leadCard)) {
            notifyError(nickname, "You must follow the seed of the first card played");
            return;
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

    public void playerExitGame(String nickname) {
        GamePlayer player = findPlayer(nickname);
        if (player != null) {
            // Players who left cannot win
            player.updateState(PlayerState.EXIT);
        }
        broadcast(new PlayerExitGame(nickname), MessageType.PLAYER_EXIT_GAME);

        // A match cannot continue with a missing player yet, so it ends for everyone
        endGameResult();
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
        // Every player's state, so the client knows whose turn it is (including its own)
        for (GamePlayer p : playOrder()) {
            sendTo(nickname, new PlayerStateUpdate(p.getPlayerState(), p.getNickname()),
                    MessageType.PLAYER_STATE_UPDATE);
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
        giveTurn(playOrder().get(0), PlayerState.PUT);
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
        giveTurn(playOrder().get(0), PlayerState.BET);
    }

    private void endGameResult() {
        Map<String, Integer> resultAndScore = new LinkedHashMap<>();
        for (GamePlayer p : game.endGame()) {
            resultAndScore.put(p.getNickname(), p.getPlayerState() == PlayerState.EXIT ? EXIT_SCORE : p.getScore());
        }
        broadcast(new EndGame(resultAndScore), MessageType.END_GAME);

        // Lets the master controller forget this match
        gameLifeCycleListener.onGameEnded(this);
    }

    private void notifyDistributedCards() {
        for (GamePlayer p : game.getPlayers()) {
            sendTo(p.getNickname(), new HandUpdate(p.getHand()), MessageType.HAND_UPDATE);
        }
        broadcast(new BriscolaUpdate(game.getTableCard().getBriscola()), MessageType.BRISCOLA_UPDATE);
    }

    private void giveTurn(GamePlayer player, PlayerState state) {
        player.updateState(state);
        broadcast(new PlayerStateUpdate(state, player.getNickname()), MessageType.PLAYER_STATE_UPDATE);
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
