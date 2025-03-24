package polimi.ascensore;

import lombok.Getter;

public class Card {

    @Getter
    private final Seed seed;
    @Getter
    private final int value;

    public Card(Seed seed, int value) {
        this.seed = seed;
        this.value = value;
    }
}
