package polimi.ascensore.model;

import java.util.List;
import java.util.Map;

/**
 * Everything needed to resume a started match after the server restarts: saved after every move and
 * loaded back at startup. The deck is not saved: it is reshuffled before every deal.
 *
 * @param seats          players still in the match, in seating order (who bets first rotates along it)
 * @param left           players who left, in leaving order
 * @param playOrder      nicknames in the order they act in the current trick (or betting round)
 * @param trick          cards of the current trick, in the order they were played
 * @param timeoutsInARow turns each player let time out in a row
 */
public record MatchState(
        int version,
        String id,
        int playersPerMatch,
        int maxHandSize,
        int setIndex,
        int round,
        int betsPlaced,
        List<Seat> seats,
        List<Seat> left,
        List<String> playOrder,
        List<Card> trick,
        Card briscola,
        Map<String, Integer> timeoutsInARow) {

    public static final int VERSION = 1;

    public record Seat(
            String supabaseUid,
            String nickname,
            int score,
            int bet,
            int roundsWon,
            List<Card> hand,
            PlayerState state) {
    }
}
