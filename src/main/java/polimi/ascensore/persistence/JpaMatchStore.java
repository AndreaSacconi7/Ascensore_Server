package polimi.ascensore.persistence;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import polimi.ascensore.controller.MatchStore;
import polimi.ascensore.model.MatchState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps match snapshots in the {@code match_snapshot} table.
 * <p>
 * Matches call {@link #save} after every move, from their own loops; a single writer thread does the
 * database work. Writes are coalesced: if a match moves again before its previous state was written, only
 * the latest state is written. If the database is unreachable, the pending writes are retried a few seconds
 * later, and the matches carry on in memory meanwhile.
 */
@Component
public class JpaMatchStore implements MatchStore {

    private static final Logger log = LoggerFactory.getLogger(JpaMatchStore.class);

    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final MatchSnapshotRepository repository;

    private final TransactionTemplate transaction;

    private final Duration maxAge;

    private final Clock clock;

    private final Gson gson = new Gson();

    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "match-snapshots");
        thread.setDaemon(true);
        return thread;
    });

    // Match id -> latest state still to write, or empty to delete it. Guarded by this.
    private final Map<String, Optional<MatchState>> pending = new LinkedHashMap<>();

    // True while a write is queued on the writer. Guarded by this.
    private boolean scheduled;

    @Autowired
    public JpaMatchStore(MatchSnapshotRepository repository, TransactionTemplate transaction,
                         @Value("${ascensore.snapshot-max-age-minutes:120}") long maxAgeMinutes) {
        this(repository, transaction, Duration.ofMinutes(maxAgeMinutes), Clock.systemUTC());
    }

    JpaMatchStore(MatchSnapshotRepository repository, TransactionTemplate transaction, Duration maxAge, Clock clock) {
        this.repository = repository;
        this.transaction = transaction;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    @Override
    public void save(MatchState state) {
        enqueue(state.id(), Optional.of(state));
    }

    @Override
    public void delete(String matchId) {
        enqueue(matchId, Optional.empty());
    }

    private synchronized void enqueue(String matchId, Optional<MatchState> state) {
        pending.put(matchId, state);
        if (!scheduled && !writer.isShutdown()) {
            try {
                writer.execute(this::write);
                scheduled = true;
            } catch (RejectedExecutionException e) {
                // Shutting down: flush() writes what is pending
            }
        }
    }

    /**
     * Snapshots of the matches in progress. Snapshots older than the maximum age belong to matches nobody
     * will come back to (the server was down for hours): they are deleted instead of restored.
     */
    @Override
    public List<MatchState> loadAll() {
        Instant oldest = clock.instant().minus(maxAge);
        List<MatchState> states = new ArrayList<>();
        for (MatchSnapshot snapshot : repository.findAll()) {
            MatchState state = parse(snapshot);
            if (state == null || snapshot.getUpdatedAt().isBefore(oldest)) {
                log.info("Discarding old or unreadable snapshot {}", snapshot.getId());
                repository.deleteById(snapshot.getId());
            } else {
                states.add(state);
            }
        }
        return states;
    }

    private MatchState parse(MatchSnapshot snapshot) {
        try {
            MatchState state = gson.fromJson(snapshot.getState(), MatchState.class);
            return state != null && state.version() == MatchState.VERSION ? state : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void flush() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Snapshot writer still busy at shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Whatever was saved after the writer stopped
        write();
    }

    // On the writer thread (or at shutdown)
    private void write() {
        Map<String, Optional<MatchState>> batch;
        synchronized (this) {
            batch = new LinkedHashMap<>(pending);
            pending.clear();
            scheduled = false;
        }
        if (batch.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        try {
            transaction.executeWithoutResult(status -> batch.forEach((matchId, state) -> {
                if (state.isPresent()) {
                    repository.save(new MatchSnapshot(matchId, gson.toJson(state.get()), now));
                } else if (repository.existsById(matchId)) {
                    repository.deleteById(matchId);
                }
            }));
        } catch (RuntimeException e) {
            log.warn("Could not write {} match snapshots, retrying in {}s: {}", batch.size(),
                    RETRY_DELAY.toSeconds(), e.getMessage());
            retry(batch);
        }
    }

    private synchronized void retry(Map<String, Optional<MatchState>> batch) {
        // A match that moved on since then has a newer state pending: that one wins
        batch.forEach(pending::putIfAbsent);
        if (!scheduled && !writer.isShutdown()) {
            scheduled = true;
            writer.schedule(this::write, RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
