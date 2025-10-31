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
        //nel caso di carte speciali (asso e tre) restituisco il valore più alto rispetto al re (10)
        if(value == 1){
            return 12;
        } else if (value == 3) {
            return 11;
        }
        //tutte carte tranne asso e tre
        return value;
    }
}
