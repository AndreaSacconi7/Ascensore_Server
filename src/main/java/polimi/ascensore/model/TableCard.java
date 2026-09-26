package polimi.ascensore.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What is on the table: the current trick, the briscola and the order players act in.
 */
public class TableCard {

    // Cards of the current trick, in the order they were played
    private final List<Card> playedCards = new ArrayList<>();

    private Card briscola;

    // Order of play for the current trick (or of betting, at the start of a set)
    private List<GamePlayer> playerListOrder = new ArrayList<>();

    /**
     * Rotates the order so that {@code first} acts first, keeping the seating order.
     */
    public void updatePlayerListOrder(GamePlayer first) {
        for (int i = 0; i < playerListOrder.size(); i++) {
            if (playerListOrder.get(i).getNickname().equals(first.getNickname())) {
                List<GamePlayer> rotated = new ArrayList<>(playerListOrder.subList(i, playerListOrder.size()));
                rotated.addAll(playerListOrder.subList(0, i));
                playerListOrder = rotated;
                return;
            }
        }
    }

    public void resetPlayedCard() {
        playedCards.clear();
    }

    public List<Card> getPlayedCards() {
        return playedCards;
    }

    public Card getBriscola() {
        return briscola;
    }

    public void setBriscola(Card briscola) {
        this.briscola = briscola;
    }

    public List<GamePlayer> getPlayerListOrder() {
        return playerListOrder;
    }

    public void setPlayerListOrder(List<GamePlayer> playerListOrder) {
        this.playerListOrder = playerListOrder;
    }
}
