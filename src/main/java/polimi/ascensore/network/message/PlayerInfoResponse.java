package polimi.ascensore.network.message;

public class PlayerInfoResponse implements ExecutableInClient{

    final String nickname;
    final Boolean isLogged;

    public PlayerInfoResponse(String nickname, Boolean isLogged) {
        this.nickname = nickname;
        this.isLogged = isLogged;
    }

    public String getNickname() {
        return nickname;
    }
}
