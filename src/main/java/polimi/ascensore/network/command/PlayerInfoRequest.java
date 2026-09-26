package polimi.ascensore.network.command;

import polimi.ascensore.network.websocket.CommandDispatcher;

public class PlayerInfoRequest implements ExecutableInServer {

    // Supabase access token
    private String token;

    // Only read when the player has no public nickname yet; empty otherwise
    private String nickname;

    private transient String clientSessionId;

    @Override
    public void execute(CommandDispatcher commandDispatcher) {
        commandDispatcher.fetchPlayerInfo(clientSessionId, token, nickname);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }
}
