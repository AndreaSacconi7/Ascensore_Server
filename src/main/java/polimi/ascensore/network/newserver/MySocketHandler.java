package polimi.ascensore.network.newserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.server.MasterServer;

import java.io.IOException;
import java.util.*;

@Component
public class MySocketHandler extends TextWebSocketHandler {

    HashMap<String, WebSocketSession> sessions;

    HashMap<String, String> nicknameToSessionId;

    MasterServer masterServer;

    Gson gson;

    public MySocketHandler(MasterServer masterServer) {
        this.masterServer = masterServer;
        this.sessions = new HashMap<>();
        this.nicknameToSessionId = new HashMap<>();
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
        for(Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if(entry.getValue() == session) {
                sessions.remove(entry.getKey());
                break;
            }
        }
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

    public void forwardUpdateToAll(Message message, String nickname) {

        WebSocketMessage<String> msg = new TextMessage(message.toJson());
        System.out.println("Sending: " + msg.getPayload());
        for(Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            try {
                session.sendMessage(msg);
            } catch (IOException e) {
                System.err.println("Error sending message to client " + entry.getKey() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void forwardUpdateToSingleClient(Message message, String nickname){

        for(Map.Entry<String, String> entryNick : nicknameToSessionId.entrySet()) {
            //cerco id correspondente al nickname
            if(entryNick.getKey().equals(nickname)) {
                for (Map.Entry<String, WebSocketSession> entryId : sessions.entrySet()) {
                    //cerco session corrispondente all'id
                    if(entryId.getKey().equals(entryNick.getValue())){
                        WebSocketSession session = entryId.getValue();
                        try {
                            WebSocketMessage<String> msg = new TextMessage(message.toJson());
                            System.out.println("Sending to " + nickname + ": " + msg.getPayload());
                            session.sendMessage(msg);
                        } catch (IOException e) {
                            System.err.println("Error sending message to client " + nickname + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                        break; // Exit the loop once the matching session is found
                    }
                }
            }
        }
    }

    public void addNicknameToSessionIdNode(String nickname, String sessionId){
        nicknameToSessionId.put(nickname, sessionId);
    }

    public void sendMessageToClient(Message message, String sessionId){
        WebSocketSession session = sessions.get(sessionId);

        if (session != null) {

            WebSocketMessage<String> msg = new TextMessage(message.toJson());

            System.out.println("Sending : " + msg.getPayload());
            try {
                session.sendMessage(msg);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            System.err.println("No session found for sessionId: " + sessionId);
        }
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

}
