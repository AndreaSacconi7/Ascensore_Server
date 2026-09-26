package polimi.ascensore.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLoopTest {

    private final CommandLoop loop = new CommandLoop();

    @AfterEach
    void shutdown() {
        loop.shutdown();
    }

    @Test
    void failingTaskDoesNotStopTheLoop() throws InterruptedException {
        CountDownLatch nextTaskRan = new CountDownLatch(1);

        loop.submit(() -> { throw new NullPointerException("malformed command"); });
        loop.submit(nextTaskRan::countDown);

        assertTrue(nextTaskRan.await(2, TimeUnit.SECONDS), "loop died after a failing task");
    }

    @Test
    void tasksRunInSubmissionOrderOnASingleThread() throws InterruptedException {
        List<Integer> order = new CopyOnWriteArrayList<>();
        List<String> threads = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            int n = i;
            loop.submit(() -> {
                order.add(n);
                threads.add(Thread.currentThread().getName());
                done.countDown();
            });
        }

        assertTrue(done.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < 100; i++) {
            assertEquals(i, order.get(i));
        }
        assertEquals(1, threads.stream().distinct().count());
    }
}
