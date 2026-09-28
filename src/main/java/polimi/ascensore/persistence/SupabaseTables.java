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
     * Turns Row Level Security on for the server's tables, and takes away the client roles' privileges on
     * them; safe to repeat at every start.
     *
     * @throws IllegalStateException if a table is left readable with the public key: the server refuses to
     *                               start rather than publish the players' hands
     */
    public void lockDown() {
        if (!isPostgres()) {
            return;
        }
        // Roles Supabase gives to requests made with the public key, signed in or not (absent outside Supabase)
        List<String> clientRoles = jdbc.queryForList(
                "select rolname from pg_roles where rolname in ('anon', 'authenticated')", String.class);
        for (String table : TABLES) {
            // Only the owner may turn it on, even when it is on already: a table secured from the dashboard is fine
            if (!rowSecurityOn(table)) {
                try {
                    jdbc.execute("alter table public." + table + " enable row level security");
                } catch (DataAccessException e) {
                    throw new IllegalStateException("Row Level Security is off on public." + table
                            + " and could not be turned on, so anyone with the public key could read it."
                            + " Turn it on from the Supabase dashboard, or connect as the table's owner", e);
                }
            }
            // Row Level Security already hides every row; without privileges the tables are not even reachable
            if (!clientRoles.isEmpty()) {
                try {
                    jdbc.execute("revoke all on public." + table + " from " + String.join(", ", clientRoles));
                } catch (DataAccessException e) {
                    log.warn("Could not revoke the client roles' privileges on {}: {}", table, e.getMessage());
                }
            }
        }
    }

    /**
     * Makes nicknames unique in the database too, ignoring case, like the login check. While two accounts
     * still share one (rows from before that check) the index cannot be built: the login makes them choose
     * again, and a later start builds it.
     */
    public void enforceUniqueNicknames() {
        if (!isPostgres()) {
            return;
        }
        try {
            jdbc.execute("create unique index if not exists player_nickname_lower_key on public.player (lower(nickname))");
        } catch (DataAccessException e) {
            log.warn("No unique index on nicknames yet (two accounts share one, or the server is not the table's"
                    + " owner): {}", e.getMostSpecificCause().getMessage());
        }
    }

    private boolean rowSecurityOn(String table) {
        List<Boolean> on = jdbc.queryForList("select c.relrowsecurity from pg_class c"
                + " join pg_namespace n on n.oid = c.relnamespace"
                + " where n.nspname = 'public' and c.relname = ?", Boolean.class, table);
        return !on.isEmpty() && Boolean.TRUE.equals(on.get(0));
    }

    private boolean isPostgres() {
        String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        return "PostgreSQL".equals(product);
    }
}
