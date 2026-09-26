package polimi.ascensore.model;

public enum PlayerState {

    // In a match that has not started yet
    IDLE,

    // Someone else's turn
    WAIT,

    // Must bet
    BET,

    // Must play a card
    PUT,

    // Left the match
    EXIT
}
