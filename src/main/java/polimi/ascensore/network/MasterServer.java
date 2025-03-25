package polimi.ascensore.network;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import polimi.ascensore.Card;
import polimi.ascensore.Controller;

@Component
public class MasterServer {

    private final Controller controller;

    public MasterServer(Controller controller) {
        this.controller = controller;
    }

    public void putCard(int indexHand, String nickName) {
        controller.putCard(indexHand, nickName);
    }

    public void setBet(int bet, String nickName) {
        controller.setBet(bet, nickName);
    }
}
