package polimi.ascensore;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Player {

    @Getter
    private final String nickName;
    @Getter
    private int score;
    @Getter
    private int bet;
    @Getter
    private int roundsWon;
    @Getter
    @Setter
    private List<Card> hand;
    @Getter
    private PlayerState playerState;


    public Player(String nickName) {
        this.nickName = nickName;
        this.score = 0;
        this.bet = 0;
        this.hand = new ArrayList<>();
        this.playerState = PlayerState.IDLE;
    }

    public void updateScore() {
        if(roundsWon == bet){
            score = score + (10 * bet) + 10;
        } else {
            int diff = Math.abs(roundsWon - bet);
            score = score - (diff * 10);
        }
    }

    public void updateRoundsWon(){
        this.roundsWon++;
    }

    public void updateBet(int newBet) {
        this.bet = newBet;
    }

    public void updateState(PlayerState playerState){
        this.playerState = playerState;
    }

    public void removeCardFromHand(int index){
        hand.remove(index);
    }

    public void addCardToHand(Card card){
        hand.add(card);
    }

    public void resetRoundsWon(){
        this.roundsWon = 0;
    }

    public void resetBet(){
        this.bet = 0;
    }
}
