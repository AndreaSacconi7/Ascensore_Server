package polimi.ascensore.model;

import java.util.List;

/**
 * The rules of Ascensore as pure functions, with no game state or I/O, so they can be tested in isolation.
 */
public final class GameRules {

    private GameRules() {
    }

    /**
     * Number of sets in a match: the hand size goes up from 1 to {@code maxHandSize} and back down to 1.
     */
    public static int totalSets(int maxHandSize) {
        return 2 * maxHandSize - 1;
    }

    /**
     * Cards dealt to each player in the set with the given 0-based index: 1, 2, ..., max, ..., 2, 1.
     */
    public static int handSize(int setIndex, int maxHandSize) {
        return setIndex < maxHandSize ? setIndex + 1 : totalSets(maxHandSize) - setIndex;
    }

    /**
     * Bets go from 0 to the hand size, and the last player to bet cannot make the total equal to the
     * number of tricks in the set, so at least one player must miss their bet.
     */
    public static boolean isValidBet(int bet, int handSize, boolean isLastBettor, int otherBetsTotal) {
        if (bet < 0 || bet > handSize) {
            return false;
        }
        return !isLastBettor || otherBetsTotal + bet != handSize;
    }

    /**
     * A player who holds a card of the lead seed must follow it; with no lead card any card is valid.
     */
    public static boolean isValidCard(Card card, List<Card> hand, Card leadCard) {
        if (leadCard == null || card.getSeed() == leadCard.getSeed()) {
            return true;
        }
        return hand.stream().noneMatch(c -> c.getSeed() == leadCard.getSeed());
    }

    /**
     * Index, in play order, of the card that takes the trick: the highest briscola if any was played,
     * otherwise the highest card of the lead seed.
     *
     * @param briscola the trump seed, or null if the set has none
     */
    public static int trickWinnerIndex(List<Card> trick, Seed briscola) {
        int winner = 0;
        for (int i = 1; i < trick.size(); i++) {
            if (beats(trick.get(i), trick.get(winner), briscola)) {
                winner = i;
            }
        }
        return winner;
    }

    /**
     * True if {@code challenger}, played after {@code current}, takes the trick from it.
     */
    static boolean beats(Card challenger, Card current, Seed briscola) {
        if (challenger.getSeed() == current.getSeed()) {
            return challenger.getValueForComparison() > current.getValueForComparison();
        }
        // Different seeds: only a briscola can take a card of another seed
        return briscola != null && challenger.getSeed() == briscola;
    }

    /**
     * Points for one set: an exact bet scores 10 plus 10 per trick, a missed bet loses 10 per trick of difference.
     */
    public static int setScore(int bet, int tricksWon) {
        if (bet == tricksWon) {
            return 10 * bet + 10;
        }
        return -10 * Math.abs(tricksWon - bet);
    }
}
