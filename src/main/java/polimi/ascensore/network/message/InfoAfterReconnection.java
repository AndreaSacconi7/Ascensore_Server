package polimi.ascensore.network.message;

import java.util.HashMap;

public class InfoAfterReconnection implements ExecutableInClient {

    private final HashMap<String, Integer> scores;

    private final HashMap<String, Integer> bets;

    private final HashMap<String, Integer> roundsWon;

    public InfoAfterReconnection(HashMap<String, Integer> scores, HashMap<String, Integer> bets, HashMap<String, Integer> roundsWon) {
        this.scores = scores;
        this.bets = bets;
        this.roundsWon = roundsWon;
    }
}
