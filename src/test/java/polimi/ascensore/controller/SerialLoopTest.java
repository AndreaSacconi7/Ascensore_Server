package polimi.ascensore.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialLoopTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void failingTaskDoesNotStopTheLoop() throws InterruptedException {
        SerialLoop loop = new SerialLoop(pool);
        CountDownLatch nextTaskRan = new CountDownLatch(1);

        loop.execute(() -> { throw new NullPointerException("malformed command"); });
        loop.execute(nextTaskRan::countDown);

        assertTrue(nextTaskRan.await(2, TimeUnit.SECONDS), "loop died after a failing task");
    }

    @Test
    void tasksRunInSubmissionOrderOneAtATime() throws InterruptedException {
        SerialLoop loop = new SerialLoop(pool);
        List<Integer> order = new CopyOnWriteArrayList<>();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(500);

        for (int i = 0; i < 500; i++) {
            int n = i;
            loop.execute(() -> {
                if (running.incrementAndGet() > 1) {
                    overlaps.incrementAndGet();
                }
                order.add(n);
                running.decrementAndGet();
                done.countDown();
            });
        }

        assertTrue(done.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < 500; i++) {
            assertEquals(i, order.get(i));
        }
        assertEquals(0, overlaps.get());
    }

    @Test
    void aBlockedLoopDoesNotHoldUpTheOthers() throws InterruptedException {
        SerialLoop slowMatch = new SerialLoop(pool);
        SerialLoop otherMatch = new SerialLoop(pool);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherRan = new CountDownLatch(1);

        slowMatch.execute(() -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        otherMatch.execute(otherRan::countDown);

        assertTrue(otherRan.await(2, TimeUnit.SECONDS), "one slow match stalled another");
        release.countDown();
    }

    @Test
    void onTheCallingThreadATaskQueuedFromATaskRunsAfterIt() {
        SerialLoop loop = new SerialLoop(Runnable::run);
        List<String> order = new ArrayList<>();

        loop.execute(() -> {
            loop.execute(() -> order.add("second"));
            order.add("first");
        });

        assertEquals(List.of("first", "second"), order);
    }
}
