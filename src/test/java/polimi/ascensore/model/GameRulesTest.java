package polimi.ascensore.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRulesTest {

    private static Card card(Seed seed, int value) {
        return new Card(seed, value);
    }

    @Test
    void handSizeGoesUpToTheMaxAndBackDown() {
        int[] expected = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        assertEquals(expected.length, GameRules.totalSets(10));
        for (int set = 0; set < expected.length; set++) {
            assertEquals(expected[set], GameRules.handSize(set, 10), "set index " + set);
        }
    }

    @Test
    void betMustBeWithinTheHandSize() {
        assertTrue(GameRules.isValidBet(0, 3, false, 0));
        assertTrue(GameRules.isValidBet(3, 3, false, 0));
        assertFalse(GameRules.isValidBet(4, 3, false, 0));
        assertFalse(GameRules.isValidBet(-1, 3, false, 0));
    }

    @Test
    void lastBetCannotMakeTheTotalEqualTheTricks() {
        assertFalse(GameRules.isValidBet(1, 3, true, 2));
        assertTrue(GameRules.isValidBet(0, 3, true, 2));
        assertTrue(GameRules.isValidBet(2, 3, true, 2));
        // Only the last bettor is constrained
        assertTrue(GameRules.isValidBet(1, 3, false, 2));
    }

    @Test
    void anyCardLeadsATrick() {
        List<Card> hand = List.of(card(Seed.CUPS, 1), card(Seed.SWORDS, 5));
        assertTrue(GameRules.isValidCard(hand.get(1), hand, null));
    }

    @Test
    void mustFollowTheLeadSeedWhenHoldingIt() {
        List<Card> hand = List.of(card(Seed.CUPS, 1), card(Seed.SWORDS, 5));
        Card lead = card(Seed.CUPS, 7);
        assertTrue(GameRules.isValidCard(hand.get(0), hand, lead));
        assertFalse(GameRules.isValidCard(hand.get(1), hand, lead));
    }

    @Test
    void anyCardWhenTheLeadSeedIsNotInHand() {
        List<Card> hand = List.of(card(Seed.COINS, 1), card(Seed.SWORDS, 5));
        assertTrue(GameRules.isValidCard(hand.get(1), hand, card(Seed.CUPS, 7)));
    }

    @Test
    void aceThenThreeBeatTheKing() {
        // Card strength within a seed: ace, three, king (10), ..., two
        List<Card> trick = List.of(card(Seed.CUPS, 10), card(Seed.CUPS, 3), card(Seed.CUPS, 1));
        assertEquals(2, GameRules.trickWinnerIndex(trick, Seed.SWORDS));
        assertEquals(1, GameRules.trickWinnerIndex(trick.subList(0, 2), Seed.SWORDS));
    }

    @Test
    void offSeedCardsNeverWin() {
        List<Card> trick = List.of(card(Seed.CUPS, 2), card(Seed.COINS, 1));
        assertEquals(0, GameRules.trickWinnerIndex(trick, Seed.SWORDS));
    }

    @Test
    void anyBriscolaBeatsTheLeadSeed() {
        List<Card> trick = List.of(card(Seed.CUPS, 1), card(Seed.SWORDS, 2), card(Seed.SWORDS, 4));
        assertEquals(2, GameRules.trickWinnerIndex(trick, Seed.SWORDS));
    }

    @Test
    void withoutBriscolaTheHighestLeadSeedWins() {
        List<Card> trick = List.of(card(Seed.CUPS, 5), card(Seed.SWORDS, 1), card(Seed.CUPS, 6));
        assertEquals(2, GameRules.trickWinnerIndex(trick, null));
    }

    @Test
    void exactBetScoresTenPlusTenPerTrick() {
        assertEquals(10, GameRules.setScore(0, 0));
        assertEquals(40, GameRules.setScore(3, 3));
    }

    @Test
    void missedBetLosesTenPerTrickOfDifference() {
        assertEquals(-20, GameRules.setScore(1, 3));
        assertEquals(-30, GameRules.setScore(3, 0));
    }
}
