package polimi.ascensore.network.message;

import java.util.List;

/**
 * A match starts (or is replayed to a reconnecting player).
 */
public class StartingGame implements ExecutableInClient {

    // Nicknames in betting order
    private final List<String> connectedPlayers;

    // Largest hand of the match: hands go 1..maxHandSize..1
    private final int maxHandSize;

    public StartingGame(List<String> connectedPlayers, int maxHandSize) {
        this.connectedPlayers = connectedPlayers;
        this.maxHandSize = maxHandSize;
    }

    public List<String> getConnectedPlayers() {
        return connectedPlayers;
    }

    public int getMaxHandSize() {
        return maxHandSize;
    }
}
