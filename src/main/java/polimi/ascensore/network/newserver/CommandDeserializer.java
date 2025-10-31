package polimi.ascensore.network.newserver;

import com.google.gson.*;
import polimi.ascensore.network.command.*;

import java.lang.reflect.Type;

public class CommandDeserializer implements JsonDeserializer<Command> {

    @Override
    public Command deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        // Leggi il tipo di comando
        String commandType = jsonObject.get("commandType").getAsString();

        // Deserializza l'eseguibile in base al tipo
        ExecutableInServer executable = null;
        if ("LOGIN_COMMAND".equals(commandType)) {
            executable = context.deserialize(jsonObject.get("executable"), ConnectionRequest.class);
        } else if ("SET_BET".equals(commandType)) {
            executable = context.deserialize(jsonObject.get("executable"), SetBet.class);
        } else if ("PUT_CARD".equals(commandType)) {
            executable = context.deserialize(jsonObject.get("executable"), PutCard.class);
        }else if("PLAYER_INFO_REQUEST".equals(commandType)){
            executable = context.deserialize(jsonObject.get("executable"), PlayerInfoRequest.class);
        } else{
            //comando sconosciuto
            throw new JsonParseException("Unknown command type: " + commandType);
        }
        // Aggiungi altri tipi di comando prima dell'exception, se necessario

        // Crea e restituisci il comando
        return new Command(executable, CommandType.valueOf(commandType));
    }
}