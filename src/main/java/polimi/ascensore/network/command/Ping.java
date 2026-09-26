package polimi.ascensore.network.command;

import polimi.ascensore.network.websocket.CommandDispatcher;

/**
 * Heartbeat. Answered with PONG directly by the socket handler; it never reaches the command loop.
 */
public class Ping implements ExecutableInServer {

    @Override
    public void execute(CommandDispatcher commandDispatcher) {
        // Nothing to do: see GameWebSocketHandler
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
    }
}
