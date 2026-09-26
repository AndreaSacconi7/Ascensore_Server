package polimi.ascensore.network.command;

import polimi.ascensore.network.websocket.CommandDispatcher;

public class SetBet implements ExecutableInServer {

    private int bet;

    private transient String clientSessionId;

    @Override
    public void execute(CommandDispatcher commandDispatcher) {
        commandDispatcher.setBet(bet, clientSessionId);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }
}
