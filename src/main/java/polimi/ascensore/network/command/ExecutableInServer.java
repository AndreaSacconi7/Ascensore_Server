package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

import java.io.Serializable;

public interface ExecutableInServer extends Serializable {

    public void execute(MasterServer masterServer);

    public void setClientSessionId(String clientSessionId);
}
