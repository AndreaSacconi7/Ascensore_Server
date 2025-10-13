package polimi.ascensore.network.server;

import org.springframework.stereotype.Service;
import polimi.ascensore.controller.Controller;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.ExecutableInServer;
import polimi.ascensore.network.newserver.MySocketHandler;

import java.time.LocalTime;
import java.util.LinkedList;
import java.util.Queue;

@Service
public class MasterServer {

    private final Controller controller;

    private final Object lockCommand = new Object();

    private Queue<Command> commandList;

    public MasterServer(Controller controller) {
        System.out.println("MasterServer created");
        this.controller = controller;
        this.commandList = new LinkedList<>();

        new Thread(() -> {
            ExecutableInServer executable;
            while (true) {
                synchronized (lockCommand) {
                    if (commandList.isEmpty()){
                        executable = null;
                        try {
                            System.out.println("MasterServer: waiting" + LocalTime.now());
                            lockCommand.wait();                            //eseguo l'istruzione
                            System.out.println("MasterServer: woke up" + LocalTime.now());
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }else {
                        executable = commandList.poll().getExecutable();

                    }
                }
                executeExecutable(executable);
            }
        }).start();
    }

    public void addCommandToList(Command command) {

        System.out.println("Aggiungo command a lista");
        synchronized (lockCommand) {
            commandList.add(command);
            lockCommand.notifyAll();
        }
    }

    public void putCard(Seed seed, int value, String nickName) {
        controller.putCard(seed, value, nickName);
    }

    public void setBet(int bet, String nickName) {
        controller.setBet(bet, nickName);
    }

    public void executeExecutable(ExecutableInServer executable) {
        System.out.println("ESEGUO EXECUTABLE INVIATO DAL CLIENT");
        if (executable != null) {
            executable.execute(this);
        }
    }

    public void addClient(String nickName, String password, String clientSessionId) {

        //controller.addClientHandler(clientHandler);
        controller.addPlayer(nickName, clientSessionId);
    }

    public void setSocketHandler(MySocketHandler socketHandler) {
        controller.setSocketHandler(socketHandler);
    }
}
