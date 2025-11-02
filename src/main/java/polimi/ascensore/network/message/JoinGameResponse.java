package polimi.ascensore.network.message;

public class JoinGameResponse implements ExecutableInClient{

    final String nickname;

    final boolean isJoined;

    public JoinGameResponse(boolean isJoined, String nickname) {
        this.nickname = nickname;
        this.isJoined = isJoined;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean getIsJoined() {
        return isJoined;
    }
}
