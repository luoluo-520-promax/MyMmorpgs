package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.PlayerMail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerMailRepository extends JpaRepository<PlayerMail, Long> {

    List<PlayerMail> findByPlayerIdOrderByIdAsc(Long playerId);

    long countByPlayerId(Long playerId);

    Optional<PlayerMail> findByIdAndPlayerId(Long id, Long playerId);
}
