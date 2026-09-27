package polimi.ascensore.model;

import polimi.ascensore.persistence.Player;
import polimi.ascensore.model.exception.InvalidCard;

import java.util.ArrayList;
import java.util.List;

public class GamePlayer {

    private String supabaseId;

    private String sessionId;

    private String nickname;

    private int score;

    private int bet;

    private int roundsWon;

    private List<Card> hand;

    private PlayerState playerState;

    public GamePlayer(Player player, String sessionId){
        this.supabaseId = player.getSupabaseUid();
        this.sessionId = sessionId;
        this.nickname = player.getNickname();
        this.score = 0;
        this.bet = 0;
        this.hand = new ArrayList<>();
        this.playerState = PlayerState.IDLE;
    }

    /**
     * A player of a match restored after a restart. They have no socket until they reconnect.
     */
    public GamePlayer(MatchState.Seat seat) {
        this.supabaseId = seat.supabaseUid();
        this.nickname = seat.nickname();
        this.score = seat.score();
        this.bet = seat.bet();
        this.roundsWon = seat.roundsWon();
        this.hand = new ArrayList<>(seat.hand());
        this.playerState = seat.state();
    }

    public MatchState.Seat toSeat() {
        return new MatchState.Seat(supabaseId, nickname, score, bet, roundsWon, List.copyOf(hand), playerState);
    }

    public void updateScore() {
        score = score + GameRules.setScore(bet, roundsWon);
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

    public String getSupabaseId() {
        return supabaseId;
    }

    public String getNickname() {
        return nickname;
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

    public String getUsername() {
        return nickname;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
