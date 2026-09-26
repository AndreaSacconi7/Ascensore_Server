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

    // Runs every state change, including expired reconnection timers
    private final CommandLoop commandLoop;

    // Pending reconnection windows: PlayerUUID -> timer. Only touched on the command loop.
    private final Map<String, PendingDisconnect> disconnectTimers = new ConcurrentHashMap<>();

    // Il motore che esegue i timer in background
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Tempo di attesa prima della disconnessione definitiva (es. 60 secondi)
    private static final int DISCONNECT_TIMEOUT = 60;

    // One reconnection window. Compared by identity, so a timer that fired late cannot close a newer window.
    private static final class PendingDisconnect {
        private ScheduledFuture<?> timer;
    }


    public MasterController(CommandLoop commandLoop) {
        this.commandLoop = commandLoop;
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
            // Invalid or expired token: answer instead of leaving the client on the loading screen
            Message message = new Message(new PlayerInfoResponse("", false), MessageType.PLAYER_INFO_RESPONSE);
            gameNotifications.sendMessageToClient(message, sessionId);
            return;
        }

        // 2. Cerchi nel DB se esiste già
        Optional<Player> playerOpt = playerRepository.findBySupabaseUid(supabaseUid);

        Player player;
        boolean playerInGame = false;

        if (playerOpt.isPresent()) {
            // --- CASO A: AUTO-LOGIN (Utente già registrato) ---
            // NON ci serve il nickname dal client, usiamo quello che abbiamo nel DB!
            player = playerOpt.get();
            System.out.println("Bentornato " + player.getNickname());
            gameNotifications.addNicknameToSessionIdNode(player.getNickname(), sessionId);
            // Controlla se il giocatore è già in una partita (ovvero se gli è caduta la connessione e sta cercando di rientrare)
            playerInGame = checkIfPlayerInGame(player.getSupabaseUid());
            System.out.println("Player " + player.getNickname() + " is in game: " + playerInGame + "----------------------------------------------------");
        } else if (nicknameOpzionale == null || nicknameOpzionale.isBlank()) {
            // A new player needs a nickname: reject instead of registering a nameless account
            player = null;
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

            if(playerInGame){
                System.out.println("Sending all info to Player " + player.getNickname() + " that is trying to reconnect to the game..." + "----------------------------------------------------");
                //mettere il giocatore in partita, ovvero ricollegarlo alla partita a cui stava giocando prima della disconnessione
                handlePlayerReconnection(player, sessionId);
            }
        }else{
            System.err.println("Errore nel login, player è null");

            // player is null here: echo the requested nickname (the client expects a string)
            PlayerInfoResponse response = new PlayerInfoResponse(Objects.requireNonNullElse(nicknameOpzionale, ""), false);
            Message message = new Message(response, MessageType.PLAYER_INFO_RESPONSE);

            gameNotifications.sendMessageToClient(message, sessionId);
        }
    }

    public void addPlayerToGame(String sessionId){
        Player player = getPlayerBySession(sessionId);
        if (player == null) {
            System.err.println("JOIN_GAME_REQUEST ignored: session " + sessionId + " is not logged in");
            return;
        }
        if (checkIfPlayerInGame(player.getSupabaseUid())) {
            System.err.println("JOIN_GAME_REQUEST ignored: " + player.getNickname() + " is already in a game");
            return;
        }

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
                gc.addPlayerToGame(player, sessionId); //inserisco giocagore nella partita
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
        GameController gameController = gameOf(player);
        if (gameController == null) {
            System.err.println("PUT_CARD ignored: session " + sessionId + " is not in a game");
            return;
        }
        gameController.putCard(seed, value, player.getNickname());
    }

    public void setBet(int bet, String sessionId) {
        Player player = getPlayerBySession(sessionId);
        GameController gameController = gameOf(player);
        if (gameController == null) {
            System.err.println("SET_BET ignored: session " + sessionId + " is not in a game");
            return;
        }
        gameController.setBet(bet, player.getNickname());
    }

    // The match this player is in, or null if not logged in or not playing
    private GameController gameOf(Player player) {
        return player == null ? null : playerGameMap.get(player.getSupabaseUid());
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

    public boolean checkIfPlayerInGame(String supabaseUid) {

        return playerGameMap.containsKey(supabaseUid);
    }

    /**
     * Runs on the command loop once a socket has closed, after every command that socket had sent.
     */
    public void handleConnectionClosed(String sessionId) {
        Player player = getPlayerBySession(sessionId);
        // Only the player's current socket opens a reconnection window: if they already reconnected on a
        // new socket, the old one closing late must not start a timer that would kick them out.
        if (player != null
                && gameNotifications.isCurrentSession(player.getNickname(), sessionId)
                && checkIfPlayerInGame(player.getSupabaseUid())) {
            player.setConnectionStatus(PlayerConnection.OFFLINE);
            onPlayerDisconnected(player.getSupabaseUid());
        }
        gameNotifications.removeSession(sessionId);
    }

    /**
     * Chiamato quando la socket si chiude
     */
    private void onPlayerDisconnected(String playerUuid) {
        System.out.println("Giocatore " + playerUuid + " disconnesso. Avvio timer di grazia...");

        // The timer thread only enqueues the expiry: the cleanup itself runs on the command loop
        PendingDisconnect pending = new PendingDisconnect();
        pending.timer = scheduler.schedule(
                () -> commandLoop.submit(() -> onDisconnectTimeout(playerUuid, pending)),
                DISCONNECT_TIMEOUT, TimeUnit.SECONDS);

        // Salviamo il timer per poterlo annullare se il player torna
        PendingDisconnect previous = disconnectTimers.put(playerUuid, pending);
        if (previous != null) {
            previous.timer.cancel(false);
        }
    }

    private void onDisconnectTimeout(String playerUuid, PendingDisconnect pending) {
        // The timer may have fired just before the player reconnected: then its window is already closed
        if (!disconnectTimers.remove(playerUuid, pending)) {
            return;
        }
        finalizeDisconnection(playerUuid);
    }

    // Visible for tests
    boolean hasPendingDisconnect(String playerUuid) {
        return disconnectTimers.containsKey(playerUuid);
    }

    private void handlePlayerReconnection(Player player, String sessionId) {

        if (player != null) {
            player.setConnectionStatus(PlayerConnection.ONLINE);
            onPlayerReconnected(player.getSupabaseUid());
            //playerGameMap.get(player.getSupabaseUid()).notifyPlayerReconnection(player.getNickname());
            GameController gameController = playerGameMap.get(player.getSupabaseUid());
            gameController.sendAllDataAfterReconnection(player.getNickname(), sessionId);
        }else{
            //non dovrebbe accadere
            System.err.println("Errore: giocatore NULL " + " nel database durante la riconnessione.");
        }
    }

    /**
     * Chiamato quando il giocatore si riconnette (nella stessa partita)
     */
    private void onPlayerReconnected(String playerUuid) {
        PendingDisconnect pending = disconnectTimers.remove(playerUuid);

        if (pending != null) {
            pending.timer.cancel(false); // Fermiamo il timer!
            System.out.println("Bentornato " + playerUuid + "! Timer annullato.");
        }
    }

    /**
     * Azione eseguita allo scadere del timer
     */
    private void finalizeDisconnection(String playerUuid) {
        PendingDisconnect pending = disconnectTimers.remove(playerUuid);
        if (pending != null) {
            pending.timer.cancel(false);
        }
        System.out.println("Timer scaduto per " + playerUuid + ". Rimozione definitiva dalla partita.");

        // 1. Rimuovi il player dalla partita
        GameController gameController = playerGameMap.remove(playerUuid);
        if (gameController == null) {
            // The match already ended while the player was away
            return;
        }

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

    public void logout(String clientSessionId) {
        Player player = getPlayerBySession(clientSessionId);
        if(player != null && checkIfPlayerInGame(player.getSupabaseUid())){
            //se il giocatore è in partita finalizzo direttamente la disconnessione senza aspettare il timer
            finalizeDisconnection(player.getSupabaseUid());
        }
        gameNotifications.removeSession(clientSessionId);
    }
}
