package polimi.ascensore.network.command;

import polimi.ascensore.model.Seed;
import polimi.ascensore.network.websocket.CommandDispatcher;

public class PutCard implements ExecutableInServer {

    private Seed seed;

    private int value;

    private transient String clientSessionId;

    @Override
    public void execute(CommandDispatcher commandDispatcher) {
        commandDispatcher.putCard(seed, value, clientSessionId);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }
}
