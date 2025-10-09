package polimi.ascensore.network.message;

import polimi.ascensore.model.PlayerState;

public class PlayerStateUpdate implements ExecutableInClient {

    private final PlayerState playerState;

    private final String nickname;

    public PlayerStateUpdate(PlayerState playerState, String nickname) {
        this.playerState = playerState;
        this.nickname = nickname;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public String getNickname() {
        return nickname;
    }
}
