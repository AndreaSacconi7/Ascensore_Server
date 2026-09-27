package polimi.ascensore.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * The saved state of a match in progress, as JSON (see {@link polimi.ascensore.model.MatchState}).
 * The row is deleted when the match ends.
 */
@Entity
public class MatchSnapshot {

    @Id
    private String id;

    // A four-player match with full hands is a few KB
    @Column(nullable = false, length = 20_000)
    private String state;

    @Column(nullable = false)
    private Instant updatedAt;

    // Required by JPA
    protected MatchSnapshot() {
    }

    public MatchSnapshot(String id, String state, Instant updatedAt) {
        this.id = id;
        this.state = state;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
