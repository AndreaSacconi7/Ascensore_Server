package polimi.ascensore.network.message;

import java.util.HashMap;

public class EndRoundUpdate implements ExecutableInClient{

    private final int nextRoundNumber;
    private final HashMap<String, Integer> nextPlayerOrderAndTaken;

    public EndRoundUpdate(int nextRoundNumber, HashMap<String, Integer> nextPlayerOrderAndTaken) {
        this.nextRoundNumber = nextRoundNumber;
        this.nextPlayerOrderAndTaken = nextPlayerOrderAndTaken;
    }

    public int getNextRoundNumber() {
        return nextRoundNumber;
    }

    public HashMap<String, Integer> getNextPlayerOrderAndTaken() {
        return nextPlayerOrderAndTaken;
    }
}
