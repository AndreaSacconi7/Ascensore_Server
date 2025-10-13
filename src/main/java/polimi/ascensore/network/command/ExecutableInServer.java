package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

import java.io.Serializable;

public interface ExecutableInServer extends Serializable {

    public void execute(MasterServer masterServer);

    //metodo per settare la client session id ma serve solo per connectionRequest in modo da collegare il channel al nickname
    public void setClientSessionId(String clientSessionId);
}
