package polimi.ascensore.network.message;

import polimi.ascensore.model.Card;

import java.util.List;

public class HandUpdate implements ExecutableInClient {

    private final List<Card> cards;

    public HandUpdate(List<Card> cards){
        this.cards = cards;
    }

    public List<Card> getCards() {
        return cards;
    }
}
