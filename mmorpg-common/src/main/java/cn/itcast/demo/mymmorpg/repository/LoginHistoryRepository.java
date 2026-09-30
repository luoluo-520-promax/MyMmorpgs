package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findTop20ByAccountIdAndSuccessOrderByCreatedAtDesc(Long accountId, boolean success);
}
