package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.PlayerSkinOwned;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerSkinOwnedRepository extends JpaRepository<PlayerSkinOwned, PlayerSkinOwned.Pk> {

    List<PlayerSkinOwned> findByPlayerId(long playerId);

    boolean existsByPlayerIdAndSkinId(long playerId, int skinId);
}
