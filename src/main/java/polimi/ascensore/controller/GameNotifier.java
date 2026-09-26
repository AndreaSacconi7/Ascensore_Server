package polimi.ascensore.controller;

import polimi.ascensore.model.GamePlayer;
import polimi.ascensore.network.message.Message;

import java.util.List;

/**
 * Outgoing side of a match: how a {@link GameController} reaches its players.
 */
public interface GameNotifier {

    void forwardUpdateToAll(Message message, List<GamePlayer> playersInGame);

    void forwardUpdateToSingleClient(Message message, String nickname);
}
