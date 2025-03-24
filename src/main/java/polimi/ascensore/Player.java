package polimi.ascensore;

import lombok.Getter;

public class Player {

    @Getter
    private final String nickName;
    @Getter
    private int score;
    @Getter
    private int bet;
    @Getter
    private int turnsWon;
    @Getter
    private Hand hand;
    @Getter
    private PlayerState playerState;


    public Player(String nickName) {
        this.nickName = nickName;
        this.score = 0;
        this.bet = 0;
        this.hand = new Hand();
        this.playerState = PlayerState.IDLE;
    }

    public void updateScore() {
        if(turnsWon == bet){
            score = score + (10 * bet) + 10;
        } else {
            int diff = Math.abs(turnsWon - bet);
            score = score - (diff * 10);
        }
    }

    public void updateTurnsWon(){
        this.turnsWon++;
    }

    public void updateBet(int newBet) {
        this.bet = newBet;
    }

    public void updateState(PlayerState playerState){
        this.playerState = playerState;
    }

    public void resetTurnsWon(){
        this.turnsWon = 0;
    }

    public void resetBet(){
        this.bet = 0;
    }
}
