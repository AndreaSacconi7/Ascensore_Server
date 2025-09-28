package polimi.ascensore.network.command;

public enum CommandType {

    //comando di login inviato dal client per l'autenticazione e aggiungersi alla partita
    LOGIN_COMMAND,
    //comando di ping inviato dal client per verificare la connessione con il server
    PING_COMMAND,
    //comando inviato dal client per posizionare una carta sul tavolo
    PUT_CARD,
    //comando inviato dal client per chiamare le prese che vuole fare
    SET_BET
}
