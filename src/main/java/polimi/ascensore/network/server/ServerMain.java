package polimi.ascensore.network.server;

import polimi.ascensore.controller.Controller;

import java.io.IOException;

public class ServerMain {

    public static void main(String[] args) {

        Controller controller;

        controller = new Controller();

        MasterServer masterServer = new MasterServer(controller);

        //uso come porta il primo argomento della run configuration
        int socketPort = Integer.parseInt(args[0]);

        try {

            new SocketServer(masterServer, socketPort).runServer();

        } catch (IOException e) {
            System.out.println("Error in Socket server creation");
        }

    }
}
