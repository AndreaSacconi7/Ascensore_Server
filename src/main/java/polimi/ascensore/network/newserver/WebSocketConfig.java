package polimi.ascensore.network.newserver;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import polimi.ascensore.controller.Controller;
import polimi.ascensore.network.server.MasterServer;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        Controller controller = new Controller();

        MasterServer masterServer = new MasterServer(controller);

        registry.addHandler(new MySocketHandler(masterServer), "/ws").setAllowedOrigins("*");
    }
}
