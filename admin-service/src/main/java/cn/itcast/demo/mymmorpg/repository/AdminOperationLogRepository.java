package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.AdminOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {
}
