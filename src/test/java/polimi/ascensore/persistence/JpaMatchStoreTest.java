package polimi.ascensore.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import polimi.ascensore.model.Card;
import polimi.ascensore.model.MatchState;
import polimi.ascensore.model.PlayerState;
import polimi.ascensore.model.Seed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Against the real schema, in an in-memory database (the WebSocket config needs a servlet container)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:snapshots",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "supabase.jwks-url=http://localhost:1/jwks"
        })
class JpaMatchStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Autowired
    private MatchSnapshotRepository repository;

    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void emptyTable() {
        repository.deleteAll();
    }

    @Test
    void savedMatchComesBackAfterARestart() {
        JpaMatchStore store = store(NOW);
        store.save(match("m-1", 3));
        store.flush();

        List<MatchState> loaded = store(NOW.plusSeconds(30)).loadAll();

        assertEquals(1, loaded.size());
        MatchState state = loaded.get(0);
        assertEquals(3, state.setIndex());
        assertEquals(List.of("alice", "bob"), state.playOrder());
        assertEquals(Seed.SWORDS, state.briscola().getSeed());
        assertEquals(PlayerState.BET, state.seats().get(0).state());
        assertEquals(2, state.seats().get(0).hand().size());
    }

    @Test
    void onlyTheLatestStateOfAMatchIsKept() {
        JpaMatchStore store = store(NOW);
        store.save(match("m-1", 1));
        store.save(match("m-1", 2));
        store.save(match("m-1", 5));
        store.flush();

        assertEquals(1, repository.count());
        assertEquals(5, store(NOW).loadAll().get(0).setIndex());
    }

    @Test
    void anEndedMatchIsGone() {
        JpaMatchStore store = store(NOW);
        store.save(match("m-1", 1));
        store.save(match("m-2", 1));
        store.delete("m-1");
        store.flush();

        assertEquals(List.of("m-2"), store(NOW).loadAll().stream().map(MatchState::id).toList());
    }

    @Test
    void snapshotsFromLongAgoAreDiscarded() {
        JpaMatchStore store = store(NOW);
        store.save(match("m-1", 1));
        store.flush();

        assertTrue(store(NOW.plus(Duration.ofHours(3))).loadAll().isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    void anUnreadableSnapshotIsDiscarded() {
        repository.save(new MatchSnapshot("m-broken", "{not json", NOW));

        assertTrue(store(NOW).loadAll().isEmpty());
        assertEquals(0, repository.count());
    }

    private JpaMatchStore store(Instant now) {
        return new JpaMatchStore(repository, transaction, Duration.ofHours(2), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static MatchState match(String id, int setIndex) {
        List<Card> hand = List.of(new Card(Seed.CUPS, 1), new Card(Seed.COINS, 7));
        return new MatchState(MatchState.VERSION, id, 2, 10, setIndex, 0, 0,
                List.of(new MatchState.Seat("uid-alice", "alice", 12, 0, 0, hand, PlayerState.BET),
                        new MatchState.Seat("uid-bob", "bob", -3, 0, 0, hand, PlayerState.WAIT)),
                List.of(), List.of("alice", "bob"), List.of(), new Card(Seed.SWORDS, 3), Map.of("bob", 1));
    }
}
