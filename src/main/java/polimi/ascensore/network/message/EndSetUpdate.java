package polimi.ascensore.network.message;

import java.util.HashMap;

public class EndSetUpdate implements ExecutableInClient {

    private final int nextSetNumber;
    private final HashMap<String, Integer> nextPlayerOrderAndScore;

    public EndSetUpdate(int nextSetNumber, HashMap<String, Integer> nextPlayerOrderAndScore) {
        this.nextSetNumber = nextSetNumber;
        this.nextPlayerOrderAndScore = nextPlayerOrderAndScore;
    }

    public int getNextSetNumber() {
        return nextSetNumber;
    }

    public HashMap<String, Integer> getNextPlayerOrderAndScore() {
        return nextPlayerOrderAndScore;
    }
}
