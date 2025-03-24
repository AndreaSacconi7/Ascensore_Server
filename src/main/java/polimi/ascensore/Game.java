package polimi.ascensore;

import lombok.Getter;
import org.hibernate.mapping.Table;
import polimi.ascensore.exception.PlayerNickNameDoesNotExist;

import java.io.FileNotFoundException;
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
    private TableCard tableCard;

    public Game() {
        this.players = new ArrayList<>();
        this.round = 0;
        this.numTurn = 0;
        this.deck = new Deck();
        this.tableCard = new TableCard();
    }

    public void startGame() throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        deck.createCardDeck();
    }

    public void addPlayer(String nickName) {
        Player player = new Player(nickName);
        players.add(player);
    }

    public void updateRound() {
        this.round++;
    }

    public void updateNumTurn() {
        this.numTurn++;
    }

    public void resetNumTurn(){
        this.numTurn = 0;
    }

    public Player getPlayerByNickName(String nickname) throws PlayerNickNameDoesNotExist {
        for(Player p : players) {
            if(p.getNickName().equals(nickname)) {
                return p;
            }
        }
        throw new PlayerNickNameDoesNotExist();
    }

}
