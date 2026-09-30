package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.GrantIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrantIdempotencyRepository extends JpaRepository<GrantIdempotency, Long> {
    Optional<GrantIdempotency> findByPlayerIdAndIdempotencyKey(long playerId, String idempotencyKey);

    boolean existsByPlayerIdAndIdempotencyKey(long playerId, String idempotencyKey);
}
