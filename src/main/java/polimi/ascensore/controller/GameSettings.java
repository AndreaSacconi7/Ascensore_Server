package polimi.ascensore.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import polimi.ascensore.model.Deck;

/**
 * Match configuration. A 40-card deck limits players × max hand size to 40.
 */
@Component
public class GameSettings {

    private final int playersPerMatch;

    private final int maxHandSize;

    public GameSettings(@Value("${ascensore.players-per-match:2}") int playersPerMatch,
                        @Value("${ascensore.max-hand-size:10}") int maxHandSize) {
        if (playersPerMatch < 2 || playersPerMatch > 4) {
            throw new IllegalArgumentException("players-per-match must be between 2 and 4, was " + playersPerMatch);
        }
        if (maxHandSize < 1 || playersPerMatch * maxHandSize > Deck.SIZE) {
            throw new IllegalArgumentException("max-hand-size " + maxHandSize + " does not fit a "
                    + Deck.SIZE + "-card deck with " + playersPerMatch + " players");
        }
        this.playersPerMatch = playersPerMatch;
        this.maxHandSize = maxHandSize;
    }

    public int playersPerMatch() {
        return playersPerMatch;
    }

    public int maxHandSize() {
        return maxHandSize;
    }
}
