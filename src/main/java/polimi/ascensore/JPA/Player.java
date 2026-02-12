package polimi.ascensore.JPA;


import jakarta.persistence.*;


@Entity
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Questo lo gestisce il DB, NON metterlo nel costruttore!

    @Column(unique = true, nullable = false)
    private String supabaseUid; // Il "ponte"

    private String nickname;

    // Statistiche di gioco (con valori di default)
    //private int level = 1;
    //private int gold = 100;

    // --- COSTRUTTORE 1: VUOTO (Obbligatorio per JPA) ---
    // Hibernate usa questo per leggere dal database. Se non c'è, il codice esplode.
    public Player() {
    }

    // --- COSTRUTTORE 2: LOGICO (Per creare nuovi utenti) ---
    // Usalo nel tuo MasterServer quando arriva un nuovo giocatore
    public Player(String supabaseUid, String nickname) {
        this.supabaseUid = supabaseUid;
        this.nickname = nickname;
    }

    // --- GETTER E SETTER ---
    public Long getId() {
        return id;
    }

    public String getSupabaseUid() {
        return supabaseUid;
    }

    public String getNickname() {
        return nickname;
    }
}