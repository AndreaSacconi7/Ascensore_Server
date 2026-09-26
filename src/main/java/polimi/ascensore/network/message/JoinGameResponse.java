package polimi.ascensore.network.message;

/**
 * The player is seated in a match that is waiting for players.
 */
public class JoinGameResponse implements ExecutableInClient {

    private final String nickname;

    private final boolean isJoined;

    private final int playersPerMatch;

    public JoinGameResponse(boolean isJoined, String nickname, int playersPerMatch) {
        this.nickname = nickname;
        this.isJoined = isJoined;
        this.playersPerMatch = playersPerMatch;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean getIsJoined() {
        return isJoined;
    }

    public int getPlayersPerMatch() {
        return playersPerMatch;
    }
}
