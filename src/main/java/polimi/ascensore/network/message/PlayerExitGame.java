package polimi.ascensore.network.message;

public class PlayerExitGame implements ExecutableInClient {

    final String nickname;

    public PlayerExitGame(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() {
        return nickname;
    }
}
