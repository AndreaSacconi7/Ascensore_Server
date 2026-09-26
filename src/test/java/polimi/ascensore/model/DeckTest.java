package polimi.ascensore.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckTest {

    @Test
    void shuffledDeckHasAllFortyCardsOnce() {
        Deck deck = new Deck(new Random(1));
        deck.shuffleDeck();

        Set<String> cards = new HashSet<>();
        for (Card card : deck.getDeckcards()) {
            cards.add(card.getSeed() + "-" + card.getValue());
        }
        assertEquals(40, deck.getDeckcards().size());
        assertEquals(40, cards.size());
    }

    @Test
    void reshufflingRestoresTheFullDeck() {
        Deck deck = new Deck(new Random(1));
        deck.shuffleDeck();
        deck.getDeckcards().pop();

        deck.shuffleDeck();

        assertEquals(40, deck.getDeckcards().size());
    }
}
