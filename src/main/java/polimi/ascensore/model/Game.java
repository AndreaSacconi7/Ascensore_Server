package polimi.ascensore.model;

import polimi.ascensore.model.exception.PlayerNickNameDoesNotExist;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

//NOTE:
//una partita è composta da 19 set e ogni set è composto da N round. All'interno di un round ci sono 8 turni
//SET: partita che si conclude quando i giocatori hanno giocato tutte le carte in mano
//ROUND = round che si conclude quando tutti i giocatori hanno giocato una carta
//NUMTURN = turno di gioco, si conclude quando un giocatore ha giocato una carta o scomesso
//Il gioco si conclude quando si sono eseguiti 19 round

public class Game {

    private final int NUM_PLAYER = 4;

    List<Player> players;
    //Round indica la partita (per gestire carte e scommesse)
    //numTurn indica il turno di gioco (per gestire le azioni dei giocatori)

    private int set;

    private int round;

    private int numTurn;

    private Deck deck;

    private TableCard tableCard;

    public Game() {
        this.players = new ArrayList<>();
        this.round = 1;
        this.numTurn = 0;
        this.deck = new Deck();
        this.tableCard = new TableCard();
    }

    public void startGame() throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        deck.createCardDeck();
    }

    public void distributeCards() {
        for(Player p : players) {
            for(int i = 0; i < NUM_PLAYER; i++) {
                //aggiungp #set carte per ogni giocatore
                for(int j = 0; j < set; j++)
                    p.addCardToHand(deck.getDeckcards().pop());
            }
        }
        //aggiungo la briscola tranne nel set 10
        if(!deck.getDeckcards().isEmpty())
            tableCard.setBriscola(deck.getDeckcards().pop());
        else
            tableCard.setBriscola(null);
    }

    public void addPlayer(String nickName) {
        Player player = new Player(nickName);
        players.add(player);
    }

    public void updateSet() {
        this.set++;
    }

    public void updateRound() {
        this.round++;
    }

    public void resetRound(){
        this.round = 0;
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

    public int getSet() {
        return set;
    }

    public int getRound() {
        return round;
    }

    public int getNumTurn() {
        return numTurn;
    }

    public Deck getDeck() {
        return deck;
    }

    public TableCard getTableCard() {
        return tableCard;
    }

    public List<Player> getPlayers() {
        return players;
    }

}
