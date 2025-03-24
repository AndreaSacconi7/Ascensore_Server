package polimi.ascensore;

import polimi.ascensore.exception.PlayerNickNameDoesNotExist;

import java.io.FileNotFoundException;

public class Controller {

    private final int NUM_PLAYERS = 4;

    private final Game game;

    public Controller() {

        game = new Game();
    }

    public void startGame() {

        try {
            game.startGame();

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

    public void addPlayer(String name) {
        game.addPlayer(name);
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
            //TODO: controllare se la carta è valida oppure non può giocarla per i vincoli sui seed
            Card card = player.getHand().getCardByHand(indexHand);

            //cambio turno
            updateTurn();

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
            if(checkIfValidBet(bet)){
                player.updateBet(bet);

            }else{
                //TODO: notifica client scommessa non valida
            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        }
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

    private boolean checkIfValidCard(Card card){
        /*if(game.getTableCard().isEmpty()){
            return true;
        }
        for(Card c : game.getTableCard().keySet()){
            if(c.getSeed() == card.getSeed()){
                return true;
            }
        }*/
        return false;
    }

    private void updateTurn(){
        if(game.getNumTurn() == NUM_PLAYERS){
            updateScore();
            resetBetAndTurnsWon();
            game.updateRound();
            game.resetNumTurn();
        }else{
            game.updateNumTurn();
        }
    }

    //metodo che aggiorna gli score di tutti i player
    private void updateScore() {
        for(Player p : game.getPlayers()) {
            p.updateScore();
        }
    }

    private void resetBetAndTurnsWon() {
        for(Player p : game.getPlayers()) {
            p.resetBet();
            p.resetTurnsWon();
        }
    }
}