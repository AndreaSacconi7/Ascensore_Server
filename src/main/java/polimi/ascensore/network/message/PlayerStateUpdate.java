package polimi.ascensore.network.message;

import polimi.ascensore.model.PlayerState;

public class PlayerStateUpdate implements ExecutableInClient {

    private final PlayerState playerState;

    public PlayerStateUpdate(PlayerState playerState) {
        this.playerState = playerState;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }
}
