package polimi.ascensore.model;

import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.exception.PlayerNicknameAlreadyExistException;

import java.util.HashMap;

public class Lobby {

    HashMap<String, Player> onlinePlayers;

    public Lobby() {
        onlinePlayers = new HashMap<>();
    }

    public Player addPlayerToLobby(String nickname, String supabaseId) {
        Player player = new Player(supabaseId, nickname);
        onlinePlayers.put(nickname, player);
        return player;
    }

    public void removePlayerFromLobby(String nickname) {
        onlinePlayers.remove(nickname);
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
