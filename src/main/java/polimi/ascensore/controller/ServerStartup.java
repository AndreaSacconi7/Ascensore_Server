package polimi.ascensore.controller;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import polimi.ascensore.network.websocket.GameWebSocketHandler;
import polimi.ascensore.persistence.SupabaseTables;

import java.util.concurrent.CompletableFuture;

/**
 * Runs once the application is wired and before the server accepts connections: secures the database
 * tables and brings back the matches that were in progress when the server last stopped.
 */
@Component
public class ServerStartup implements SmartInitializingSingleton {

    private final MasterController masterController;

    private final GameLoops loops;

    private final SupabaseTables tables;

    // Constructed first, so the master controller can reach the players' sockets
    public ServerStartup(MasterController masterController, GameLoops loops, SupabaseTables tables,
                         GameWebSocketHandler sockets) {
        this.masterController = masterController;
        this.loops = loops;
        this.tables = tables;
    }

    @Override
    public void afterSingletonsInstantiated() {
        tables.lockDown();
        CompletableFuture.runAsync(masterController::restoreMatches, loops.lobby()).join();
    }
}
