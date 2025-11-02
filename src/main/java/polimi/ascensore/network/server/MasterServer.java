package polimi.ascensore.network.server;

import org.springframework.stereotype.Service;
import polimi.ascensore.controller.GameController;
import polimi.ascensore.controller.MasterController;
import polimi.ascensore.model.Seed;
import polimi.ascensore.network.command.Command;
import polimi.ascensore.network.command.ExecutableInServer;
import polimi.ascensore.network.newserver.MySocketHandler;

import java.time.LocalTime;
import java.util.LinkedList;
import java.util.Queue;

@Service
public class MasterServer {

    private final MasterController masterController;

    //private final GameController gameController;

    private final Object lockCommand = new Object();

    private Queue<Command> commandList;

    //TODO: conviene far si che questa rimanga come unico server che smista i comandi ai vari game controller. e in ognuno di essi pongo una coda che gli esegue
    public MasterServer(MasterController masterController) {
        System.out.println("MasterServer created");
        //this.gameController = gameController;
        this.masterController = masterController;
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
        masterController.putCard(seed, value, nickName);
    }

    public void setBet(int bet, String nickName) {
        masterController.setBet(bet, nickName);
    }

    public void executeExecutable(ExecutableInServer executable) {
        System.out.println("ESEGUO EXECUTABLE INVIATO DAL CLIENT");
        if (executable != null) {
            executable.execute(this);
        }
    }

    public void addClient(String nickName, String password, String clientSessionId) {

        //controller.addClientHandler(clientHandler);
        masterController.loginPlayer(nickName, clientSessionId);
    }

    public void setSocketHandler(MySocketHandler socketHandler) {
        masterController.setSocketHandler(socketHandler);
    }

    public void fetchPlayerInfo(String token) {
        masterController.fetchPlayerInfo(token);
    }

    public void joinGame(String nickname) {
        masterController.addPlayerToGame(nickname);
    }
}
