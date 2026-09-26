package polimi.ascensore.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import java.util.stream.IntStream;

/**
 * The 40-card Italian deck: four seeds, values 1 (ace) to 10 (king).
 */
public class Deck {

    public static final int SIZE = 40;

    private static final List<Card> FULL_DECK = Arrays.stream(Seed.values())
            .flatMap(seed -> IntStream.rangeClosed(1, 10).mapToObj(value -> new Card(seed, value)))
            .toList();

    // Shuffling source: SecureRandom in production so deals cannot be predicted from earlier ones
    private final Random random;

    private final Stack<Card> deckcards = new Stack<>();

    public Deck(Random random) {
        this.random = random;
    }

    /**
     * Puts all 40 cards back and shuffles them; called before each deal.
     */
    public void shuffleDeck() {
        deckcards.clear();
        deckcards.addAll(FULL_DECK);
        Collections.shuffle(deckcards, random);
    }

    public Stack<Card> getDeckcards() {
        return deckcards;
    }
}
