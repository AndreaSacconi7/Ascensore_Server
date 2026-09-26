package polimi.ascensore.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import polimi.ascensore.model.Deck;

/**
 * Match configuration. Players choose the match size (2 to 4) when they join; a 40-card deck limits
 * players × max hand size to 40.
 */
@Component
public class GameSettings {

    private final int playersPerMatch;

    private final int maxHandSize;

    private final Duration turnTime;

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;

    public GameSettings(@Value("${ascensore.players-per-match:2}") int playersPerMatch,
                        @Value("${ascensore.max-hand-size:10}") int maxHandSize,
                        @Value("${ascensore.turn-seconds:30}") int turnSeconds) {
        this.turnTime = Duration.ofSeconds(Math.max(0, turnSeconds));
        if (maxHandSize < 1 || MIN_PLAYERS * maxHandSize > Deck.SIZE) {
            throw new IllegalArgumentException("max-hand-size must be between 1 and " + Deck.SIZE / MIN_PLAYERS);
        }
        this.maxHandSize = maxHandSize;
        if (!isValidMatchSize(playersPerMatch)) {
            throw new IllegalArgumentException("players-per-match " + playersPerMatch + " is not a valid match size");
        }
        this.playersPerMatch = playersPerMatch;
    }

    /**
     * Match size used when a client does not ask for one.
     */
    public int playersPerMatch() {
        return playersPerMatch;
    }

    /**
     * Time to bet or play before the server acts for the player; zero means no limit.
     */
    public Duration turnTime() {
        return turnTime;
    }

    public boolean isValidMatchSize(int players) {
        return players >= MIN_PLAYERS && players <= MAX_PLAYERS && players * maxHandSize <= Deck.SIZE;
    }

    /**
     * The requested match size if valid, otherwise the default.
     */
    public int matchSize(Integer requested) {
        return requested != null && isValidMatchSize(requested) ? requested : playersPerMatch;
    }

    public int maxHandSize() {
        return maxHandSize;
    }
}
