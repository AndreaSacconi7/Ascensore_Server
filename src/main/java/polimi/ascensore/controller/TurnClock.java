package polimi.ascensore.controller;

import java.time.Duration;

/**
 * Schedules turn deadlines. The action runs on the match's own loop, like every other change to the match.
 */
public interface TurnClock {

    /**
     * @return a handle that cancels the action if it has not run yet
     */
    Runnable schedule(Duration delay, Runnable action);
}
