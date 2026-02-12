package polimi.ascensore.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.Lobby;
import polimi.ascensore.model.GamePlayer;
import polimi.ascensore.model.Seed;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.PlayerNicknameAlreadyExistException;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.newserver.MySocketHandler;
import polimi.ascensore.network.newserver.PlayerRepository;
import polimi.ascensore.network.newserver.SupabaseAuthService;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static polimi.ascensore.model.Game.NUM_PLAYER;

@Service
public class MasterController {

    private final Lobby lobby;

    private MySocketHandler gameNotifications;

    private List<GameController> notStartedGameControllersList; //lista di game controller che gesticono partite non ancora iniziate

    private HashMap<String, GameController> playerGameMap; //mappa nickname giocatore -> id partita

    @Autowired
    private SupabaseAuthService authService; // Il servizio che hai creato
    @Autowired
    private PlayerRepository playerRepository; // Il repository JPA

    public MasterController() {
        lobby = new Lobby();
        notStartedGameControllersList = new LinkedList<>();
        playerGameMap = new HashMap<>();
    }

    //TODO: quando si sposterà il login su supabase, il primo messaggio scambiato
    // con il server java sarà fetcnhPlayerInfo quindi dovro porre lì il clientSessionId
    public Player loginPlayer(String nickName, String clientSessionId, String supabaseId) {

        LinkedList<String> connectedPlayers = new LinkedList<>();
        boolean isLogged = false;
        LoginResponse loginResponse;

        Player player = null;
        try{
            lobby.checkIfValidLogin(nickName);
            player = lobby.addPlayerToLobby(nickName, supabaseId);
            isLogged = true;
            gameNotifications.addNicknameToSessionIdNode(nickName, clientSessionId);
            playerRepository.save(player); // Salviamo l'associazione per il futuro

        } catch (PlayerNicknameAlreadyExistException e) {
            System.out.println("Nickname already exists");
        }
        /*
        loginResponse = new LoginResponse(isLogged, nickName, connectedPlayers);

        //notifySingleClient(loginResponse, nickName, MessageType.LOGIN_RESPONSE);
        notifyClient(loginResponse, clientSessionId, MessageType.LOGIN_RESPONSE);
        */
        return player;
    }

    public void fetchPlayerInfo(String sessionId, String token, String nicknameOpzionale) {

        // 1. Estrai l'ID univoco dal token (garantito da Supabase)
        String supabaseUid = authService.validateAndGetUserId(token);

        if (supabaseUid == null) {
            //gameNotifications.closeSession(sessionId); // Token non valido
            return;
        }

        // 2. Cerchi nel DB se esiste già
        Optional<Player> playerOpt = playerRepository.findBySupabaseUid(supabaseUid);

        Player player;

        if (playerOpt.isPresent()) {
            // --- CASO A: AUTO-LOGIN (Utente già registrato) ---
            // NON ci serve il nickname dal client, usiamo quello che abbiamo nel DB!
            player = playerOpt.get();
            System.out.println("Bentornato " + player.getNickname());

        } else {
            // --- CASO B: PRIMO ACCESSO ASSOLUTO (Nuovo Utente) ---
            player = loginPlayer(nicknameOpzionale, sessionId, supabaseUid); // Questo metodo si occuperà di creare il Player e salvarlo nel DB
        }

        if(player != null) {
            System.out.println("Nuovo utente creato");
            // 3. Salva nella sessione
            gameNotifications.getSession(sessionId).getAttributes().put("PLAYER", player);

            PlayerInfoResponse response = new PlayerInfoResponse(player.getNickname(), true);
            Message message = new Message(response, MessageType.PLAYER_INFO_RESPONSE);

            System.out.println("Player info fetched for " + player.getNickname() + ", sending response...");
            // 5. Rispondi al client
            gameNotifications.sendMessageToClient(message, sessionId);
        }else{
            System.out.println("Errore nel login, player è null");

            PlayerInfoResponse response = new PlayerInfoResponse(player.getNickname(), false);
            Message message = new Message(response, MessageType.PLAYER_INFO_RESPONSE);

            gameNotifications.sendMessageToClient(message, sessionId);
        }
    }

    public void addPlayerToGame(String sessionId){
        Player player = getPlayerBySession(sessionId);

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
                gc.addPlayerToGame(player);
                isJoined = true;
                System.out.println("Player " + player.getNickname() + " added to game controller");
                //TODO: potrei spostare il player dalla lobby a dentro il game. poi quando finisce la partita lo tiro fuori e lo rimetto in lobby
                //associo il giocatore alla partita
                playerGameMap.put(player.getNickname(), gc);

                joinGameResponse = new JoinGameResponse(isJoined, player.getNickname());
                //notifySingleClient(joinGameResponse, player, MessageType.JOIN_GAME_RESPONSE);
                notifyClient(joinGameResponse, sessionId, MessageType.JOIN_GAME_RESPONSE);

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

                joinGameResponse = new JoinGameResponse(isJoined, player.getNickname());
                //notifySingleClient(joinGameResponse, player.getNickname(), nickname, MessageType.JOIN_GAME_RESPONSE);
                notifyClient(joinGameResponse, sessionId, MessageType.JOIN_GAME_RESPONSE);
            }
        }
    }

    public void putCard(Seed seed, int value, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        String nickname = player.getNickname();

        GameController gameController = playerGameMap.get(nickname);
        gameController.putCard(seed, value, nickname);
    }

    public void setBet(int bet, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        String nickname = player.getNickname();

        GameController gameController = playerGameMap.get(nickname);
        gameController.setBet(bet, nickname);
    }


    public void setSocketHandler(MySocketHandler mySocketHandler) {

        this.gameNotifications = mySocketHandler;
    }

    public void notifySingleClient(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.forwardUpdateToSingleClient(message, nickname);
        }
    }

    public void notifyClient(ExecutableInClient executable, String sessionId, MessageType messageType) {

        Message message = new Message(executable, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.sendMessageToClient(message, sessionId);
        }
    }

    public Player getPlayerBySession(String sessionId) {
        WebSocketSession session = gameNotifications.getSession(sessionId);
        if (session != null) {
            return (Player) session.getAttributes().get("PLAYER");
        }
        return null; // O lancia un'eccezione se preferisci
    }
}
