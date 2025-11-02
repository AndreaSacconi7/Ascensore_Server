package polimi.ascensore.model;

import polimi.ascensore.model.exception.PlayerNicknameAlreadyExistException;

import java.util.HashMap;

public class Lobby {

    HashMap<String, Player> onlinePlayers;

    public Lobby() {
        onlinePlayers = new HashMap<>();
    }

    public void addPlayerToLobby(String nickname) {
        onlinePlayers.put(nickname, new Player(nickname));
    }

    public Player getPlayerByNickname(String nickname) {
        return onlinePlayers.get(nickname);
    }

    //TODO: facendo auth su supabase, questa funzione non servirà più
    public void checkIfValidLogin(String nickName) throws PlayerNicknameAlreadyExistException {
        for(String s : onlinePlayers.keySet()) {
            if(s.equals(nickName))
                throw new PlayerNicknameAlreadyExistException();
        }
    }


}
