package polimi.ascensore.network.command;

import polimi.ascensore.network.websocket.CommandDispatcher;

/**
 * Leave matchmaking, or the match in progress for good.
 */
public class LeaveGameRequest implements ExecutableInServer {

    private transient String clientSessionId;

    @Override
    public void execute(CommandDispatcher commandDispatcher) {
        commandDispatcher.leaveGame(clientSessionId);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }
}
