package polimi.ascensore.network.message;

import com.google.gson.Gson;

import java.io.Serializable;

public class Message implements Serializable {

    private ExecutableInClient executable;

    //nickname del player che è di turno
    /**
     * Represents the nickname of the player that is playing.
     */
    private String nickName;

    private MessageType messageType;
    //tale costruttore pone i messaggi direttamente a tipo COMMON_MESSAGE

    /**
     * Creates a message with the specified executable and nickname.
     * Sets messages directly to type COMMON_MESSAGE
     * @param executable the executable with the response to the client
     * @param nickName the player who receives the message
     */
    public Message(ExecutableInClient executable, String nickName, MessageType messageType){
        this.executable = executable;
        this.nickName = nickName;
        this.messageType = messageType;
    }

    /**
     * @return the executable of the message
     */
    public ExecutableInClient getExecutable(){
        return this.executable;
    }

    /**
     * @return the nickname of the player that is playing
     */
    public String getNickName(){
        return this.nickName;
    }

    // Convert the object to JSON
    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

}
