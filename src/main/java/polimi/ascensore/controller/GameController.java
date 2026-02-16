package polimi.ascensore.controller;

import org.springframework.stereotype.Service;
import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.*;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.InvalidCard;
import polimi.ascensore.model.exception.PlayerNickNameDoesNotExist;
import polimi.ascensore.network.message.*;
import polimi.ascensore.network.newserver.MySocketHandler;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static polimi.ascensore.model.Game.NUM_PLAYER;

@Service
public class GameController {

    private final Game game;

    private MySocketHandler gameNotifications;

    private GameLifeCycleListener gameLifeCycleListener;

    public GameController(GameLifeCycleListener gameLifeCycleListener) {

        game = new Game();
        this.gameNotifications = null;
        this.gameLifeCycleListener = gameLifeCycleListener;
    }

    public void setSocketHandler(MySocketHandler mySocketHandler) {

        this.gameNotifications = mySocketHandler;
    }

    public void notifyAllClients(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, messageType);
        synchronized (gameNotifications) {

            //sincronizzazione che dovrebbe servire ad evitare contrasti tra messaggi di Ping e messaggi di Update
            gameNotifications.forwardUpdateToAll(message, game.getPlayers());
        }
    }

    public void notifySingleClient(ExecutableInClient executable, String nickname, MessageType messageType) {

        Message message = new Message(executable, messageType);
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

        //invio la lista dei giocatori connessi a tutti i client che sono anche in ordine per il primo turno
        for(int i = 0; i < game.getTableCard().getPlayerListOrder().size(); i++){
            playerNicknames.add(game.getTableCard().getPlayerListOrder().get(i).getNickname());
        }

        StartingGame startingGame = new StartingGame(playerNicknames);

        notifyAllClients(startingGame, "", MessageType.STARTING_GAME);

        notifyDistributedCards();

        //imposto il primo player di turno per scommettere
        /*Player firstPlayer = game.getTableCard().getPlayerListOrder().get(0);
        firstPlayer.updateState(PlayerState.BET);
        TurnUpdate turnUpdate = new TurnUpdate(firstPlayer.getNickName(), PlayerState.BET);
        notifyAllClients(turnUpdate, firstPlayer.getNickName(), MessageType.TURN_UPDATE);*/
        for(GamePlayer p : game.getPlayers()){
            p.updateState(PlayerState.WAIT);
            PlayerStateUpdate initialStateUpdate = new PlayerStateUpdate(PlayerState.WAIT, p.getNickname());
            notifyAllClients(initialStateUpdate, p.getNickname(), MessageType.PLAYER_STATE_UPDATE);
        }

        GamePlayer firstPlayer = game.getTableCard().getPlayerListOrder().get(0);
        firstPlayer.updateState(PlayerState.BET);
        PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(PlayerState.BET, firstPlayer.getNickname());
        notifyAllClients(playerStateUpdate, firstPlayer.getNickname(), MessageType.PLAYER_STATE_UPDATE);
    }

    private void notifyDistributedCards(){
        for(GamePlayer p : game.getPlayers()){
            //notifico a ogni player le sue carte in mano
            List<Card> handCard = p.getHand();
            HandUpdate handUpdate = new HandUpdate(handCard);
            notifySingleClient(handUpdate, p.getNickname(), MessageType.HAND_UPDATE);
        }
        //notifico a tutti la briscola
        Card briscola = game.getTableCard().getBriscola();
        BriscolaUpdate briscolaUpdate = new BriscolaUpdate(briscola);
        notifyAllClients(briscolaUpdate, "", MessageType.BRISCOLA_UPDATE);
    }

    public void addPlayerToGame(Player player, String sessionId) throws CannotAddPlayerNowException {

        game.addPlayer(player, sessionId);
    }

    public int getNumPlayersInGame() {
        return game.getPlayers().size();
    }

    public void putCard(Seed seed, int value, String nickName) {

        try {
            GamePlayer player = game.getPlayerByNickName(nickName);

            //controllare se player è attivo, può giocare la carta
            if (player.getPlayerState() != PlayerState.PUT) {
                //GenericMessage error = new GenericMessage(nickName + " can't put a card now");
                //notifyObservers(error, nickName);
                TextMessage error = new TextMessage(nickName + " can't put a card now");
                notifyAllClients(error, nickName, MessageType.TEXT_MESSAGE);
                return;
            }
            //controllo se il player ha la carta in mano
            Card card = null;
            for(Card c : player.getHand()){
                if(c.getSeed() == seed && c.getValue() == value){
                    card = c;
                    break;
                }
            }
            if(card == null){
                //notifica client carta non valida
                TextMessage error = new TextMessage("Invalid card played by " + nickName + ". He does not have this card in hand");
                notifyAllClients(error, nickName, MessageType.TEXT_MESSAGE);
                return;
            }
            //controllo se la carta è valida oppure non può giocarla per i vincoli sui seed
            if(checkIfValidCard(card, player)){
                game.getTableCard().getPlayedCards().add(card);
                player.removeCardFromHand(seed, value);
                //notifico a tutti i player la carta giocata
                PlayedCardUpdate playedCardUpdate = new PlayedCardUpdate(card, nickName);
                notifyAllClients(playedCardUpdate, nickName, MessageType.PLAYED_CARD);
                //cambio turno
                updateTurn(player);
            }else{
                TextMessage error = new TextMessage("Invalid card played by " + nickName + ", must follow the seed of the first card played");
                notifyAllClients(error, nickName, MessageType.TEXT_MESSAGE);
            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        } catch (InvalidCard e) {
            System.out.println("Invalid card");
            TextMessage error = new TextMessage("Invalid card played by " + nickName);
            notifyAllClients(error, nickName, MessageType.TEXT_MESSAGE);
        }
    }

    public void setBet(int bet, String nickName) {

        try {
            GamePlayer player = game.getPlayerByNickName(nickName);
            //controllare se player è attivo, può giocare la carta
            if (player.getPlayerState() != PlayerState.BET) {
                //GenericMessage error = new GenericMessage(nickName + " can't put a card now");
                //notifyObservers(error, nickName);
                TextMessage error = new TextMessage(nickName + " can't set a bet now");
                notifyAllClients(error, nickName, MessageType.TEXT_MESSAGE);
                return;
            }
            //controllare se la scommessa è valida oppure bet totali == num giocatori (quindi invalida)
            if (checkIfValidBet(bet, nickName)) {
                player.updateBet(bet);
                //notifico a tutti i player la scommessa fatta
                SettedBetUpdate settedBetUpdate = new SettedBetUpdate(nickName, bet);
                notifyAllClients(settedBetUpdate, nickName, MessageType.SETTED_BET);
                updateTurn(player);

            } else {
                //notifica client scommessa non valida
                TextMessage error = new TextMessage("Invalid bet of " + nickName + ", total bet can't be equal to number of players");
                notifyAllClients(error, nickName, MessageType.TEXT_MESSAGE);
            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        }
    }

    public void playerExitGame(String nickname){

        try {
            //giocatori con stato EXIT non possono vincere la partita
            game.getPlayerByNickName(nickname).updateState(PlayerState.EXIT);
        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found in exitGame");
        }

        //notifico a tutti i player che questo player è uscito
        PlayerExitGame playerExitGameUpdate = new PlayerExitGame(nickname);
        notifyAllClients(playerExitGameUpdate, null, MessageType.PLAYER_EXIT_GAME);

        //TODO: per ora facciamo che se un giocatore esce dalla partita allora finisce. in seguito poi la facciamo continuare senza di lui
        endGameResult();
    }

    public void sendAllDataAfterReconnection(String nickname, String sessionId) {
        try {
            GamePlayer player = game.getPlayerByNickName(nickname);
            //aggiorno la sessionId del player riconnesso
            player.setSessionId(sessionId);

            //notifico al player le sue carte in mano
            List<String> playerNicknames = new LinkedList<>();
            for(GamePlayer p : game.getTableCard().getPlayerListOrder()){
                playerNicknames.add(p.getNickname());
            }
            //inizio partita per il giocatore riconnesso
            StartingGame startingGame = new StartingGame(playerNicknames);

            //carte in mano
            List<Card> handCard = player.getHand();
            HandUpdate handUpdate = new HandUpdate(handCard);

            //la briscola
            Card briscola = game.getTableCard().getBriscola();
            BriscolaUpdate briscolaUpdate = new BriscolaUpdate(briscola);

            notifySingleClient(startingGame, nickname, MessageType.STARTING_GAME);
            notifySingleClient(briscolaUpdate, nickname, MessageType.BRISCOLA_UPDATE);
            notifySingleClient(handUpdate, nickname, MessageType.HAND_UPDATE);

            //bet, prese fatte (round vinti) e score di tutti i player
            HashMap<String, Integer> scores = new HashMap<>();
            HashMap<String, Integer> bets = new HashMap<>();
            HashMap<String, Integer> roundsWon = new HashMap<>();

            for(GamePlayer p : game.getPlayers()){
                scores.put(p.getNickname(), p.getScore());
                bets.put(p.getNickname(), p.getBet());
                roundsWon.put(p.getNickname(), p.getRoundsWon());
            }
            InfoAfterReconnection infoAfterReconnection = new InfoAfterReconnection(scores, bets, roundsWon);
            notifySingleClient(infoAfterReconnection, nickname, MessageType.INFO_AFTER_RECONNECTION);

            //TODO: da inviare anche le carte giocate da tutti i player
            /*for(GamePlayer p : game.getPlayers()){
                if(game.getTableCard().getPlayedCards())
            }*/

            PlayerState playerState = player.getPlayerState();
            PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(playerState, nickname);
            notifySingleClient(playerStateUpdate, nickname, MessageType.PLAYER_STATE_UPDATE);

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found in sendAllDataAfterReconnection");
        }
    }


    private void updateTurn(GamePlayer currentPlayer) {
        try {
            currentPlayer.updateState(PlayerState.WAIT);
            //currentPlayer diventa WAIT
            PlayerStateUpdate playerStateUpdateCurrent = new PlayerStateUpdate(PlayerState.WAIT, currentPlayer.getNickname());
            notifyAllClients(playerStateUpdateCurrent, currentPlayer.getNickname(), MessageType.PLAYER_STATE_UPDATE);

            if(game.getNumTurn() == ((NUM_PLAYER * 2) - 1) + 2*(game.getRound())){
                //alla fine del turno 7 si calcola il vincitore del round e si aggiornano le prese fatte dal winner player
                String winnerTurnPlayerNickName = checkWinnerRoundPlayer();
                GamePlayer winnerTurnPlayer = game.getPlayerByNickName(winnerTurnPlayerNickName);
                game.getTableCard().updateWinnerPlayer(winnerTurnPlayer);
                game.getTableCard().updatePlayerRoundsWon();

                //entro in questo if solo se ho finito tutti i round del set
                if(game.getRound() + 1 == game.getSet()) {
                    //FINE SET
                    //finiti tutti i round del set quindi si calcolano punteggi
                    updateScore();
                    //aggiorna set, round e resetta numTurn, le carte giocate e le scommesse e le prese fatte
                    if(game.checkIfEndGame()){
                        //fine partita
                        endGameResult();
                        return;
                    }
                    game.updateSet();
                    game.resetRound();
                    game.resetNumTurn();
                    game.getTableCard().resetPlayedCard();
                    resetBetAndRoundsWon();
                    //aggiorna lista player order per il prossimo set
                    GamePlayer nextPlayer = game.getPlayers().get((game.getSet()-1)%NUM_PLAYER);
                    game.getTableCard().updatePlayerListOrder(nextPlayer);
                    game.getTableCard().getPlayerListOrder().get(0).updateState(PlayerState.BET);
                    //aggiorna deck e distribuisce carte e briscola
                    game.getDeck().shuffleDeck();
                    game.distributeCards();
                    //notifica client fine set con punteggi di tutto il set, vincitore round, nuovo playerListOrder e nuovo state per next player
                    HashMap<String, Integer> nextPlayerOrderAndScore = new HashMap<>();
                    for(GamePlayer p : game.getTableCard().getPlayerListOrder()){
                        nextPlayerOrderAndScore.put(p.getNickname(), p.getScore());
                    }
                    EndSetUpdate endSetUpdate = new EndSetUpdate(game.getSet(), nextPlayerOrderAndScore);
                    notifyAllClients(endSetUpdate, "", MessageType.END_SET);

                    notifyDistributedCards();

                    String nextPlayerNickName = game.getTableCard().getPlayerListOrder().get(0).getNickname();
                    PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(PlayerState.BET, nextPlayerNickName);
                    notifyAllClients(playerStateUpdate, nextPlayerNickName, MessageType.PLAYER_STATE_UPDATE);
                }else{
                    //FINE ROUND
                    //aggiorno playerList per il prossimo round (devo cambiare l'ordine di gioco dei player)
                    game.updateRound();
                    game.updateNumTurn();
                    //game.resetNumTurn();
                    game.getTableCard().resetPlayedCard();
                    game.getTableCard().updatePlayerListOrder(winnerTurnPlayer);
                    game.getTableCard().getPlayerListOrder().get(0).updateState(PlayerState.PUT);
                    //notifica client fine round con vincitore round (chi ha fatto la presa), nuovo playerListOrder e nuovo state per next player
                    HashMap<String, Integer> nextPlayerOrderAndTaken = new HashMap<>();
                    for(GamePlayer p : game.getTableCard().getPlayerListOrder()){
                        nextPlayerOrderAndTaken.put(p.getNickname(), p.getRoundsWon());
                    }
                    EndRoundUpdate endRoundUpdate = new EndRoundUpdate(game.getRound(), nextPlayerOrderAndTaken);
                    notifyAllClients(endRoundUpdate, "", MessageType.END_ROUND);

                    String nextPlayerNickName = game.getTableCard().getPlayerListOrder().get(0).getNickname();
                    PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(PlayerState.PUT, nextPlayerNickName);
                    notifyAllClients(playerStateUpdate, nextPlayerNickName, MessageType.PLAYER_STATE_UPDATE);
                }

            }else if(game.getNumTurn() < NUM_PLAYER - 1){
                //turni da 0 a 3 per scommettere sulle prese (entro qui quando turno appena passato è 0,1,2)
                game.updateNumTurn();
                GamePlayer nextPlayer = game.getTableCard().getPlayerListOrder().get(game.getNumTurn());
                nextPlayer.updateState(PlayerState.BET);
                // notifica client prossimo player a scommettere
                PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(PlayerState.BET, nextPlayer.getNickname());
                notifyAllClients(playerStateUpdate, nextPlayer.getNickname(), MessageType.PLAYER_STATE_UPDATE);
            }else{
                //turni da 4 a 7 per giocare (entro qui quando turno appena passato è 3,4,5,6)
                game.updateNumTurn();
                GamePlayer nextPlayer = game.getTableCard().getPlayerListOrder().get((game.getNumTurn() - NUM_PLAYER) % NUM_PLAYER);
                nextPlayer.updateState(PlayerState.PUT);
                // notifica client prossimo player a giocare
                PlayerStateUpdate playerStateUpdate = new PlayerStateUpdate(PlayerState.PUT, nextPlayer.getNickname());
                notifyAllClients(playerStateUpdate, nextPlayer.getNickname(), MessageType.PLAYER_STATE_UPDATE);
            }

        } catch (PlayerNickNameDoesNotExist e) {
            System.out.println("Player not found");
        }
    }

    private void endGameResult(){
        List<GamePlayer> gameResults = game.endGame();

        HashMap<String, Integer> resultAndScore = new HashMap<>();
        for(GamePlayer p : gameResults){
            if(p.getPlayerState() != PlayerState.EXIT)
                resultAndScore.put(p.getNickname(), p.getScore());
            else
                resultAndScore.put(p.getNickname(), -500); //punteggio fittizzio per giocatore uscito dal game
        }

        EndGame endGameUpdate = new EndGame(resultAndScore);
        notifyAllClients(endGameUpdate, null, MessageType.END_GAME);

        //notificare MasterController della partita finita per poterla rimuovere dalla lista partite attive e per poter aggiornare le statistiche dei player nel database
        gameLifeCycleListener.onGameEnded(this);
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
        return game.getTableCard().getPlayerListOrder().get(winnerIndex).getNickname();
    }

    private boolean checkIfValidBet(int bet, String nickname) {
        int totalBet = bet;
        if(bet > game.getSet() || bet < 0) {
            return false;
        }
        //entro se sta scommettendo l'ultimo player
        if(game.getTableCard().getPlayerListOrder().get(game.getPlayers().size()-1).getNickname().equals(nickname)){
            //scorro tutti i player fino all'ultimo che scommette (anche l'ultimo tanto avrà bet = 0 dato che la sta impostando ora)
            for(GamePlayer p : game.getPlayers()) {
                totalBet = totalBet + p.getBet();
            }
            if(totalBet == game.getSet()) {
                return false;
            }
        }
        return true;
    }

    private boolean checkIfValidCard(Card card, GamePlayer player) {
        if(game.getTableCard().getPlayedCards().isEmpty()){
            if(game.getSet() == 10)
                //se è la prima carta del set 10 allora diventa la briscola per questo round
                game.getTableCard().setBriscola(card);
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
            if (card1.getValueForComparison() > card2.getValueForComparison()) {
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
        for(GamePlayer p : game.getPlayers()) {
            p.updateScore();
        }
    }

    private void resetBetAndRoundsWon() {
        for(GamePlayer p : game.getPlayers()) {
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