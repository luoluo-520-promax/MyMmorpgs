package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.PlayerQuestProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerQuestProgressRepository extends JpaRepository<PlayerQuestProgress, PlayerQuestProgress.Pk> {

    List<PlayerQuestProgress> findByPlayerId(Long playerId);

    Optional<PlayerQuestProgress> findByPlayerIdAndQuestId(Long playerId, Integer questId);
}
