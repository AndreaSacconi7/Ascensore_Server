package polimi.ascensore.network.message;

import java.util.Map;

/**
 * A trick is over: tricks taken per player, keyed in the play order of the next trick (winner first).
 */
public class EndRoundUpdate implements ExecutableInClient {

    private final int nextRoundNumber;

    // Insertion-ordered: the key order is the play order, so it must not be a HashMap
    private final Map<String, Integer> nextPlayerOrderAndTaken;

    public EndRoundUpdate(int nextRoundNumber, Map<String, Integer> nextPlayerOrderAndTaken) {
        this.nextRoundNumber = nextRoundNumber;
        this.nextPlayerOrderAndTaken = nextPlayerOrderAndTaken;
    }

    public int getNextRoundNumber() {
        return nextRoundNumber;
    }

    public Map<String, Integer> getNextPlayerOrderAndTaken() {
        return nextPlayerOrderAndTaken;
    }
}
