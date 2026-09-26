package polimi.ascensore.network.message;

import polimi.ascensore.model.PlayerState;

/**
 * A player's state changed. For a turn (BET, PUT) it carries the time left to act, in milliseconds;
 * clients count down from when they receive it.
 */
public class PlayerStateUpdate implements ExecutableInClient {

    private final PlayerState playerState;

    private final String nickname;

    // Time left for this turn, and the full turn length; 0 when there is no time limit
    private final long turnMillisLeft;

    private final long turnMillis;

    public PlayerStateUpdate(PlayerState playerState, String nickname) {
        this(playerState, nickname, 0, 0);
    }

    public PlayerStateUpdate(PlayerState playerState, String nickname, long turnMillisLeft, long turnMillis) {
        this.playerState = playerState;
        this.nickname = nickname;
        this.turnMillisLeft = turnMillisLeft;
        this.turnMillis = turnMillis;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public String getNickname() {
        return nickname;
    }

    public long getTurnMillisLeft() {
        return turnMillisLeft;
    }
}
