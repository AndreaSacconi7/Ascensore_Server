package polimi.ascensore;

import polimi.ascensore.exception.PlayerNickNameDoesNotExist;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class Controller {

    private final int NUM_PLAYERS = 4;

    private final Game game;

    public Controller() {

        game = new Game();
    }

    public void startGame() {

        try {
            game.startGame();
            game.distributeCards();

        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found");
        } catch (InstantiationException e) {
            System.out.println("Instantiation error");
        } catch (IllegalAccessException e) {
            System.out.println("Illegal access");
        }
    }

    public void addPlayer(String nickName) {
        //TODO: controllare se il player è già presente
        game.addPlayer(nickName);
    }

    public void putCard(int indexHand, String nickName) {

        try {
            Player player = game.getPlayerByNickName(nickName);

            //controllare se player è attivo, può giocare la carta
            if (player.getPlayerState() != PlayerState.PLAY) {
                //GenericMessage error = new GenericMessage(nickName + " can't put a card now");
                //notifyObservers(error, nickName);
                return;
            }
            //controllo se la carta è valida oppure non può giocarla per i vincoli sui seed
            Card card = player.getHand().get(indexHand);
            if(checkIfValidCard(card, player)){
                game.getTableCard().getPlayedCards().add(card);
                player.removeCardFromHand(indexHand);
                //cambio turno
                updateTurn(player);
            }else{
                //TODO: notifica client carta non valida

            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        }
    }

    public void setBet(int bet, String nickName) {

        try {
            Player player = game.getPlayerByNickName(nickName);
            //controllare se player è attivo, può giocare la carta
            if (player.getPlayerState() != PlayerState.BET) {
                //GenericMessage error = new GenericMessage(nickName + " can't put a card now");
                //notifyObservers(error, nickName);
                return;
            }
            //controllare se la scommessa è valida oppure bet totali == num giocatori (quindi invalida)
            if (checkIfValidBet(bet)) {
                player.updateBet(bet);
                updateTurn(player);

            } else {
                //TODO: notifica client scommessa non valida
            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        }
    }


    private void updateTurn(Player currentPlayer) {
        try {
            currentPlayer.updateState(PlayerState.WAIT);

            if(game.getNumTurn() == (NUM_PLAYERS * 2) - 1){
                //alla fine del turno 7 si calcola il vincitore del round e si aggiornano le prese fatte dal winner player
                String winnerTurnPlayerNickName = checkWinnerRoundPlayer();
                Player winnerTurnPlayer = game.getPlayerByNickName(winnerTurnPlayerNickName);
                game.getTableCard().updateWinnerPlayer(winnerTurnPlayer);
                game.getTableCard().updatePlayerRoundsWon();

                //entro in questo if solo se ho finito tutti i round del set
                if(game.getRound() + 1 == game.getSet()) {
                    //finiti tutti i round del set quindi si calcolano punteggi
                    updateScore();
                    game.updateSet();
                    game.resetRound();
                    game.resetNumTurn();
                    resetBetAndRoundsWon();
                    //aggiorna deck e distribuisce carte e briscola
                    game.getDeck().shuffleDeck();
                    game.distributeCards();
                    //aggiorna lista player order per il prossimo set
                    Player nextPlayer = game.getPlayers().get(game.getRound());
                    game.getTableCard().updatePlayerListOrder(nextPlayer);
                    game.getTableCard().getPlayerListOrder().get(0).updateState(PlayerState.BET);
                    //TODO: notifica client fine set
                }else{
                    //aggiorno playerList per il prossimo round (devo cambiare l'ordine di gioco del player)
                    game.updateRound();
                    game.resetNumTurn();
                    game.getTableCard().updatePlayerListOrder(winnerTurnPlayer);
                    game.getTableCard().getPlayerListOrder().get(0).updateState(PlayerState.BET);
                    //TODO: notifica client fine round
                }

            }else if(game.getNumTurn() <= NUM_PLAYERS){
                //turni da 0 a 3 per scommettere sulle prese
                game.updateNumTurn();
                Player nextPlayer = game.getTableCard().getPlayerListOrder().get(game.getNumTurn());
                nextPlayer.updateState(PlayerState.BET);
                //TODO: notifica client prossimo player a scommettere
            }else{
                //turni da 4 a 7 per giocare
                game.updateNumTurn();
                Player nextPlayer = game.getTableCard().getPlayerListOrder().get(game.getNumTurn() - NUM_PLAYERS);
                nextPlayer.updateState(PlayerState.PLAY);
                //TODO: notifica client prossimo player a giocare
            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        }
    }

    private String checkWinnerRoundPlayer(){
        LinkedList<Card> playedCards = game.getTableCard().getPlayedCards();
        int winnerIndex = 0;
        for(int i = 0; i < playedCards.size(); i++){
            if(!cardMajorThanOtherCard(playedCards.get(winnerIndex), playedCards.get(i))){
                winnerIndex = i;
            }
        }
        //carte e player hanno stesso ordine quindi posso prendere il player vincitore con l'index della carta vincitrice
        return game.getTableCard().getPlayerListOrder().get(winnerIndex).getNickName();
    }

    private boolean checkIfValidBet(int bet) {
        int totalBet = 0;
        for(Player p : game.getPlayers()) {
            totalBet = totalBet + p.getBet();
        }
        if(totalBet == bet) {
            return false;
        }
        return true;
    }

    private boolean checkIfValidCard(Card card, Player player) {
        if(game.getTableCard().getPlayedCards().isEmpty()){
            return true;
        }else if(card.getSeed() == game.getTableCard().getPlayedCards().get(0).getSeed()){
            //se la carta giocata ha lo stesso seed della prima carta giocata allora è valida sicuro
            return true;
        }

        for(int i = 0; i < player.getHand().size(); i++){
            if(player.getHand().get(i).getSeed() == game.getTableCard().getPlayedCards().get(0).getSeed()){
                //se il player ha in mano una carta dello stesso seed rispetto la prima carta giocata deve giocarla
                return false;
            }
        }
        return true;
    }

    //TRUE se card1 vince, FALSE altrimenti
    private boolean cardMajorThanOtherCard(Card card1, Card card2) {
        if (card1 == null) {
            return false;
        } else if (card2 == null) {
            return true;
        }

        if (card1.getSeed() == card2.getSeed()) {
            if (card1.getValue() > card2.getValue()) {
                //card1 vince
                return true;
            } else {
                //card2 vince
                return false;
            }
        } else {
            if (card1.getSeed() == game.getTableCard().getBriscola().getSeed()) {
                //card1 vince
                return true;
            } else if (card2.getSeed() == game.getTableCard().getBriscola().getSeed()) {
                //card2 vince
                return false;
            } else {
                //nessuna delle due carte è briscola e non sono dello stesso seed quindi vince quella lanciata prima.
                return true;
            }
        }
    }

    //metodo che aggiorna gli score di tutti i player
    private void updateScore() {
        for(Player p : game.getPlayers()) {
            p.updateScore();
        }
    }

    private void resetBetAndRoundsWon() {
        for(Player p : game.getPlayers()) {
            p.resetBet();
            p.resetRoundsWon();
        }
    }
}