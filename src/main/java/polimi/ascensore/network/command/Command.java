package polimi.ascensore.network.command;

/**
 * Envelope of every client-to-server message: {"commandType": ..., "executable": {...}}.
 */
public class Command {

    private final CommandType commandType;

    private final ExecutableInServer executable;

    public Command(ExecutableInServer executable, CommandType commandType) {
        this.executable = executable;
        this.commandType = commandType;
    }

    public ExecutableInServer getExecutable() {
        return executable;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public void setClientSessionId(String clientSessionId) {
        executable.setClientSessionId(clientSessionId);
    }
}
