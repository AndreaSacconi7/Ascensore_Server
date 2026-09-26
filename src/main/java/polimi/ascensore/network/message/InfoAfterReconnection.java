package polimi.ascensore.network.message;

import polimi.ascensore.model.Card;

import java.util.Map;

/**
 * Everything a reconnecting player needs to rebuild the table, sent after STARTING_GAME, BRISCOLA_UPDATE
 * and HAND_UPDATE.
 */
public class InfoAfterReconnection implements ExecutableInClient {

    // Hand size of the current set
    private final int set;

    // Tricks already completed in the current set
    private final int round;

    // Sets completed before the current one
    private final int setsPlayed;

    private final int maxHandSize;

    private final Map<String, Integer> scores;

    private final Map<String, Integer> bets;

    private final Map<String, Integer> roundsWon;

    // Cards already on the table in the current trick, in play order
    private final Map<String, Card> playedCards;

    public InfoAfterReconnection(int set, int round, int setsPlayed, int maxHandSize, Map<String, Integer> scores,
                                 Map<String, Integer> bets, Map<String, Integer> roundsWon,
                                 Map<String, Card> playedCards) {
        this.set = set;
        this.round = round;
        this.setsPlayed = setsPlayed;
        this.maxHandSize = maxHandSize;
        this.scores = scores;
        this.bets = bets;
        this.roundsWon = roundsWon;
        this.playedCards = playedCards;
    }
}
