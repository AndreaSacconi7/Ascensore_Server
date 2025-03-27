package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

import java.io.ObjectOutputStream;

import polimi.ascensore.network.server.SocketClientHandler;

public class ConnectionRequest implements ExecutableInServer{

    private ObjectOutputStream output;
    /**
     * The player's nickname
     */
    private final String nickname;

    /**
     * Constructs a connection request with the specified nickname and color.
     * @param nickname the player's nickname
     */
    public ConnectionRequest(String nickname){
        this.nickname = nickname;
        this.output=null;
    }

    /**
     * Method that executes the connection request. It creates a new socket client handler and connects
     * it to the server.
     * @param masterServer the server that receives the connection request
     */
    @Override
    public void execute(MasterServer masterServer) {

        SocketClientHandler clientHandler = new SocketClientHandler(output, nickname);
        masterServer.addClientHandler(clientHandler, nickname);
    }

    public void setOutput(ObjectOutputStream output) {
        this.output = output;
    }

    /**
     * @return the player's nickname
     */
    public String getNickName() {
        return this.nickname;
    }
}
