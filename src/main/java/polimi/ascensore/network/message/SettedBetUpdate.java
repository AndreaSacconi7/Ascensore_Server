package polimi.ascensore.network.message;

public class SettedBetUpdate implements ExecutableInClient {
    private final String nickname;
    private final int bet;

    public SettedBetUpdate(String nickname, int bet) {
        this.nickname = nickname;
        this.bet = bet;
    }

    public String getNickname() {
        return nickname;
    }

    public int getBet() {
        return bet;
    }
}
