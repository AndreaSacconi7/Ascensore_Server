package polimi.ascensore.model;

import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
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

    public static int setVariation = -1;

    public static final int NUM_PLAYER = 2;

    List<GamePlayer> players;
    //Round indica la partita (per gestire carte e scommesse)
    //numTurn indica il turno di gioco (per gestire le azioni dei giocatori)
    private int set;

    private int round;

    private int numTurn;

    private Deck deck;

    private TableCard tableCard;

    public Game() {
        this.players = new ArrayList<>();
        this.set = 1;
        resetRound();
        resetNumTurn();
        this.deck = new Deck();
        this.tableCard = new TableCard();
    }

    public void startGame() throws FileNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        deck.createCardDeck();
        tableCard.setPlayerListOrder(players);
    }

    public void distributeCards() {
        for(GamePlayer p : players) {
            //aggiungp #set carte per ogni giocatore
            for(int j = 0; j < set; j++)
                p.addCardToHand(deck.getDeckcards().pop());
        }
        //aggiungo la briscola tranne nel set 10
        if(!deck.getDeckcards().isEmpty() && set != 10)
            tableCard.setBriscola(deck.getDeckcards().pop());
        else
            tableCard.setBriscola(null);
    }

    public void addPlayer(Player player, String sessionId) throws CannotAddPlayerNowException {
        for( GamePlayer p : players) {
            if(p.getPlayerState() != PlayerState.IDLE)
                throw new CannotAddPlayerNowException();
        }
        GamePlayer gamePlayer = new GamePlayer(player, sessionId);
        players.add(gamePlayer);
    }

    //rimuovo giocatori disconnessi dal gioco per far continuare la partita
    public void removePlayer(String nickname) throws PlayerNickNameDoesNotExist {
        for(GamePlayer p : players) {
            if(p.getNickname().equals(nickname)) {
                players.remove(p);
                return;
            }
        }
        throw new PlayerNickNameDoesNotExist();
    }

    public boolean checkIfEndGame(){
        //ho finito ultimo set
        return setVariation == -1 && set == 1;
    }

    public void updateSet() {
        //quando arrivo a dieci devo diminuire i set anzichè aumentarli
        if(set == 10)
            setVariation = -1;

        this.set = this.set + setVariation;
    }

    public List<GamePlayer> endGame(){
        System.out.println("Game Over");

        return getResult();
    }

    private List<GamePlayer> getResult(){
        List<GamePlayer> results = new ArrayList<>();
        GamePlayer maxScorePlayer;
        for(GamePlayer k : players){
            maxScorePlayer = k;
            for(GamePlayer p : players){
                if(p.getScore() > maxScorePlayer.getScore() && p.getPlayerState() != PlayerState.EXIT && !results.contains(p))
                    maxScorePlayer = p;
            }
            if(!results.contains(maxScorePlayer))
                results.add(maxScorePlayer);
        }

        return results;
    }

    public void updateRound() {
        this.round++;

        resetBriscolaInSet10();
    }

    //nel turno dieci la briscola è la prima carta giocata. quindi quando finisco il round la tolgo
    private void resetBriscolaInSet10(){
        if(getSet() == 10){
            getTableCard().setBriscola(null);
        }
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

    public GamePlayer getPlayerByNickName(String nickname) throws PlayerNickNameDoesNotExist {
        for(GamePlayer p : players) {
            if(p.getNickname().equals(nickname)) {
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

    public List<GamePlayer> getPlayers() {
        return players;
    }

}
