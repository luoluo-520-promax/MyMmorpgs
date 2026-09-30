package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.PlayerFriend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerFriendRepository extends JpaRepository<PlayerFriend, Long> {

    List<PlayerFriend> findByPlayerId(Long playerId);

    boolean existsByPlayerIdAndFriendId(Long playerId, Long friendId);

    Optional<PlayerFriend> findByPlayerIdAndFriendId(Long playerId, Long friendId);

    void deleteByPlayerIdAndFriendId(Long playerId, Long friendId);
}
