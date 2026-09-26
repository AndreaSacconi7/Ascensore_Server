package polimi.ascensore.network.message;

import java.util.Map;

/**
 * A set is over: scores per player, keyed in the betting order of the next set.
 */
public class EndSetUpdate implements ExecutableInClient {

    // Hand size of the next set
    private final int nextSetNumber;

    // Sets completed so far: the next set is number setsPlayed + 1 of 2 * maxHandSize - 1
    private final int setsPlayed;

    // Insertion-ordered: the key order is the betting order, so it must not be a HashMap
    private final Map<String, Integer> nextPlayerOrderAndScore;

    public EndSetUpdate(int nextSetNumber, int setsPlayed, Map<String, Integer> nextPlayerOrderAndScore) {
        this.nextSetNumber = nextSetNumber;
        this.setsPlayed = setsPlayed;
        this.nextPlayerOrderAndScore = nextPlayerOrderAndScore;
    }

    public int getNextSetNumber() {
        return nextSetNumber;
    }

    public int getSetsPlayed() {
        return setsPlayed;
    }

    public Map<String, Integer> getNextPlayerOrderAndScore() {
        return nextPlayerOrderAndScore;
    }
}
