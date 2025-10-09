package polimi.ascensore.controller;

import org.springframework.stereotype.Service;
import polimi.ascensore.model.exception.PlayerNickNameDoesNotExist;
import polimi.ascensore.model.Card;
import polimi.ascensore.model.Game;
import polimi.ascensore.model.Player;
import polimi.ascensore.model.PlayerState;
import polimi.ascensore.model.exception.PlayerNicknameAlreadyExistException;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.newserver.MySocketHandler;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

import static polimi.ascensore.model.Game.NUM_PLAYER;

@Service
public class Controller {

    private final Game game;

    private MySocketHandler gameNotifications;

    public Controller() {

        game = new Game();
        this.gameNotifications = null;
    }

    public void setSocketHandler(MySocketHandler mySocketHandler) {

        this.gameNotifications = mySocketHandler;
    }

    public void notifyAllClients(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, nickname, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.forwardUpdateToAll(message, nickname);
        }
    }

    public void notifySingleClient(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, nickname, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.forwardUpdateToSingleClient(message, nickname);
        }
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

        List<String> playerNicknames = new LinkedList<>();

        for(Player p : game.getPlayers()){
            playerNicknames.add(p.getNickName());
        }

        StartingGame startingGame = new StartingGame(playerNicknames);

        notifyAllClients(startingGame, "", MessageType.STARTING_GAME);

        for(Player p : game.getPlayers()){
            //notifico a ogni player le sue carte in mano
            List<Card> handCard = p.getHand();
            HandUpdate handUpdate = new HandUpdate(handCard);
            notifySingleClient(handUpdate, p.getNickName(), MessageType.HAND_UPDATE);
        }
        //notifico a tutti la briscola
        Card briscola = game.getTableCard().getBriscola();
        BriscolaUpdate briscolaUpdate = new BriscolaUpdate(briscola);
        notifyAllClients(briscolaUpdate, "", MessageType.BRISCOLA_UPDATE);

        //imposto il primo player di turno per scommettere
        /*Player firstPlayer = game.getTableCard().getPlayerListOrder().get(0);
        firstPlayer.updateState(PlayerState.BET);
        TurnUpdate turnUpdate = new TurnUpdate(firstPlayer.getNickName(), PlayerState.BET);
        notifyAllClients(turnUpdate, firstPlayer.getNickName(), MessageType.TURN_UPDATE);*/

        Player firstPlayer = game.getTableCard().getPlayerListOrder().get(game.getNumTurn());
        firstPlayer.updateState(PlayerState.BET);
        PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(PlayerState.BET, firstPlayer.getNickName());
        notifyAllClients(playerStateUpdate, firstPlayer.getNickName(), MessageType.PLAYER_STATE_UPDATE);
    }

    public void addPlayer(String nickName, String clientSessionId) {

        LinkedList<String> connectedPlayers = new LinkedList<>();
        boolean isLogged = false;
        LoginResponse loginResponse;

        for(Player p: game.getPlayers()){
            connectedPlayers.add(p.getNickName());
        }

        try{
            game.addPlayer(nickName);
            isLogged = true;
            gameNotifications.addNicknameToSessionIdNode(nickName, clientSessionId);

        }catch(PlayerNicknameAlreadyExistException e){
            System.out.println("Nickname already exists");
        }

        loginResponse = new LoginResponse(isLogged, nickName, connectedPlayers);

        notifyAllClients(loginResponse, nickName, MessageType.LOGIN_RESPONSE);

        //controllo condizione di inizio partita
        if(game.getPlayers().size() == NUM_PLAYER){
            startGame();
        }
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

            if(game.getNumTurn() == (NUM_PLAYER * 2) - 1){
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

            }else if(game.getNumTurn() <= NUM_PLAYER){
                //turni da 0 a 3 per scommettere sulle prese
                game.updateNumTurn();
                Player nextPlayer = game.getTableCard().getPlayerListOrder().get(game.getNumTurn());
                nextPlayer.updateState(PlayerState.BET);
                //TODO: notifica client prossimo player a scommettere
            }else{
                //turni da 4 a 7 per giocare
                game.updateNumTurn();
                Player nextPlayer = game.getTableCard().getPlayerListOrder().get(game.getNumTurn() - NUM_PLAYER);
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

    /*public void addClientHandler(MySocketHandler clientHandler) {
        synchronized (gameNotifications) {
            gameNotifications.add(clientHandler);
        }
    }*/
}