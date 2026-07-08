/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/repository/AdminUserRepository.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/repository
 * 3) 主要职责：admin_user 表 JPA 仓储，GM 后台登录时按 username 查 AdminUser。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.repository; // player-service Spring Data JPA 仓储接口

import cn.itcast.demo.mymmorpg.entity.AdminUser; // JPA 实体引用
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPA Repository 基类
import java.util.Optional; // Optional，AdminUserRepository.java 编译依赖
/**
 * 后台管理员用户数据访问：AdminPermissionService RBAC 链路第一步。
 */

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> { // AdminUserRepository 接口定义
    /**
     * RBAC 查询：GM 后台登录鉴权——按登录名加载 admin_user 行，校验 enabled 与密码哈希。
     * AdminPermissionService.listPermissionsByUserId 亦通过 findById 校验用户存在且 enabled=true。
     */

    Optional<AdminUser> findByUsername(String username); // AdminUserRepository 逻辑
} // AdminUserRepository 类体结束
