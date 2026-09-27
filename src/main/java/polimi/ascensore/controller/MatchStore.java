package polimi.ascensore.controller;

import polimi.ascensore.model.MatchState;

import java.util.List;

/**
 * Keeps a copy of every started match outside the server's memory, so that matches survive a restart
 * (a deploy or a crash). Saves and deletes return at once: the writing happens in the background.
 */
public interface MatchStore {

    /**
     * Replaces the saved state of the match with {@code state}.
     */
    void save(MatchState state);

    /**
     * Forgets a match that ended.
     */
    void delete(String matchId);

    /**
     * The matches that were in progress when the server last stopped.
     */
    List<MatchState> loadAll();

    /**
     * Writes whatever is still waiting to be written; called when the server shuts down.
     */
    default void flush() {
    }

    /**
     * Keeps nothing: matches end with the server.
     */
    MatchStore NONE = new MatchStore() {
        @Override
        public void save(MatchState state) {
        }

        @Override
        public void delete(String matchId) {
        }

        @Override
        public List<MatchState> loadAll() {
            return List.of();
        }
    };
}
