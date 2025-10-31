package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

public class PlayerInfoRequest implements ExecutableInServer{

    String token;

    public PlayerInfoRequest(String token) {
        this.token = token;
    }


    @Override
    public void execute(MasterServer masterServer) {
        masterServer.fetchPlayerInfo(token);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {

    }
}
