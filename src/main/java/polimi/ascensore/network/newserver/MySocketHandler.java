package polimi.ascensore.network.newserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import polimi.ascensore.controller.GameNotifier;
import polimi.ascensore.model.GamePlayer;
import polimi.ascensore.model.PlayerState;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.server.MasterServer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MySocketHandler extends TextWebSocketHandler implements GameNotifier {

    // Concurrent maps: sessions are added on WebSocket container threads and read by the command loop.

    //sessionId, session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    //nickname, sessionId
    private final Map<String, String> nicknameToSessionId = new ConcurrentHashMap<>();

    MasterServer masterServer;

    Gson gson;

    public MySocketHandler(MasterServer masterServer) {
        this.masterServer = masterServer;
        gson = new GsonBuilder()
                .registerTypeAdapter(Command.class, new CommandDeserializer())
                .create();
        masterServer.setSocketHandler(this);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        System.out.println("Received: " + payload);
        //session.sendMessage(new TextMessage("Echo: " + payload));
        try {
            Command receivedCommand = gson.fromJson(payload, Command.class);
            //imposto id della session (così so chi mi ha mandato il pacchetto e gli rispondo)
            receivedCommand.setClientSessionId(session.getId());
            masterServer.addCommandToList(receivedCommand);
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            e.printStackTrace();
        }
        /*
        boolean isLogged = false;
        if(payload.contains("marco")) {
            isLogged = true;
        }
        Message tmp = new Message(new LoginResponse(isLogged, "nickname", new LinkedList<>(Arrays.asList("andrea", "luca"))), "nickname");
        WebSocketMessage<String> msg = new TextMessage(tmp.toJson());
        System.out.println("Sending: " + msg.getPayload());
        session.sendMessage(msg);
         */
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("New connection established: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Game-side cleanup (reconnection timer, session removal) runs on the command loop
        masterServer.handleConnectionClosed(session.getId());
        System.out.println("Connection closed: " + session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        // QUI VEDI L'ERRORE VERO!
        System.err.println("--- ERRORE SOCKET RILEVATO ---");
        System.err.println("Sessione: " + session.getId());
        System.err.println("Messaggio Errore: " + exception.getMessage());

        // Stampa tutto lo stack trace per capire se è "MessageTooLarge" o altro
        exception.printStackTrace();

        // Opzionale: Prova a chiudere la sessione pulitamente se è ancora aperta
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void forwardUpdateToAll(Message message, List<GamePlayer> playersInGame){

        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        System.out.println("Sending: " + msg.getPayload());
        //invia a tutti i giocatori in partita
        for(GamePlayer p : playersInGame) {
            if(p.getPlayerState() != PlayerState.EXIT){
                send(getSession(p.getSessionId()), msg, p.getNickname());
            }
        }
    }

    @Override
    public void forwardUpdateToSingleClient(Message message, String nickname){

        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        System.out.println("Sending to " + nickname + ": " + msg.getPayload());
        String sessionId = nickname == null ? null : nicknameToSessionId.get(nickname);
        send(getSession(sessionId), msg, nickname);
    }

    public void addNicknameToSessionIdNode(String nickname, String sessionId){
        nicknameToSessionId.put(nickname, sessionId);
    }

    /**
     * True if {@code sessionId} is the socket the player is currently using. After a reconnection the
     * player's old socket can report its close late, and that close must not affect the new session.
     */
    public boolean isCurrentSession(String nickname, String sessionId) {
        return nickname != null && sessionId.equals(nicknameToSessionId.get(nickname));
    }

    public void sendMessageToClient(Message message, String sessionId){

        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        System.out.println("Sending : " + msg.getPayload());
        send(getSession(sessionId), msg, sessionId);
    }

    /**
     * Sends to one recipient and never throws: a player who is mid-reconnection has no open session,
     * and failing on them would abort the broadcast (and the game update) for everyone else.
     */
    private void send(WebSocketSession session, WebSocketMessage<String> msg, String recipient) {
        if (session == null || !session.isOpen()) {
            System.err.println("No open session for " + recipient + ", message skipped");
            return;
        }
        try {
            session.sendMessage(msg);
        } catch (IOException | IllegalStateException e) {
            System.err.println("Error sending message to " + recipient + ": " + e.getMessage());
        }
    }

    public WebSocketSession getSession(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    // Called on the command loop, on logout and when a socket closes
    public void removeSession(String sessionId) {

        // Only drop the nickname mapping if it still points here: after a reconnection it points to the new session
        nicknameToSessionId.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
        sessions.remove(sessionId);
    }

}
