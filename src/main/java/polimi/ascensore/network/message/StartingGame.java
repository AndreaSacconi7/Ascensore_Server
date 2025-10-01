package polimi.ascensore.network.message;

import polimi.ascensore.model.Player;

import java.util.List;

public class StartingGame implements ExecutableInClient {

    private final List<String> connectedPlayers;

    public StartingGame(List<String> connectedPlayers) {
        this.connectedPlayers = connectedPlayers;
    }

    public List<String> getConnectedPlayers() {
        return connectedPlayers;
    }
}
