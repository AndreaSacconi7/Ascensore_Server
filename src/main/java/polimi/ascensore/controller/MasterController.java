package polimi.ascensore.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import polimi.ascensore.JPA.Player;
import polimi.ascensore.JPA.PlayerConnection;
import polimi.ascensore.model.Lobby;
import polimi.ascensore.model.Seed;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.PlayerNicknameAlreadyExistException;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.newserver.MySocketHandler;
import polimi.ascensore.network.newserver.PlayerRepository;
import polimi.ascensore.network.newserver.SupabaseAuthService;

import java.util.*;
import java.util.concurrent.*;

import static polimi.ascensore.model.Game.NUM_PLAYER;

@Service
public class MasterController implements GameLifeCycleListener {

    private final Lobby lobby;

    private MySocketHandler gameNotifications;

    private List<GameController> notStartedGameControllersList; //lista di game controller che gesticono partite non ancora iniziate

    private HashMap<String, GameController> playerGameMap; //mappa supabaseID giocatore -> id partita

    @Autowired
    private SupabaseAuthService authService; // Il servizio che hai creato
    @Autowired
    private PlayerRepository playerRepository; // Il repository JPA

    ////DISCONNECTION HANDLING/////

    // Mappa per salvare i timer attivi: PlayerUUID -> ScheduledFuture
    private final Map<String, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();

    // Il motore che esegue i timer in background
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Tempo di attesa prima della disconnessione definitiva (es. 60 secondi)
    private static final int DISCONNECT_TIMEOUT = 60;


    public MasterController() {
        lobby = new Lobby();
        notStartedGameControllersList = new LinkedList<>();
        playerGameMap = new HashMap<>();
    }

    //TODO: quando si sposterà il login su supabase, il primo messaggio scambiato
    // con il server java sarà fetcnhPlayerInfo quindi dovro porre lì il clientSessionId
    public Player firstLoginPlayer(String nickName, String clientSessionId, String supabaseId) {

        LinkedList<String> connectedPlayers = new LinkedList<>();
        boolean isLogged = false;
        LoginResponse loginResponse;

        Player player = null;
        try{
            lobby.checkIfValidLogin(nickName);
            player = lobby.addPlayerToLobby(nickName, supabaseId);
            //isLogged = true;
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
            gameNotifications.addNicknameToSessionIdNode(player.getNickname(), sessionId);
            // Controlla se il giocatore è già in una partita (ovvero se gli è caduta la connessione e sta cercando di rientrare)
            boolean playerInGame = checkIfPlayerInGame(sessionId);
            if(playerInGame){
                //mettere il giocatore in partita, ovvero ricollegarlo alla partita a cui stava giocando prima della disconnessione
                handlePlayerReconnection(player);
            }
        } else {
            // --- CASO B: PRIMO ACCESSO ASSOLUTO (Nuovo Utente) ---
            player = firstLoginPlayer(nicknameOpzionale, sessionId, supabaseUid); // Questo metodo si occuperà di creare il Player e salvarlo nel DB
        }

        if(player != null) {
            System.out.println("Player logged");
            // 3. Salva nella sessione
            gameNotifications.getSession(sessionId).getAttributes().put("PLAYER", player);

            PlayerInfoResponse response = new PlayerInfoResponse(player.getNickname(), true);
            Message message = new Message(response, MessageType.PLAYER_INFO_RESPONSE);

            System.out.println("Player info fetched for " + player.getNickname() + ", sending response...");
            // 5. Rispondi al client
            gameNotifications.sendMessageToClient(message, sessionId);
        }else{
            System.err.println("Errore nel login, player è null");

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
            GameController newGameController = new GameController(this);
            newGameController.setSocketHandler(gameNotifications);
            notStartedGameControllersList.add(newGameController);
        }
        for(GameController gc : notStartedGameControllersList){
            try {
                lobby.removePlayerFromLobby(player.getNickname()); //tolgo il giocatore dalla lobby
                gc.addPlayerToGame(player); //inserisco giocagore nella partita
                isJoined = true;
                System.out.println("Player " + player.getNickname() + " added to game controller");
                //TODO: potrei spostare il player dalla lobby a dentro il game. poi quando finisce la partita lo tiro fuori e lo rimetto in lobby
                //associo il giocatore alla partita
                playerGameMap.put(player.getSupabaseUid(), gc);

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
        String supabaseUid = player.getSupabaseUid();

        GameController gameController = playerGameMap.get(supabaseUid);
        gameController.putCard(seed, value, player.getNickname());
    }

    public void setBet(int bet, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        String supabaseUid = player.getSupabaseUid();

        GameController gameController = playerGameMap.get(supabaseUid);
        gameController.setBet(bet, player.getNickname());
    }


    public void setSocketHandler(MySocketHandler mySocketHandler) {

        this.gameNotifications = mySocketHandler;
    }

    /*public void notifySingleClient(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.forwardUpdateToSingleClient(message, nickname);
        }
    }*/

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

    ///CONNECTION RESILIENCE METHODS///

    public boolean checkIfPlayerInGame(String sessionId) {
        Player player = getPlayerBySession(sessionId);
        if (player != null) {
            return playerGameMap.containsKey(player.getSupabaseUid());
        }
        //non dovrei arrivare mai qui
        return false;
    }

    public void handlePlayerDisconnection(String sessionId) {
        Player player = getPlayerBySession(sessionId);
        if (player != null) {
            player.setConnectionStatus(PlayerConnection.OFFLINE);
            onPlayerDisconnected(player.getSupabaseUid());
        }
    }

    /**
     * Chiamato quando la socket si chiude
     */
    private void onPlayerDisconnected(String playerUuid) {
        System.out.println("Giocatore " + playerUuid + " disconnesso. Avvio timer di grazia...");

        // Programmiamo l'esecuzione del compito di "pulizia" dopo 60 secondi
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            finalizeDisconnection(playerUuid);
        }, DISCONNECT_TIMEOUT, TimeUnit.SECONDS);

        // Salviamo il timer per poterlo annullare se il player torna
        disconnectTimers.put(playerUuid, timer);
    }

