package polimi.ascensore.network.message;

import java.util.HashMap;
import java.util.LinkedList;

public class LoginResponse implements ExecutableInClient {

    private final boolean isLogged;

    private final String nickname;

    private final LinkedList<String> connectedPlayers;

    public LoginResponse(boolean isLogged, String nickname, LinkedList<String> connectedPlayers){
        this.isLogged = isLogged;
        this.nickname = nickname;
        this.connectedPlayers = connectedPlayers;
    }
}
