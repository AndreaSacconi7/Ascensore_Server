package polimi.ascensore.network.message;

import polimi.ascensore.model.Card;

public class PlayedCardUpdate implements ExecutableInClient{

    private final Card playedCard;
    private final String nickname;

    public PlayedCardUpdate(Card playedCard, String nickname) {
        this.playedCard = playedCard;
        this.nickname = nickname;
    }

    public Card getPlayedCard() {
        return playedCard;
    }

    public String getNickname() {
        return nickname;
    }
}
