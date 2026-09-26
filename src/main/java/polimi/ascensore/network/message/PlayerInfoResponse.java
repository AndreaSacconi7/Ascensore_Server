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

    // One of the constants above, or null
    private final String error;

    private PlayerInfoResponse(String nickname, boolean isLogged, boolean needsNickname, String error) {
        this.nickname = nickname;
        this.isLogged = isLogged;
        this.needsNickname = needsNickname;
        this.error = error;
    }

    public static PlayerInfoResponse loggedIn(String nickname) {
        return new PlayerInfoResponse(nickname, true, false, null);
    }

    public static PlayerInfoResponse rejected(String error) {
        return new PlayerInfoResponse("", false, false, error);
    }

    public static PlayerInfoResponse nicknameRequired(String error) {
        return new PlayerInfoResponse("", false, true, error);
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

    public String getError() {
        return error;
    }
}
