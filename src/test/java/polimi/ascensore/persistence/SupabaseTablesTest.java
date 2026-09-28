package polimi.ascensore.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class SupabaseTablesTest {

    private JdbcTemplate jdbc;
    private SupabaseTables tables;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbc.queryForList(contains("pg_roles"), eq(String.class))).thenReturn(List.of("anon", "authenticated"));
        tables = new SupabaseTables(jdbc);
    }

    @Test
    void rowSecurityIsTurnedOnAndTheClientRolesLoseTheirPrivileges() {
        rowSecurity("player", false);
        rowSecurity("match_snapshot", false);

        tables.lockDown();

        verify(jdbc).execute("alter table public.player enable row level security");
        verify(jdbc).execute("alter table public.match_snapshot enable row level security");
        verify(jdbc).execute("revoke all on public.player from anon, authenticated");
        verify(jdbc).execute("revoke all on public.match_snapshot from anon, authenticated");
    }

    @Test
    void tablesAlreadySecuredAreLeftAsTheyAre() {
        rowSecurity("player", true);
        rowSecurity("match_snapshot", true);

        tables.lockDown();

        verify(jdbc, never()).execute(startsWith("alter table"));
    }

    @Test
    void theServerDoesNotStartWithATableEveryoneCanRead() {
        rowSecurity("player", false);
        rowSecurity("match_snapshot", false);
        doThrow(new PermissionDeniedDataAccessException("must be owner of table player", null))
                .when(jdbc).execute("alter table public.player enable row level security");

        assertThrows(IllegalStateException.class, tables::lockDown);
    }

    @Test
    void outsidePostgresNothingIsTouched() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("H2");

        tables.lockDown();
        tables.enforceUniqueNicknames();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void duplicateNicknamesDelayTheUniqueIndexWithoutStoppingTheServer() {
        doThrow(new DataIntegrityViolationException("could not create unique index"))
                .when(jdbc).execute(startsWith("create unique index"));

        assertDoesNotThrow(tables::enforceUniqueNicknames);
    }

    private void rowSecurity(String table, boolean on) {
        when(jdbc.queryForList(contains("relrowsecurity"), eq(Boolean.class), eq(table))).thenReturn(List.of(on));
    }
}
