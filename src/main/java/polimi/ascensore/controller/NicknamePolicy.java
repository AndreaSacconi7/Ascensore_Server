package polimi.ascensore.controller;

import java.util.regex.Pattern;

/**
 * Public nicknames: 3 to 16 letters, digits or underscores. Nicknames are shown to other players, so the
 * character set also rules out email addresses, which older clients used to send as nicknames.
 */
public final class NicknamePolicy {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private NicknamePolicy() {
    }

    public static boolean isValid(String nickname) {
        return nickname != null && VALID.matcher(nickname).matches();
    }
}
