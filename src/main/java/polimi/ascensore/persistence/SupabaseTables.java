package polimi.ascensore.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Supabase serves every table of the {@code public} schema through its REST API to anyone holding the
 * project's public key, which ships inside the client. Row Level Security without policies closes that
 * door: nobody could otherwise read the hands saved in the match snapshots. The server connects as the
 * tables' owner and is not affected.
 */
@Component
public class SupabaseTables {

    private static final Logger log = LoggerFactory.getLogger(SupabaseTables.class);

    private static final List<String> TABLES = List.of("player", "match_snapshot");

    private final JdbcTemplate jdbc;

    public SupabaseTables(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Turns Row Level Security on for the server's tables; safe to repeat at every start.
     */
    public void lockDown() {
        if (!isPostgres()) {
            return;
        }
        for (String table : TABLES) {
            try {
                jdbc.execute("alter table public." + table + " enable row level security");
            } catch (DataAccessException e) {
                log.warn("Could not enable Row Level Security on {}: {}", table, e.getMessage());
            }
        }
    }

    private boolean isPostgres() {
        String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        return "PostgreSQL".equals(product);
    }
}
