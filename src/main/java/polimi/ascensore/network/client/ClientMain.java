package polimi.ascensore.network.client;

import java.io.IOException;

public class ClientMain {

    public static void main(String[] args) {

        try {
            SocketClient client = new SocketClient("localhost", 8080);
            client.run();

            client.connect("marco");


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
