package polimi.ascensore.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs its tasks one at a time, in submission order, on threads borrowed from a shared pool.
 * <p>
 * Each match has one, and so does the lobby: the state a loop owns is only ever touched by one task at a
 * time, while different loops run in parallel. A slow task delays its own loop, never the others.
 */
public final class SerialLoop implements Executor {

    private static final Logger log = LoggerFactory.getLogger(SerialLoop.class);

    // Tasks run in one go before the loop hands its thread back, so a busy loop cannot starve the others
    private static final int BATCH = 64;

    private final Executor pool;

    private final Queue<Runnable> tasks = new ArrayDeque<>();

    // True while a drain is queued on the pool or running
    private boolean draining;

    public SerialLoop(Executor pool) {
        this.pool = pool;
    }

    /**
     * Queues a task. A task that throws is logged and skipped: one malformed command must not stop the loop.
     */
    @Override
    public void execute(Runnable task) {
        synchronized (this) {
            tasks.add(task);
            if (draining) {
                return;
            }
            draining = true;
        }
        schedule();
    }

    private void schedule() {
        try {
            pool.execute(this::drain);
        } catch (RejectedExecutionException e) {
            // The server is shutting down
            synchronized (this) {
                tasks.clear();
                draining = false;
            }
        }
    }

    private void drain() {
        for (int i = 0; i < BATCH; i++) {
            Runnable task;
            synchronized (this) {
                task = tasks.poll();
                if (task == null) {
                    draining = false;
                    return;
                }
            }
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Task failed and was skipped", e);
            }
        }
        // More tasks are waiting: go back in the pool's queue behind the other loops
        schedule();
    }
}
