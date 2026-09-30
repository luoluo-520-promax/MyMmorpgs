package cn.itcast.demo.mymmorpg.repository;

import cn.itcast.demo.mymmorpg.entity.SessionBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SessionBackupRepository extends JpaRepository<SessionBackup, Long> {

    @Query("select s from SessionBackup s where s.expireAt is null or s.expireAt > ?1")
    List<SessionBackup> findActive(long nowMillis);
}