    private void handlePlayerReconnection(Player player) {

        if (player != null) {
            player.setConnectionStatus(PlayerConnection.ONLINE);
            onPlayerReconnected(player.getSupabaseUid());
            //playerGameMap.get(player.getSupabaseUid()).notifyPlayerReconnection(player.getNickname());
            GameController gameController = playerGameMap.get(player.getSupabaseUid());
            gameController.sendAllDataAfterReconnection(player.getNickname());
        }else{
            //non dovrebbe accadere
            System.err.println("Errore: giocatore NULL " + " nel database durante la riconnessione.");
        }
    }

    /**
     * Chiamato quando il giocatore si riconnette (nella stessa partita)
     */
    private void onPlayerReconnected(String playerUuid) {
        ScheduledFuture<?> activeTimer = disconnectTimers.remove(playerUuid);

        if (activeTimer != null) {
            activeTimer.cancel(false); // Fermiamo il timer!
            System.out.println("Bentornato " + playerUuid + "! Timer annullato.");
        }
    }

    /**
     * Azione eseguita allo scadere del timer
     */
    private void finalizeDisconnection(String playerUuid) {
        disconnectTimers.remove(playerUuid);
        System.out.println("Timer scaduto per " + playerUuid + ". Rimozione definitiva dalla partita.");

        // QUI INSERISCI LA TUA LOGICA DI GIOCO:
        GameController gameController = playerGameMap.get(playerUuid);
        // 1. Rimuovi il player dalla partita

        playerGameMap.remove(playerUuid);

        Player player = playerRepository.findBySupabaseUid(playerUuid).orElse(null);
        if(player != null) {
            System.out.println("Notifico agli altri giocatori l'abbandono di " + player.getNickname());

            gameController.playerExitGame(player.getNickname());
        }else{
            //non si dovrebbe arrivare qui
            System.err.println("Errore: giocatore NULL " + " nel database durante la disconnessione definitiva.");
        }
    }

    @Override
    public void onGameEnded(GameController gameController) {
        System.out.println("Partita terminata, pulisco dati partita...");
        playerGameMap.entrySet().removeIf(entry -> entry.getValue().equals(gameController));
        System.out.println("PARTITE ATTIVE: " + playerGameMap);
    }
}
