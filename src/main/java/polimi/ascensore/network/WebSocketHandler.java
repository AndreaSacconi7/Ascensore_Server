package polimi.ascensore.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }


    public void sendMessageToAll(Object message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
