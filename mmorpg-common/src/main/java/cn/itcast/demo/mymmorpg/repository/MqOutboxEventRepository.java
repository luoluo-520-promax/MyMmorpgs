package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.MqOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MqOutboxEventRepository extends JpaRepository<MqOutboxEvent, Long> {

    @Query("select e from MqOutboxEvent e where e.status = :status and e.nextRetryAt <= :now order by e.id asc")
    List<MqOutboxEvent> findReady(@Param("status") String status, @Param("now") long now, Pageable pageable);

    long countByStatus(String status);
}
