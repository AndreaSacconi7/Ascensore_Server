package polimi.ascensore.network.command;

import java.io.Serializable;


public class Command implements Serializable {

    /**
     * The type of the command sended by the client.
     */
    private final CommandType commandType;
    /**
     * The executable inside the command.
     */
    private ExecutableInServer executable;

    private String clientSessionId;

    /**
     * Constructs a command with the specified executable and type.
     * @param executable the executable inside the command.
     * @param commandType the type of the command.
     */
    public Command(ExecutableInServer executable, CommandType commandType){
        this.executable = executable;
        this.commandType = commandType;
        this.clientSessionId = null;
    }

    /**
     * @return the executable inside the command.
     */
    public ExecutableInServer getExecutable(){
        return this.executable;
    }

    /**
     * @return the type of the command.
     */
    public CommandType getCommandType(){
        return this.commandType;
    }

    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
        executable.setClientSessionId(clientSessionId);
    }
}
