package polimi.ascensore.network.message;

import java.util.List;

/**
 * Who is waiting in a match that has not started, sent to them whenever someone joins or leaves.
 */
public class WaitingRoomUpdate implements ExecutableInClient {

    private final int playersPerMatch;

    // Nicknames in joining order
    private final List<String> players;

    public WaitingRoomUpdate(int playersPerMatch, List<String> players) {
        this.playersPerMatch = playersPerMatch;
        this.players = players;
    }

    public int getPlayersPerMatch() {
        return playersPerMatch;
    }

    public List<String> getPlayers() {
        return players;
    }
}
