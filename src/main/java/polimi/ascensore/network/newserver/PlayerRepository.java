package polimi.ascensore.network.newserver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import polimi.ascensore.JPA.Player;

import java.util.Optional;

//DAO per accedere ai dati dei giocatori nel database
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    // Trova il player basandosi sull'ID strano di Supabase (es. "a1b2-c3d4...")
    Optional<Player> findBySupabaseUid(String supabaseUid);

}
