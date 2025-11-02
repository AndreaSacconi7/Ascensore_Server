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

    //ritorna il valore numerico della carta
    public int getValue() {

        return value;
    }

    //metodo usato per confrontare le carte tenendo conto del fatto che l'asso e il tre hanno un valore più alto del re
    public int getValueForComparison() {
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
