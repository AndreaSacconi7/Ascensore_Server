package polimi.ascensore.controller;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where the server's work runs.
 * <ul>
 *   <li>The <b>lobby</b> loop owns sessions, matchmaking and which match each player is in.</li>
 *   <li>Each started match has its own <b>match loop</b>: matches run in parallel, and a busy one does not
 *   slow down the others.</li>
 *   <li>The <b>database</b> thread does the blocking work of logging in (token check, player lookup,
 *   nickname claims) so that no loop ever waits on the network. Being a single thread, it also makes
 *   checking and claiming a nickname atomic.</li>
 *   <li>The <b>timers</b> thread only enqueues expired deadlines on the loop that owns them.</li>
 * </ul>
 */
@Component
public class GameLoops {

    private static final Logger log = LoggerFactory.getLogger(GameLoops.class);

    private final Executor workers;

    private final Executor database;

    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(daemon("timers"));

    private final SerialLoop lobby;

    @Autowired
    public GameLoops() {
        this(Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), daemon("match")),
                Executors.newSingleThreadExecutor(daemon("database")));
    }

    private GameLoops(Executor workers, Executor database) {
        this.workers = workers;
        this.database = database;
        this.lobby = new SerialLoop(workers);
    }

    /**
     * Everything runs on the calling thread, except timers: for tests that call the controllers directly
     * and check the result right after.
     */
    public static GameLoops direct() {
        return new GameLoops(Runnable::run, Runnable::run);
    }

    public Executor lobby() {
        return lobby;
    }

    public SerialLoop newMatchLoop() {
        return new SerialLoop(workers);
    }

    public Executor database() {
        return database;
    }

    /**
     * Runs {@code action} on {@code loop} after {@code delay}.
     *
     * @return a handle that cancels the action if it has not been enqueued yet
     */
    public Runnable schedule(Duration delay, Executor loop, Runnable action) {
        ScheduledFuture<?> timer = timers.schedule(() -> loop.execute(action), delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> timer.cancel(false);
    }

    /**
     * Stops taking work and lets the tasks already queued finish, so that the last moves reach the match
     * snapshots before the server exits.
     */
    @PreDestroy
    public void shutdown() {
        timers.shutdownNow();
        for (Executor executor : new Executor[]{workers, database}) {
            if (executor instanceof ExecutorService service) {
                service.shutdown();
                try {
                    if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("Some tasks were still running at shutdown");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static ThreadFactory daemon(String name) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
