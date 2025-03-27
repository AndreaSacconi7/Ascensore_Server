package polimi.ascensore.network.client;

import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.CommandType;
import polimi.ascensore.network.command.ConnectionRequest;
import polimi.ascensore.network.message.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.LinkedList;

public class SocketClient {

    private final long SLEEP_INTERVALL=15000;

    private long lastPing=0;

    private final Object LockPing;
    /**
     * This attribute is responsible for managing incoming messages from the server
     */
    final ObjectInputStream input;
    //si occupa di gestire i messaggi in uscita da client verso server
    /**
     * This attribute is responsible for managing outgoing messages from client to server
     */
    final ObjectOutputStream output;

    private final LinkedList<Message> messages = new LinkedList<>();

    public SocketClient(String ipAddress, int port) throws IOException {
        LockPing = new Object();
        Socket serverSocket = new Socket(ipAddress, port);
        this.output = new ObjectOutputStream(serverSocket.getOutputStream());
        this.input = new ObjectInputStream(serverSocket.getInputStream());

        System.out.println("Client connesso....");
        new Thread(this::getMessage).start();
    }

    private void getMessage() {

        Message message;
        while (true){
            synchronized (messages){
                message=null;
                if (messages.isEmpty()){
                    try {
                        messages.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else {
                    message=messages.poll();
                }
            }
            if(message!=null) {
                //view.messageToView(message);
                System.out.println("MESSAGGIO LETTOOOOO");
            }
        }
    }

    /**
     * This method starts a new thread that reads updates from the server.
     */
    public void run() throws RemoteException {
        // nuovo Thread che leggerà gli aggiornamenti dal server
        new Thread(() -> {
            try {
                readMessage();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    //funzione in cui avviene effettivamente la lettura lato client del messaggio che arriva dal server
    //se il messaggio è un ping, invia un pong

    /**
     * In this method the client-side reading of the message arriving from the server actually takes place
     * if the message is a ping, send a pong
     */
    private void readMessage()  {

        Message message=null;
        try {
            while (true) {
                message = (Message) input.readObject();

                synchronized (messages){
                    messages.add(message);
                    messages.notifyAll();
                }
            }
        }catch (IOException | ClassNotFoundException e){
            System.out.println("Errore di comunicazione");
        }
    }


//invia un comando al server, si sincronizza sull'output per evitare conflitti con il thread del ping

    /**
     * This method sends a command to the server, synchronizes on the output
     * to avoid conflicts with the ping thread
     * @param command the command to be sent.
     */
    public void sendCommand(Command command) {
        try {
            synchronized (output) {
                output.writeObject(command);
            }
        } catch (IOException e) {
            System.out.println("Errore di comunicazione");
        }

    }

    public void connect(String nickname) {
        //creo un nuovo comando di connessione
        Command command = new Command(new ConnectionRequest(nickname), CommandType.CONNECTION_COMMAND);
        sendCommand(command);
    }


}