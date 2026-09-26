package polimi.ascensore.controller;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single thread that owns all game state.
 * <p>
 * Client commands, closed connections and expired reconnection timers are all submitted here and run
 * one at a time, in arrival order, so lobby, matches and sessions are never mutated by two threads at once.
 */
@Component
public class CommandLoop {

    private static final Logger log = LoggerFactory.getLogger(CommandLoop.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "command-loop");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Queues a task. A task that throws is logged and skipped: one malformed command must not stop
     * every match on the server.
     */
    public void submit(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Command failed and was skipped", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
