package polimi.ascensore.persistence;

import jakarta.persistence.*;

/**
 * A registered player. The account itself lives in Supabase Auth; this row links it to a public nickname.
 */
@Entity
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Subject of the player's Supabase JWT
    @Column(unique = true, nullable = false)
    private String supabaseUid;

    private String nickname;

    // Required by JPA
    public Player() {
    }

    public Player(String supabaseUid, String nickname) {
        this.supabaseUid = supabaseUid;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public String getSupabaseUid() {
        return supabaseUid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
