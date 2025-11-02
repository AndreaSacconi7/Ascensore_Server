package polimi.ascensore.network.command;

public enum CommandType {

    //RICORDARSI DI AGGIUNGERE IL COMMANDTYPE IN COMMAND DESERIALIZER PER FARLO FUNZIONARE

    //comando di login inviato dal client per l'autenticazione e aggiungersi alla partita
    LOGIN_COMMAND,
    //comando di richiesta informazioni del giocatore
    PLAYER_INFO_REQUEST,
    //comando di richiesta di unirsi ad una partita
    JOIN_GAME_REQUEST,
    //comando di ping inviato dal client per verificare la connessione con il server
    PING_COMMAND,
    //comando inviato dal client per posizionare una carta sul tavolo
    PUT_CARD,
    //comando inviato dal client per chiamare le prese che vuole fare
    SET_BET
}
