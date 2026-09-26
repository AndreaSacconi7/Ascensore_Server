package polimi.ascensore.network.server;

import org.springframework.stereotype.Service;
import polimi.ascensore.controller.CommandLoop;
import polimi.ascensore.controller.MasterController;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.ExecutableInServer;
import polimi.ascensore.network.newserver.MySocketHandler;

@Service
public class MasterServer {

    private final MasterController masterController;

    private final CommandLoop commandLoop;

    //TODO: conviene far si che questa rimanga come unico server che smista i comandi ai vari game controller. e in ognuno di essi pongo una coda che gli esegue
    public MasterServer(MasterController masterController, CommandLoop commandLoop) {
        System.out.println("MasterServer created");
        this.masterController = masterController;
        this.commandLoop = commandLoop;
    }

    // Called on WebSocket container threads: the command itself only ever runs on the command loop.
    public void addCommandToList(Command command) {
        commandLoop.submit(() -> executeExecutable(command.getExecutable()));
    }

    // Queued behind any command the session already sent, so those are processed before its cleanup.
    public void handleConnectionClosed(String sessionId) {
        commandLoop.submit(() -> masterController.handleConnectionClosed(sessionId));
    }

    public void putCard(Seed seed, int value, String sessionId) {
        masterController.putCard(seed, value, sessionId);
    }

    public void setBet(int bet, String sessionId) {
        masterController.setBet(bet, sessionId);
    }

    public void executeExecutable(ExecutableInServer executable) {
        System.out.println("ESEGUO EXECUTABLE INVIATO DAL CLIENT");
        if (executable != null) {
            executable.execute(this);
        }
    }

    //TODO: in disuso dopo introduzione di Supabase
    public void addClient(String nickName, String password, String clientSessionId) {

        //controller.addClientHandler(clientHandler);
        //masterController.firstLoginPlayer(nickName, clientSessionId);
    }

    public void setSocketHandler(MySocketHandler socketHandler) {
        masterController.setSocketHandler(socketHandler);
    }

    public void fetchPlayerInfo(String sessionId, String token, String nickname) {
        masterController.fetchPlayerInfo(sessionId, token, nickname);
    }

    public void joinGame(String sessionId) {
        masterController.addPlayerToGame(sessionId);
    }

    public void handlePlayerReconnection(String sessionId) {
    }

    public void logout(String clientSessionId) {
        masterController.logout(clientSessionId);
    }
}
