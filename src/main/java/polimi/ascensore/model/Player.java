package polimi.ascensore.model;

import polimi.ascensore.model.exception.InvalidCard;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private final String nickName;

    private int score;

    private int bet;

    private int roundsWon;

    private List<Card> hand;

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

    public void removeCardFromHand(Seed seed, int value) throws InvalidCard {
        for(Card card : hand){
            if(card.getSeed() == seed && card.getValue() == value){
                hand.remove(card);
                return;
            }
        }
        throw new InvalidCard();
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

    public String getNickName() {
        return nickName;
    }

    public int getScore() {
        return score;
    }

    public int getBet() {
        return bet;
    }

    public int getRoundsWon() {
        return roundsWon;
    }

    public List<Card> getHand() {
        return hand;
    }

    public void setHand(List<Card> hand) {
        this.hand = hand;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }
}
