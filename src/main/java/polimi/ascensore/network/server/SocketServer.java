package polimi.ascensore.network.server;

import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.CommandType;
import polimi.ascensore.network.command.ConnectionRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer {

    private final MasterServer masterServer;
    private final ServerSocket listenSocket;
    public static final int SOCKET_SERVER_PORT = 8080;

    public SocketServer(MasterServer masterServer, int port) throws IOException {
        ServerSocket listenSocket = new ServerSocket(port);
        this.masterServer= masterServer;
        this.listenSocket = listenSocket;
    }

    public void runServer()  {
        //istanziamo un nuovo server socket per la comunicazione
        new Thread(() -> {
            Socket clientSocket = null;
            try {
                //ogni volta che un client si connette al server
                //accept() attende una nuova connessione come se fosse uno Scanner!! (stoppa la computazione e aspetta che gli arrivi qualcosa)
                //(se non arriva nulla rimane in attesa e non fa procedere la computazione)
                while ((clientSocket = this.listenSocket.accept()) != null) {
                    ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

                    //creo un nuovo thread per gestire gli input dal client appena connesso
                    new Thread(()->{
                        listenClientCommand(input,output);
                    }).start();
                    System.out.println("New Client is connected using Socket Server!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    //creato in un thread per ogni giocatore, si mette in ascolto di messaggi dal client e li aggiunge alla lista di comandi

    /**
     * This method listens for commands from the client and adds them to the list of commands
     * @param input the command received from the client
     * @param output the message sent to the client
     */
    public void listenClientCommand(ObjectInputStream input, ObjectOutputStream output){
        boolean connected = true;
        while (connected) {
            Command command=null;
            try {
                //readObject legge l'oggetto inviato dal client come se fosse uno Scanner!! (stoppa la computazione e aspetta che gli arrivi qualcosa)
                //(se non arriva nulla rimane in attesa e non fa procedere la computazione)
                command = (Command) input.readObject();

                if (command.getType().equals(CommandType.CONNECTION_COMMAND)) {
                    ((ConnectionRequest) command.getExecutable()).setOutput(output);
                    //TODO: se la richiesta di una nuova connessione arriva quando non ci sono giocatori disconnessi allora chiudo il thread in ascolto
                }
                masterServer.addCommandToList(command);

            }catch (IOException | ClassNotFoundException e) {
                //se il gioco non riceve più correttamente notifiche, disconnetto il giocatore
                connected = false;
                System.out.println("Il socket server chiude il Thread di ascolto per il client");
            }
        }
    }
}

