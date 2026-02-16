package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

public class Logout implements ExecutableInServer{

    String clientSessionId;

    @Override
    public void execute(MasterServer masterServer) {
        masterServer.logout(clientSessionId);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }
}
