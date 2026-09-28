package polimi.ascensore.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import polimi.ascensore.persistence.Player;

import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findBySupabaseUid(String supabaseUid);

    boolean existsByNicknameIgnoreCase(String nickname);

    long countByNicknameIgnoreCase(String nickname);
}
