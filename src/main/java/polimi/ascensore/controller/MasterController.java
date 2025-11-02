package polimi.ascensore.controller;

import org.springframework.stereotype.Service;
import polimi.ascensore.model.Lobby;
import polimi.ascensore.model.Player;
import polimi.ascensore.model.Seed;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.PlayerNicknameAlreadyExistException;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.newserver.MySocketHandler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static polimi.ascensore.model.Game.NUM_PLAYER;

@Service
public class MasterController {

    private final Lobby lobby;

    private MySocketHandler gameNotifications;

    private List<GameController> notStartedGameControllersList; //lista di game controller che gesticono partite non ancora iniziate

    private HashMap<String, GameController> playerGameMap; //mappa nickname giocatore -> id partita

    public MasterController() {
        lobby = new Lobby();
        notStartedGameControllersList = new LinkedList<>();
        playerGameMap = new HashMap<>();
    }

    //TODO: quando si sposterà il login su supabase, il primo messaggio scambiato
    // con il server java sarà fetcnhPlayerInfo quindi dovro porre lì il clientSessionId
    public void loginPlayer(String nickName, String clientSessionId) {

        LinkedList<String> connectedPlayers = new LinkedList<>();
        boolean isLogged = false;
        LoginResponse loginResponse;
        /*
        for(Player p: game.getPlayers()){
            connectedPlayers.add(p.getNickName());
        }*/

        try{
            lobby.checkIfValidLogin(nickName);
            lobby.addPlayerToLobby(nickName);
            isLogged = true;
            gameNotifications.addNicknameToSessionIdNode(nickName, clientSessionId);

        } catch (PlayerNicknameAlreadyExistException e) {
            System.out.println("Nickname already exists");
        }

        loginResponse = new LoginResponse(isLogged, nickName, connectedPlayers);

        notifySingleClient(loginResponse, nickName, MessageType.LOGIN_RESPONSE);

        //controllo condizione di inizio partita
        /*if(game.getPlayers().size() == NUM_PLAYER){
            startGame();
        }*/
    }

    public void fetchPlayerInfo(String token) {
        //TODO: implementare
        //gameNotifications.addNicknameToSessionIdNode(nickName, clientSessionId);
    }

    public void addPlayerToGame(String nickname){
        JoinGameResponse joinGameResponse;
        boolean isJoined = false;
        if(notStartedGameControllersList.isEmpty()){
            //creo una nuova partita
            GameController newGameController = new GameController();
            newGameController.setSocketHandler(gameNotifications);
            notStartedGameControllersList.add(newGameController);
        }
        for(GameController gc : notStartedGameControllersList){
            try {
                gc.addPlayerToGame(nickname);
                isJoined = true;
                System.out.println("Player " + nickname + " added to game controller");
                //TODO: potrei spostare il player dalla lobby a dentro il game. poi quando finisce la partita lo tiro fuori e lo rimetto in lobby
                //associo il giocatore alla partita
                playerGameMap.put(nickname, gc);

                joinGameResponse = new JoinGameResponse(isJoined, nickname);
                notifySingleClient(joinGameResponse, nickname, MessageType.JOIN_GAME_RESPONSE);

                if(gc.getNumPlayersInGame() == NUM_PLAYER){
                    //partita piena, la rimuovo dalla lista di quelle non ancora iniziate
                    notStartedGameControllersList.remove(gc);
                    //inizio partita
                    gc.startGame();
                }
                break;
            } catch (CannotAddPlayerNowException e) {
                //in teoria non si dovrebbe mai arrivare qui perchè se una partita è piena viene rimossa prima dalla lista (quando si connette l'ultimo player)
                System.out.println("Game already started in this controller, trying next game controller -----------------------------------------------------!!!");
                notStartedGameControllersList.remove(gc);

                joinGameResponse = new JoinGameResponse(isJoined, nickname);
                notifySingleClient(joinGameResponse, nickname, MessageType.JOIN_GAME_RESPONSE);
            }
        }
    }

    public void putCard(Seed seed, int value, String nickName) {
        GameController gameController = playerGameMap.get(nickName);
        gameController.putCard(seed, value, nickName);
    }

    public void setBet(int bet, String nickName) {
        GameController gameController = playerGameMap.get(nickName);
        gameController.setBet(bet, nickName);
    }


    public void setSocketHandler(MySocketHandler mySocketHandler) {

        this.gameNotifications = mySocketHandler;
    }

    public void notifySingleClient(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, nickname, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.forwardUpdateToSingleClient(message, nickname);
        }
    }
}
