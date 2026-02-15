package polimi.ascensore.network.message;

import polimi.ascensore.model.GamePlayer;

import java.util.HashMap;
import java.util.List;

public class EndGame implements ExecutableInClient {

    private final HashMap<String, Integer> gameResult;

    public EndGame(HashMap<String, Integer> gameResult) {
        this.gameResult = gameResult;
    }

    public HashMap<String, Integer> getGameResult() {
        return gameResult;
    }
}
