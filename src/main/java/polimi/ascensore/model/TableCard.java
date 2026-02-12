package polimi.ascensore.model;

import java.util.*;

public class TableCard {


    private LinkedList<Card> playedCards;

    private GamePlayer winnerPlayer;

    private Card briscola;
    //lista che determina l'ordine di gioco dei player

    private List<GamePlayer> playerListOrder;

    //private Player currentFirstPlayerBetting;

    public TableCard() {
        this.playedCards = new LinkedList<>();
        this.winnerPlayer = null;
        this.briscola = null;
        this.playerListOrder = new ArrayList<>();
    }

    public void updatePlayerListOrder(GamePlayer winnerPlayer) {
        List<GamePlayer> newPlayerListOrder = new ArrayList<>();
        for(int i = 0; i < playerListOrder.size(); i++){
            if(playerListOrder.get(i).getNickname().equals(winnerPlayer.getNickname())) {
                //trovato winnerPlayer
                for (int j = i; j < playerListOrder.size(); j++) {
                    //aggiungo i player che seguono il winnerPlayer
                    newPlayerListOrder.add(playerListOrder.get(j));
                }
                for (int j = 0; j < i; j++) {
                    //aggiungo i player che precedono il winnerPlayer
                    newPlayerListOrder.add(playerListOrder.get(j));
                }
                //aggiorno la lista di player con il nuovo ordine
                setPlayerListOrder(newPlayerListOrder);
                return;
            }
        }
    }

    public void updateWinnerPlayer(GamePlayer player) {
        winnerPlayer = player;
    }

    //metodo che aggiorna il numero di turni vinti dal giocatore vincitore del turno
    public void updatePlayerRoundsWon(){
        winnerPlayer.updateRoundsWon();
    }

    /*public void setCurrentFirstPlayerBetting(){
        for(int i = 0; i < playerListOrder.size(); i++){
            if(playerListOrder.get(i).equals(winnerPlayer)){
                if(i != playerListOrder.size() - 1){
                    currentFirstPlayerBetting = playerListOrder.get(i + 1);
                } else {
                    currentFirstPlayerBetting = playerListOrder.get(0);
                }
            }
        }
    }

    public Player getCurrentFirstPlayerBetting() {
        return currentFirstPlayerBetting;
    }*/

    public void putCard(Card card){
        playedCards.push(card);
    }

    public void resetPlayedCard(){
        playedCards.clear();
    }

    public LinkedList<Card> getPlayedCards() {
        return playedCards;
    }

    public GamePlayer getWinnerPlayer() {
        return winnerPlayer;
    }

    public Card getBriscola() {
        return briscola;
    }

    public void setBriscola(Card briscola) {
        this.briscola = briscola;
    }

    public List<GamePlayer> getPlayerListOrder() {
        return playerListOrder;
    }

    public void setPlayerListOrder(List<GamePlayer> playerListOrder) {
        this.playerListOrder = playerListOrder;
    }

}
