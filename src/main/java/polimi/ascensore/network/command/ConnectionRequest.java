package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

import java.io.ObjectOutputStream;

public class ConnectionRequest implements ExecutableInServer{

    //private ObjectOutputStream output;
    /**
     * The player's nickname
     */
    private final String nickname;

    private final String password;

    /**
     * Constructs a connection request with the specified nickname and color.
     * @param nickname the player's nickname
     */
    public ConnectionRequest(String nickname, String password) {
        this.nickname = nickname;
        //this.output=null;
        this.password = null;
    }

    /**
     * Method that executes the connection request. It creates a new socket client handler and connects
     * it to the server.
     * @param masterServer the server that receives the connection request
     */
    @Override
    public void execute(MasterServer masterServer) {

        //MySocketHandler clientHandler = new MySocketHandler(output, nickname);
        masterServer.addClient(nickname, password);
    }

    public void setOutput(ObjectOutputStream output) {
        //this.output = output;
    }

    /**
     * @return the player's nickname
     */
    public String getNickName() {
        return this.nickname;
    }
}
