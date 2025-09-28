package polimi.ascensore.network.newserver;

import com.google.gson.*;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.CommandType;
import polimi.ascensore.network.command.ConnectionRequest;
import polimi.ascensore.network.command.ExecutableInServer;

import java.lang.reflect.Type;

public class CommandDeserializer implements JsonDeserializer<Command> {

    @Override
    public Command deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        // Leggi il tipo di comando
        String commandType = jsonObject.get("type").getAsString();

        // Deserializza l'eseguibile in base al tipo
        ExecutableInServer executable = null;
        if ("CONNECTION_REQUEST".equals(commandType)) {
            executable = context.deserialize(jsonObject.get("executable"), ConnectionRequest.class);
        }
        // Aggiungi altri tipi di comando qui, se necessario

        // Crea e restituisci il comando
        return new Command(executable, CommandType.valueOf(commandType));
    }
}