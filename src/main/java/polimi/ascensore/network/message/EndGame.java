package polimi.ascensore.network.message;

import java.util.Map;

/**
 * Final scores, keyed by final standing (winner first).
 */
public class EndGame implements ExecutableInClient {

    private final Map<String, Integer> gameResult;

    public EndGame(Map<String, Integer> gameResult) {
        this.gameResult = gameResult;
    }

    public Map<String, Integer> getGameResult() {
        return gameResult;
    }
}
