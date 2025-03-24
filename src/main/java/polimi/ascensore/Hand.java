package polimi.ascensore;

import lombok.Getter;

public class Hand {

    @Getter
    private Card[] cards;

    public Hand() {
        this.cards = new Card[5];
    }

    public void addCards(Card[] cards) {
        this.cards = cards;
    }
}
