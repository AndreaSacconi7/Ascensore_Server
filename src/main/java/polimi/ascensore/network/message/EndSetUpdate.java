package polimi.ascensore.network.message;

import java.util.Map;

/**
 * A set is over: scores per player, keyed in the betting order of the next set.
 */
public class EndSetUpdate implements ExecutableInClient {

    // Hand size of the next set
    private final int nextSetNumber;

    // Insertion-ordered: the key order is the betting order, so it must not be a HashMap
    private final Map<String, Integer> nextPlayerOrderAndScore;

    public EndSetUpdate(int nextSetNumber, Map<String, Integer> nextPlayerOrderAndScore) {
        this.nextSetNumber = nextSetNumber;
        this.nextPlayerOrderAndScore = nextPlayerOrderAndScore;
    }

    public int getNextSetNumber() {
        return nextSetNumber;
    }

    public Map<String, Integer> getNextPlayerOrderAndScore() {
        return nextPlayerOrderAndScore;
    }
}
