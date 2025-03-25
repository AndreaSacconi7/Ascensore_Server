package polimi.ascensore;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class TableCard {

    @Getter
    private LinkedList<Card> playedCards;
    @Getter
    private Player winnerPlayer;
    @Getter
    @Setter
    private Card briscola;
    //lista che determina l'ordine di gioco dei player
    @Setter
    @Getter
    private List<Player> playerListOrder;

    public TableCard() {
        this.playedCards = new LinkedList<>();
        this.winnerPlayer = null;
        this.briscola = null;
        this.playerListOrder = new ArrayList<>();
    }

    public void updatePlayerListOrder(Player winnerPlayer) {
        List<Player> newPlayerListOrder = new ArrayList<>();
        for(int i = 0; i < playerListOrder.size(); i++){
            if(playerListOrder.get(i).equals(winnerPlayer)){
                //trovato winnerPlayer
                for(int j = i + 1; j < playerListOrder.size(); j++){
                    //aggiungo i player che seguono il winnerPlayer
                    newPlayerListOrder.add(playerListOrder.get(j));
                }
                //infine aggiungo il winnerPlayer così si ha l'ordine corretto
                newPlayerListOrder.add(playerListOrder.get(i));
                //aggiorno la lista di player con il nuovo ordine
                setPlayerListOrder(newPlayerListOrder);
                return;
            }else{
                //aggiungo i player che precedono il winnerPlayer
                newPlayerListOrder.add(playerListOrder.get(i));
            }
        }
    }

    public void updateWinnerPlayer(Player player) {
        winnerPlayer = player;
    }

    //metodo che aggiorna il numero di turni vinti dal giocatore vincitore del turno
    public void updatePlayerRoundsWon(){
        winnerPlayer.updateRoundsWon();
    }

    public void putCard(Card card){
        playedCards.push(card);
    }

    public void resetPlayedCard(){
        playedCards.clear();
    }

}
