package polimi.ascensore.network.message;

import com.google.gson.Gson;

/**
 * Envelope of every server-to-client message: {"messageType": ..., "executable": {...}}.
 */
public class Message {

    private static final Gson GSON = new Gson();

    private final ExecutableInClient executable;

    private final MessageType messageType;

    public Message(ExecutableInClient executable, MessageType messageType) {
        this.executable = executable;
        this.messageType = messageType;
    }

    public ExecutableInClient getExecutable() {
        return executable;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
