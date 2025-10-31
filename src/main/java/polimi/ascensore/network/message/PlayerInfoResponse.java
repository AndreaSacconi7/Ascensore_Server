package polimi.ascensore.network.message;

public class PlayerInfoResponse implements ExecutableInClient{

    final String nickname;

    public PlayerInfoResponse(String nickname) {
        this.nickname = nickname;
    }

    public String getNickname() {
        return nickname;
    }
}
