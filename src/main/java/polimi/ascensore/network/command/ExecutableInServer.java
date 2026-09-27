package polimi.ascensore.network.command;

import polimi.ascensore.network.websocket.CommandDispatcher;

/**
 * Payload of a client command. Runs on the lobby loop.
 */
public interface ExecutableInServer {

    void execute(CommandDispatcher commandDispatcher);

    // The socket the command arrived on: identifies the player, never trusted from the payload
    void setClientSessionId(String clientSessionId);
}
