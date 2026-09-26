package polimi.ascensore.network.command;

/**
 * Commands a client can send. Each maps to an {@link ExecutableInServer} in the CommandDeserializer.
 */
public enum CommandType {

    // First message on a socket: authenticates with a Supabase token (and optionally chooses a nickname)
    PLAYER_INFO_REQUEST,

    // Enter matchmaking
    JOIN_GAME_REQUEST,

    // Bet how many tricks the player will take this set
    SET_BET,

    // Play a card on the table
    PUT_CARD,

    LOGOUT
}
