package polimi.ascensore.network.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Records the player's network address on the WebSocket session, to limit connections per address.
 * <p>
 * Behind a proxy every connection comes from the proxy, so the address is read from the header the proxy
 * sets ({@code ascensore.client-ip-header}, e.g. {@code Fly-Client-IP} on Fly.io). Only configure a header
 * the proxy always overwrites: otherwise clients could send any address they like.
 */
@Component
public class ClientAddressInterceptor implements HandshakeInterceptor {

    public static final String CLIENT_ADDRESS = "CLIENT_ADDRESS";

    private final String header;

    public ClientAddressInterceptor(@Value("${ascensore.client-ip-header:}") String header) {
        this.header = header;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String address = header.isBlank() ? null : request.getHeaders().getFirst(header);
        if (address == null) {
            InetSocketAddress remote = request.getRemoteAddress();
            address = remote == null ? null : remote.getAddress().getHostAddress();
        }
        if (address != null) {
            attributes.put(CLIENT_ADDRESS, address);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
