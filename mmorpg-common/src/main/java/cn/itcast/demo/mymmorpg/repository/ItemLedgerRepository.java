package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.ItemLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemLedgerRepository extends JpaRepository<ItemLedger, Long> {
    Optional<ItemLedger> findByIdempotencyKey(String idempotencyKey);
}
