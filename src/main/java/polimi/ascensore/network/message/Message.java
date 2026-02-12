package polimi.ascensore.network.message;

import com.google.gson.Gson;

import java.io.Serializable;

public class Message implements Serializable {

    private ExecutableInClient executable;

    private MessageType messageType;
    //tale costruttore pone i messaggi direttamente a tipo COMMON_MESSAGE

    /**
     * Creates a message with the specified executable and nickname.
     * Sets messages directly to type COMMON_MESSAGE
     * @param executable the executable with the response to the client
     */
    public Message(ExecutableInClient executable, MessageType messageType){
        this.executable = executable;
        this.messageType = messageType;
    }

    /**
     * @return the executable of the message
     */
    public ExecutableInClient getExecutable(){
        return this.executable;
    }

    // Convert the object to JSON
    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

}
