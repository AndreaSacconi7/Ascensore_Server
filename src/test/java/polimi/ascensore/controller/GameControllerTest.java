package polimi.ascensore.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.*;
import polimi.ascensore.network.message.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays whole matches through the controller with valid moves and checks the flow the clients see.
 */
class GameControllerTest {

    /** Records every message instead of sending it. */
    private static final class RecordingNotifier implements GameNotifier {
        final List<Message> broadcasts = new ArrayList<>();
        final List<Message> direct = new ArrayList<>();

        @Override
        public void forwardUpdateToAll(Message message, List<GamePlayer> playersInGame) {
            broadcasts.add(message);
        }

        @Override
        public void forwardUpdateToSingleClient(Message message, String nickname) {
            direct.add(message);
        }

        long count(MessageType type) {
            return broadcasts.stream().filter(m -> m.getMessageType() == type).count();
        }
    }

    private static final class Listener implements GameLifeCycleListener {
        int endedMatches = 0;

        @Override
        public void onGameEnded(GameController gameController) {
            endedMatches++;
        }
    }

    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Listener listener = new Listener();

    private GameController startMatch(int players, int maxHandSize) throws Exception {
        GameController controller = new GameController(listener, notifier, players, maxHandSize, new Random(42));
        for (int i = 0; i < players; i++) {
            controller.addPlayerToGame(new Player("uid-" + i, "player" + i), "session-" + i);
        }
        controller.startGame();
        return controller;
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void fullMatchPlaysEverySetAndEnds(int players) throws Exception {
        GameController controller = startMatch(players, 10);
        int[] tricksPerSet = playToTheEnd(controller);

        assertEquals(1, listener.endedMatches);
        assertEquals(1, notifier.count(MessageType.END_GAME));
        // 19 sets, only the last one is not followed by END_SET
        assertEquals(19, tricksPerSet.length);
        assertEquals(18, notifier.count(MessageType.END_SET));
        for (int set = 0; set < 19; set++) {
            assertEquals(GameRules.handSize(set, 10), tricksPerSet[set], "tricks in set " + set);
        }
    }

    @Test
    void finalStandingListsEveryPlayerByScore() throws Exception {
        GameController controller = startMatch(3, 3);
        playToTheEnd(controller);

        Message endGame = notifier.broadcasts.stream()
                .filter(m -> m.getMessageType() == MessageType.END_GAME).findFirst().orElseThrow();
        Map<String, Integer> result = ((EndGame) endGame.getExecutable()).getGameResult();
        assertEquals(3, result.size());
        List<Integer> scores = new ArrayList<>(result.values());
        for (int i = 1; i < scores.size(); i++) {
            assertTrue(scores.get(i - 1) >= scores.get(i), "standing not sorted: " + result);
        }
    }

    @Test
    void peakSetHasNoDealtBriscolaAndTheLeadCardSetsIt() throws Exception {
        // With a max hand of 1 the first set is the peak set
        GameController controller = startMatch(2, 1);
        Game game = controller.getGame();
        assertNull(game.getTableCard().getBriscola());

        controller.setBet(0, active(game).getNickname());
        controller.setBet(0, active(game).getNickname());
        assertEquals(2, game.getBetsPlaced());

        GamePlayer leader = active(game);
        Card lead = leader.getHand().get(0);
        controller.putCard(lead.getSeed(), lead.getValue(), leader.getNickname());
        assertSame(lead, game.getTableCard().getBriscola());
    }

    @Test
    void outOfTurnMovesAreRejectedAndChangeNothing() throws Exception {
        GameController controller = startMatch(2, 10);
        Game game = controller.getGame();
        GamePlayer waiting = game.getPlayers().stream()
                .filter(p -> p.getPlayerState() == PlayerState.WAIT).findFirst().orElseThrow();
        Card card = waiting.getHand().get(0);

        controller.putCard(card.getSeed(), card.getValue(), waiting.getNickname());
        controller.setBet(0, waiting.getNickname());

        assertEquals(1, waiting.getHand().size());
        assertEquals(0, game.getBetsPlaced());
        assertEquals(2, notifier.direct.stream().filter(m -> m.getMessageType() == MessageType.TEXT_MESSAGE).count());
    }

    @Test
    void lastBettorCannotMakeTheTotalEqualTheTricks() throws Exception {
        GameController controller = startMatch(2, 10);
        Game game = controller.getGame();
        controller.setBet(0, active(game).getNickname());
        GamePlayer last = active(game);

        // Hand size 1, other bets 0: a bet of 1 would make the total equal the single trick
        controller.setBet(1, last.getNickname());

        assertEquals(PlayerState.BET, last.getPlayerState());
        assertEquals(1, game.getBetsPlaced());
    }

    ///// Players leaving /////

    @Test
    void leavingOnYourTurnToBetPassesTheTurn() throws Exception {
        GameController controller = startMatch(3, 10);
        Game game = controller.getGame();
        GamePlayer bettor = active(game);

        controller.playerExitGame(bettor.getNickname());

        assertEquals(2, game.getPlayers().size());
        assertEquals(PlayerState.BET, active(game).getPlayerState());
        assertNotEquals(bettor, active(game));
        assertEquals(0, listener.endedMatches);
    }

    @Test
    void aBetFromAPlayerWhoLeftStopsCounting() throws Exception {
        GameController controller = startMatch(3, 10);
        Game game = controller.getGame();
        GamePlayer first = active(game);
        controller.setBet(0, first.getNickname());
        GamePlayer second = active(game);

        controller.playerExitGame(first.getNickname());

        assertEquals(0, game.getBetsPlaced());
        assertSame(second, active(game), "the player who was betting keeps the turn");
    }

    @Test
    void aCardFromAPlayerWhoLeftLeavesTheTrick() throws Exception {
        GameController controller = startMatch(3, 10);
        Game game = controller.getGame();
        playUntilCardsOnTable(controller, game, 1);
        GamePlayer leader = game.getTableCard().getPlayerListOrder().get(0);

        controller.playerExitGame(leader.getNickname());

        assertTrue(game.getTableCard().getPlayedCards().isEmpty());
        assertEquals(PlayerState.PUT, active(game).getPlayerState());
        playToTheEnd(controller);
    }

    @Test
    void aTwoPlayerMatchEndsWhenOneLeavesAndTheOtherWins() throws Exception {
        GameController controller = startMatch(2, 10);
        Game game = controller.getGame();
        GamePlayer leaver = game.getPlayers().get(0);
        GamePlayer stayer = game.getPlayers().get(1);

        controller.playerExitGame(leaver.getNickname());

        assertEquals(1, listener.endedMatches);
        Map<String, Integer> result = ((EndGame) notifier.broadcasts.stream()
                .filter(m -> m.getMessageType() == MessageType.END_GAME).findFirst().orElseThrow()
                .getExecutable()).getGameResult();
        assertEquals(List.of(stayer.getNickname(), leaver.getNickname()), List.copyOf(result.keySet()));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void matchesSurvivePlayersLeavingAtAnyMoment(int players) throws Exception {
        Random chaos = new Random(players);
        for (int round = 0; round < 150; round++) {
            listener.endedMatches = 0;
            notifier.broadcasts.clear();
            GameController controller = startMatch(players, 1 + chaos.nextInt(Math.min(10, 40 / players)));
            Game game = controller.getGame();
            int leaves = 1 + chaos.nextInt(players - 1);
            for (int step = 0; step < 100_000 && listener.endedMatches == 0; step++) {
                if (leaves > 0 && chaos.nextInt(12) == 0) {
                    List<GamePlayer> inGame = game.getPlayers();
                    controller.playerExitGame(inGame.get(chaos.nextInt(inGame.size())).getNickname());
                    leaves--;
                    continue;
                }
                GamePlayer actor = active(game);
                if (actor.getPlayerState() == PlayerState.BET) {
                    controller.setBet(validBet(game), actor.getNickname());
                } else {
                    Card card = validCard(game, actor);
                    controller.putCard(card.getSeed(), card.getValue(), actor.getNickname());
                }
            }
            assertEquals(1, listener.endedMatches, "match " + round + " did not end");
            Map<String, Integer> result = ((EndGame) notifier.broadcasts.stream()
                    .filter(m -> m.getMessageType() == MessageType.END_GAME).reduce((a, b) -> b).orElseThrow()
                    .getExecutable()).getGameResult();
            assertEquals(players, result.size(), "everyone appears in the final standing");
        }
    }

    // Plays valid moves until the current trick holds the given number of cards
    private void playUntilCardsOnTable(GameController controller, Game game, int cards) {
        while (game.getTableCard().getPlayedCards().size() < cards) {
            GamePlayer actor = active(game);
            if (actor.getPlayerState() == PlayerState.BET) {
                controller.setBet(validBet(game), actor.getNickname());
            } else {
                Card card = validCard(game, actor);
                controller.putCard(card.getSeed(), card.getValue(), actor.getNickname());
            }
        }
    }

    // Plays valid moves until the match ends; returns the number of tricks played in each set
    private int[] playToTheEnd(GameController controller) {
        Game game = controller.getGame();
        List<Integer> tricksPerSet = new ArrayList<>();
        int tricksInSet = 0;
        for (int step = 0; step < 100_000 && listener.endedMatches == 0; step++) {
            GamePlayer actor = active(game);
            int roundBefore = game.getRound();
            int setsBefore = game.getSetsPlayed();
            if (actor.getPlayerState() == PlayerState.BET) {
                controller.setBet(validBet(game), actor.getNickname());
            } else {
                Card card = validCard(game, actor);
                controller.putCard(card.getSeed(), card.getValue(), actor.getNickname());
                if (listener.endedMatches > 0) {
                    tricksPerSet.add(tricksInSet + 1);
                } else if (game.getSetsPlayed() > setsBefore) {
                    tricksPerSet.add(tricksInSet + 1);
                    tricksInSet = 0;
                } else if (game.getRound() > roundBefore) {
                    tricksInSet++;
                }
            }
        }
        assertEquals(1, listener.endedMatches, "match did not end");
        return tricksPerSet.stream().mapToInt(Integer::intValue).toArray();
    }

    // The single player whose turn it is
    private static GamePlayer active(Game game) {
        List<GamePlayer> active = game.getPlayers().stream()
                .filter(p -> p.getPlayerState() == PlayerState.BET || p.getPlayerState() == PlayerState.PUT)
                .toList();
        assertEquals(1, active.size(), "exactly one player must be on turn");
        return active.get(0);
    }

    private static int validBet(Game game) {
        boolean isLast = game.getBetsPlaced() == game.getPlayers().size() - 1;
        int others = game.getPlayers().stream().mapToInt(GamePlayer::getBet).sum();
        for (int bet = 0; bet <= game.getSet(); bet++) {
            if (GameRules.isValidBet(bet, game.getSet(), isLast, others)) {
                return bet;
            }
        }
        throw new AssertionError("no valid bet");
    }

    private static Card validCard(Game game, GamePlayer player) {
        List<Card> trick = game.getTableCard().getPlayedCards();
        Card lead = trick.isEmpty() ? null : trick.get(0);
        return player.getHand().stream()
                .filter(c -> GameRules.isValidCard(c, player.getHand(), lead))
                .findFirst().orElseThrow();
    }
}
