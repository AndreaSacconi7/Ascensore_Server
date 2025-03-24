package polimi.ascensore;

import lombok.Getter;

public class Player {

    @Getter
    private final String name;
    @Getter
    private int score;
    @Getter
    private int bet;
    @Getter
    private Hand hand;
    @Getter
    private State state;


    public Player(String name) {
        this.name = name;
        this.score = 0;
        this.bet = 0;
        this.hand = new Hand();
        this.state = State.IDLE;
    }

    public void updateScore(int newScore) {
        this.score = this.score + newScore;
    }

    public void updateBet(int newBet) {
        this.bet = newBet;
    }

    public void updateState(State state){
        this.state = state;
    }
}
