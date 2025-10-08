package polimi.ascensore.model;

import java.io.Serializable;

public class Card implements Serializable {

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
}
