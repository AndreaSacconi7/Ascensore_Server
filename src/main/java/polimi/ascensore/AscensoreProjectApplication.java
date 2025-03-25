package polimi.ascensore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import polimi.ascensore.network.MasterServer;
import polimi.ascensore.network.RestServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;

@SpringBootApplication
public class AscensoreProjectApplication {

    public static void main(String[] args) {

        ApplicationContext context = SpringApplication.run(AscensoreProjectApplication.class, args);

        Controller controller = context.getBean(Controller.class);
        MasterServer masterServer = new MasterServer(controller);
        new RestServer(masterServer);
    }
}