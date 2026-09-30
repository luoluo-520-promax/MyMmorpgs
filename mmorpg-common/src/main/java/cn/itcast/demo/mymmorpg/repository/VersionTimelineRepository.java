package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.VersionTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VersionTimelineRepository extends JpaRepository<VersionTimeline, Long> {

    @Query("""
            SELECT v FROM VersionTimeline v
            WHERE v.enabled = true
              AND v.effectiveAtMs >= :fromMs
              AND v.effectiveAtMs <= :toMs
            ORDER BY v.effectiveAtMs ASC
            """)
    List<VersionTimeline> findUpcoming(
            @Param("fromMs") long fromMs,
            @Param("toMs") long toMs);

    List<VersionTimeline> findByEnabledTrueAndExecutedPublishFalseAndEffectiveAtMsLessThanEqual(long nowMs);
}
