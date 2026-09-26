package polimi.ascensore.network.command;

import polimi.ascensore.network.websocket.CommandDispatcher;

public class JoinGameRequest implements ExecutableInServer {

    private transient String clientSessionId;

    @Override
    public void execute(CommandDispatcher commandDispatcher) {
        commandDispatcher.joinGame(clientSessionId);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }
}
