package polimi.ascensore.network.message;

import polimi.ascensore.model.Card;

public class BriscolaUpdate implements ExecutableInClient {

    private final Card briscolaCard;

    public BriscolaUpdate(Card briscolaCard) {
        this.briscolaCard = briscolaCard;
    }

    public Card getBriscolaCard() {
        return briscolaCard;
    }
}
