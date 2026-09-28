package polimi.ascensore.network.websocket;

import org.springframework.beans.factory.annotation.Value;
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

    private final String[] allowedOrigins;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler, ClientAddressInterceptor clientAddress,
                           @Value("${ascensore.allowed-origins}") String[] allowedOrigins) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.clientAddress = clientAddress;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Players authenticate with a token inside the protocol, not with cookies, so another site cannot act
        // on a player's behalf. Still, only the game's own pages may open sockets from a browser, so no other
        // site can have its visitors' browsers connect here. Apps send no Origin and are not affected.
        registry.addHandler(gameWebSocketHandler, "/ws")
                .addInterceptors(clientAddress)
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);
        return container;
    }
}
