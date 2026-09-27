package polimi.ascensore.network.websocket;

import org.springframework.stereotype.Service;
import polimi.ascensore.controller.GameLoops;
import polimi.ascensore.controller.MasterController;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.command.Command;

/**
 * Bridge between the socket threads and the game: queues every command on the lobby loop (see
 * {@link GameLoops}) and, once there, routes it to the {@link MasterController}.
 */
@Service
public class CommandDispatcher {

    private final MasterController masterController;

    private final GameLoops loops;

    public CommandDispatcher(MasterController masterController, GameLoops loops) {
        this.masterController = masterController;
        this.loops = loops;
    }

    // Called on WebSocket container threads: the command itself only ever runs on the lobby loop.
    public void addCommandToList(Command command) {
        loops.lobby().execute(() -> command.getExecutable().execute(this));
    }

    // Queued behind any command the session already sent, so those are processed before its cleanup.
    public void handleConnectionClosed(String sessionId) {
        loops.lobby().execute(() -> masterController.handleConnectionClosed(sessionId));
    }

    public void setSocketHandler(GameWebSocketHandler socketHandler) {
        masterController.setSocketHandler(socketHandler);
    }

    // Called by the commands, on the lobby loop

    public void fetchPlayerInfo(String sessionId, String token, String nickname) {
        masterController.fetchPlayerInfo(sessionId, token, nickname);
    }

    public void joinGame(String sessionId, Integer players) {
        masterController.addPlayerToGame(sessionId, players);
    }

    public void leaveGame(String sessionId) {
        masterController.leaveGame(sessionId);
    }

    public void putCard(Seed seed, int value, String sessionId) {
        masterController.putCard(seed, value, sessionId);
    }

    public void setBet(int bet, String sessionId) {
        masterController.setBet(bet, sessionId);
    }

    public void logout(String sessionId) {
        masterController.logout(sessionId);
    }
}
