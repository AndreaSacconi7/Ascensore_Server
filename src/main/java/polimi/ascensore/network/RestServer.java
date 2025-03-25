package polimi.ascensore.network;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ascensore")
public class RestServer {

    private final MasterServer masterServer;

    public RestServer(MasterServer masterServer) {
        this.masterServer = masterServer;
    }

    @GetMapping("/putCard")
    public void putCard(int indexHand, String nickName) {
        masterServer.putCard(indexHand, nickName);
    }

    @GetMapping("/setBet")
    public void setBet(int bet, String nickName) {
        masterServer.setBet(bet, nickName);
    }
}
