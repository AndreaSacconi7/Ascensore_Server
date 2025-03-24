package polimi.ascensore;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Game {

    @Getter
    List<Player> players;
    //Round indica la partita (per gestire carte e scommesse)
    //numTurn indica il turno di gioco (per gestire le azioni dei giocatori)
    @Getter
    private int round;
    @Getter
    private int numTurn;
    @Getter
    private Deck deck;
    @Getter
    private HashMap<Card, Player> table;
    @Getter
    private Player winnerPlayer;
    @Getter
    private Seed briscola;

    public Game() {
        this.players = new ArrayList<>();
        this.round = 0;
        this.numTurn = 0;
        this.deck = new Deck();
        this.table = new HashMap<>();
        winnerPlayer = null;

    }

    public void addPlayer(String name) {
        Player player = new Player(name);
        players.add(player);
    }

    public void updateRound() {
        this.round++;
    }

    public void updateNumTurn() {
        this.numTurn++;
    }

    public void updateTable(Card card) {
        //inserire logica per capire se è una winnerCard
        table.put(card, players.get(numTurn));
    }

    public void updateWinnerPlayer(Player player) {
        winnerPlayer = player;
    }

    public void updateBriscola(Seed briscola) {
        this.briscola = briscola;
    }

}
