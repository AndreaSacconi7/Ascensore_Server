package polimi.ascensore.network.newserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.message.LoginResponse;
import polimi.ascensore.network.message.Message;
import polimi.ascensore.network.server.MasterServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class MySocketHandler extends TextWebSocketHandler {

    List<WebSocketSession> sessions;

    MasterServer masterServer;

    Gson gson;

    public MySocketHandler(MasterServer masterServer) {
        this.masterServer = masterServer;
        this.sessions = new ArrayList<>();
        gson = new GsonBuilder()
                .registerTypeAdapter(Command.class, new CommandDeserializer())
                .create();
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        System.out.println("Received: " + payload);
        //session.sendMessage(new TextMessage("Echo: " + payload));
        try {
            Command receivedCommand = gson.fromJson(payload, Command.class);
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
        sessions.add(session);
        System.out.println("New connection established: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("Connection closed: " + session.getId());
    }

}
