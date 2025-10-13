package polimi.ascensore.network.command;

import polimi.ascensore.network.server.MasterServer;

public class SetBet implements ExecutableInServer{

    private final int bet;
    private final String nickname;

    public SetBet(int bet, String nickname) {
        this.bet = bet;
        this.nickname = nickname;
    }

    @Override
    public void execute(MasterServer masterServer) {
        masterServer.setBet(bet, nickname);
    }

    @Override
    public void setClientSessionId(String clientSessionId) {

    }
}
