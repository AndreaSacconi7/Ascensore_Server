package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

public class JoinGameRequest implements ExecutableInServer{

    final String nickname;

    public JoinGameRequest(String nickname) {
        this.nickname = nickname;
    }

    @Override
    public void execute(MasterServer masterServer) {
        masterServer.joinGame(nickname);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {

    }
}
