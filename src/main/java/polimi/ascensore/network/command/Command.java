package polimi.ascensore.network.command;

import java.io.Serializable;
import polimi.ascensore.network.command.ExecutableInServer;


public class Command implements Serializable {

    /**
     * The type of the command sended by the client.
     */
    private final CommandType type;
    /**
     * The executable inside the command.
     */
    private ExecutableInServer executable;

    /**
     * Constructs a command with the specified executable and type.
     * @param executable the executable inside the command.
     * @param type the type of the command.
     */
    public Command(ExecutableInServer executable, CommandType type){
        this.executable = executable;
        this.type = type;
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
    public CommandType getType(){
        return this.type;
    }
}
