package polimi.ascensore.controller;

public interface GameLifeCycleListener {

    void onGameEnded(GameController gameController);

    /**
     * The match removed a player on its own (too many turns timed out); they are free to join another one.
     */
    default void onPlayerRemoved(GameController gameController, String supabaseUid) {
    }
}
