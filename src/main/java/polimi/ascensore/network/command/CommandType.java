package polimi.ascensore.network.command;

/**
 * Commands a client can send. Each maps to an {@link ExecutableInServer} in the CommandDeserializer.
 */
public enum CommandType {

    // First message on a socket: authenticates with a Supabase token (and optionally chooses a nickname)
    PLAYER_INFO_REQUEST,

    // Enter matchmaking for a match of 2 to 4 players
    JOIN_GAME_REQUEST,

    // Leave matchmaking, or the match in progress for good
    LEAVE_GAME_REQUEST,

    // Bet how many tricks the player will take this set
    SET_BET,

    // Play a card on the table
    PUT_CARD,

    LOGOUT
}
