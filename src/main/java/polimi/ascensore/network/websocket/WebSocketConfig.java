package polimi.ascensore.network.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // The largest legitimate command is PLAYER_INFO_REQUEST, which carries a JWT of a couple of KB
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;

    private final GameWebSocketHandler gameWebSocketHandler;

    private final ClientAddressInterceptor clientAddress;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler, ClientAddressInterceptor clientAddress) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.clientAddress = clientAddress;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Any origin: players authenticate with a token inside the protocol, not with cookies,
        // so another site cannot act on a player's behalf through their browser
        registry.addHandler(gameWebSocketHandler, "/ws")
                .addInterceptors(clientAddress)
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);
        return container;
    }
}
