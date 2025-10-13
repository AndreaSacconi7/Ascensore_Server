package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;
import polimi.ascensore.model.Seed;

public class PutCard implements ExecutableInServer{

    private final Seed seed;
    private final int value;
    private final String nickname;

    public PutCard(Seed seed, int value, String nickname) {
        this.seed = seed;
        this.value = value;
        this.nickname = nickname;
    }

    @Override
    public void execute(MasterServer masterServer) {
        masterServer.putCard(seed, value, nickname);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {

    }
}
