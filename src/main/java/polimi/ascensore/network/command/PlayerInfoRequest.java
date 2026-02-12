package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

public class PlayerInfoRequest implements ExecutableInServer{

    final String token;
    String sessionId;
    String nickname;

    public PlayerInfoRequest(String token, String nickname) {
        this.token = token;
        this.nickname = nickname;
    }


    @Override
    public void execute(MasterServer masterServer) {
        masterServer.fetchPlayerInfo(sessionId, token, nickname);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {
        this.sessionId = clientSessionId;
    }
}
