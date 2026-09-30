package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.ActivitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivitySnapshotRepository extends JpaRepository<ActivitySnapshot, Long> {

    List<ActivitySnapshot> findByPlayerIdAndVersionCodeOrderByCreatedAtMsDesc(long playerId, String versionCode);

    Optional<ActivitySnapshot> findFirstByPlayerIdAndVersionCodeAndSnapshotTypeOrderByCreatedAtMsDesc(
            long playerId, String versionCode, String snapshotType);
}
