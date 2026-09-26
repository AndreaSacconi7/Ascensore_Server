package polimi.ascensore.model;

/**
 * A card of the Italian deck: value 1 is the ace, 8-10 are jack, knight and king.
 */
public class Card {

    private final Seed seed;

    private final int value;

    public Card(Seed seed, int value) {
        this.seed = seed;
        this.value = value;
    }

    public Seed getSeed() {
        return seed;
    }

    public int getValue() {
        return value;
    }

    /**
     * Strength within a seed: the ace is highest, then the three, then king down to two.
     */
    public int getValueForComparison() {
        if (value == 1) {
            return 12;
        }
        if (value == 3) {
            return 11;
        }
        return value;
    }
}
