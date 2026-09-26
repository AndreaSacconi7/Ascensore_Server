package polimi.ascensore.network.message;

/**
 * Sent to a session just before it is closed because the same account logged in on another device.
 * The client must not reconnect on its own, or the two devices would keep replacing each other.
 */
public class SessionReplaced implements ExecutableInClient {
}
