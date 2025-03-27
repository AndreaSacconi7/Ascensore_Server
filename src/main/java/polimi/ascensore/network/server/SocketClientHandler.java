package polimi.ascensore.network.server;

import polimi.ascensore.network.message.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class SocketClientHandler {

    /**
     * Used for managing outgoing messages from client to server
     */
    transient final ObjectOutputStream output;
    /**
     * The nickname of the player who the client is associated to
     */
    private final String nickname;
    /**
     * The list of messages received from the server
     */
    private final ArrayList<Message> messages;

    /**
     * This constructor creates a new SocketClientHandler with the given output stream and nickname.
     * @param output the output stream
     * @param nickname the nickname of the player
     */
    public SocketClientHandler(ObjectOutputStream output,String nickname) {
        this.output = output;
        this.nickname = nickname;
        this.messages = new ArrayList<>();

        //la chat dovrà essere definita nel worker

    }


    /**
     * This method forwards the message recevied from the server to the client
     * @param message the message to forward to the client
     */
    public void forwardUpdate(Message message) {
        if(message.getNickName() == null || message.getNickName().equals(nickname) || message.getNickName().equals("all")){
            sendMessage(message);
        }
    }

    /**
     * @return the nickname of the client associated
     */
    public String getNickname() {
        return nickname;
    }


    /**
     * With this method I check if the message
     * is arrived correctly to the client
     * @param message the message to send
     */
    public void sendMessage(Message message) {
        try {
            output.writeObject(message);
            output.reset();
            System.out.println("Il Comando di Notifica è stato inviato al Client");
        }catch (IOException ec){
            System.out.println("SocketClientHandler: il Comando di Notifica non ha raggiunto il Client");
        }
    }

}
