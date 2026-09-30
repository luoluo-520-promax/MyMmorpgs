package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.ShopOrderRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopOrderRecordRepository extends JpaRepository<ShopOrderRecord, String> {
    List<ShopOrderRecord> findByStatusAndPaidAtBetween(String status, long paidAtFrom, long paidAtTo);

    long countByStatus(String status);
}
