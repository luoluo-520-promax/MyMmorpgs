package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {
    Optional<WalletLedger> findByIdempotencyKey(String idempotencyKey);
}
