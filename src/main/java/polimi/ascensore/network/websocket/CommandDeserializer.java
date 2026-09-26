package polimi.ascensore.network.websocket;

import com.google.gson.*;
import polimi.ascensore.network.command.*;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Reads {"commandType": ..., "executable": {...}} into the command class for that type.
 */
public class CommandDeserializer implements JsonDeserializer<Command> {

    private static final Map<CommandType, Class<? extends ExecutableInServer>> EXECUTABLES = Map.of(
            CommandType.PLAYER_INFO_REQUEST, PlayerInfoRequest.class,
            CommandType.JOIN_GAME_REQUEST, JoinGameRequest.class,
            CommandType.LEAVE_GAME_REQUEST, LeaveGameRequest.class,
            CommandType.SET_BET, SetBet.class,
            CommandType.PUT_CARD, PutCard.class,
            CommandType.LOGOUT, Logout.class);

    @Override
    public Command deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String typeName = jsonObject.get("commandType").getAsString();

        CommandType type;
        try {
            type = CommandType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown command type: " + typeName);
        }
        JsonElement executableJson = jsonObject.get("executable");
        if (executableJson == null || executableJson.isJsonNull()) {
            throw new JsonParseException("Missing executable for " + typeName);
        }
        ExecutableInServer executable = context.deserialize(executableJson, EXECUTABLES.get(type));
        return new Command(executable, type);
    }
}
