package polimi.ascensore.network.newserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // CHIEDIAMO A SPRING L'HANDLER GIÀ PRONTO
    private final MySocketHandler mySocketHandler;

    // Costruttore: Spring inietta l'handler qui
    public WebSocketConfig(MySocketHandler mySocketHandler) {
        this.mySocketHandler = mySocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Usiamo l'istanza gestita da Spring, non una 'new'
        registry.addHandler(mySocketHandler, "/ws")
                .setAllowedOrigins("*");
    }

    //Per aumentare la dimensione del buffer
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // Impostiamo il buffer a 128 KB (abbondiamo)
        // Il default è 8192 (8KB), che con i token JWT spesso non basta.
        container.setMaxTextMessageBufferSize(128 * 1024);
        container.setMaxBinaryMessageBufferSize(128 * 1024);

        return container;
    }
}
