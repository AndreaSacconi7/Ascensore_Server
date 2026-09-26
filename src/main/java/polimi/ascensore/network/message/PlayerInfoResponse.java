package polimi.ascensore.network.message;

/**
 * Answer to PLAYER_INFO_REQUEST: logged in, rejected, or logged in to Supabase but still without a
 * public nickname (the client then asks the user to choose one and repeats the request).
 */
public class PlayerInfoResponse implements ExecutableInClient {

    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String NICKNAME_MISSING = "NICKNAME_MISSING";
    public static final String NICKNAME_INVALID = "NICKNAME_INVALID";
    public static final String NICKNAME_TAKEN = "NICKNAME_TAKEN";

    private final String nickname;

    private final boolean isLogged;

    private final boolean needsNickname;

    // True when the player has a match in progress: the table state follows (reconnection)
    private final boolean inMatch;

    // One of the constants above, or null
    private final String error;

    private PlayerInfoResponse(String nickname, boolean isLogged, boolean needsNickname, boolean inMatch,
                               String error) {
        this.nickname = nickname;
        this.isLogged = isLogged;
        this.needsNickname = needsNickname;
        this.inMatch = inMatch;
        this.error = error;
    }

    public static PlayerInfoResponse loggedIn(String nickname, boolean inMatch) {
        return new PlayerInfoResponse(nickname, true, false, inMatch, null);
    }

    public static PlayerInfoResponse rejected(String error) {
        return new PlayerInfoResponse("", false, false, false, error);
    }

    public static PlayerInfoResponse nicknameRequired(String error) {
        return new PlayerInfoResponse("", false, true, false, error);
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isLogged() {
        return isLogged;
    }

    public boolean needsNickname() {
        return needsNickname;
    }

    public boolean isInMatch() {
        return inMatch;
    }

    public String getError() {
        return error;
    }
}
