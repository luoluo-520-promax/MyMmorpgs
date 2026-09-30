package cn.itcast.demo.mymmorpg.repository; // 更新服务版本发布仓库，用于查询和维护版本清单记录

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease; // 版本发布实体，作为仓库操作对象
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPA 基础仓库接口

import java.util.Optional; // 用于表达版本记录可能不存在

/**
 * 版本发布记录的 JPA 仓库
 */
public interface ClientVersionReleaseRepository extends JpaRepository<ClientVersionRelease, Long> {

    Optional<ClientVersionRelease> findFirstByActiveTrueOrderByVersionNumberDesc(); // 查询当前 active 的最新版本，用于客户端拉取清单

    Optional<ClientVersionRelease> findByVersionCode(String versionCode); // 按版本字符串查找记录，用于导入时幂等 upsert
}
