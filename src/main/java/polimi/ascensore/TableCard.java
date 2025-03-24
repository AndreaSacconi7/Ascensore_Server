package polimi.ascensore;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;

public class TableCard {

    @Getter
    private HashMap<Card, Player> playedCards;
    @Getter
    private Player winnerPlayer;
    @Getter
    private Card briscola;
    //lista che determina l'ordine di gioco dei player
    @Getter
    private List<Player> playerList;

    public void updateBriscola(Card card){
        briscola = card;
    }

    public void updatePlayerList(List<Player> playerList) {
        this.playerList = playerList;
    }

    public void updateWinnerPlayer(Player player) {
        winnerPlayer = player;
    }

    //metodo che aggiorna il numero di turni vinti dal giocatore vincitore del turno
    public void updatePlayerTurnsWon(){
        winnerPlayer.updateTurnsWon();
    }

}
