/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/repository/AdminUserRoleRepository.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/repository
 * 3) 主要职责：admin_user_role 关联表仓储，查某管理员拥有的全部 roleId。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.repository; // player-service Spring Data JPA 仓储接口

import cn.itcast.demo.mymmorpg.entity.AdminUserRole; // JPA 实体引用
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPA Repository 基类
import java.util.List; // 敏感词表或 YAML 解析结果列表
/**
 * 管理员用户-角色关联：RBAC 第二层，连接 AdminUser 与 AdminRole。
 */

public interface AdminUserRoleRepository extends JpaRepository<AdminUserRole, Long> { // AdminUserRoleRepository 接口定义
    /**
     * RBAC 查询：用户→角色展开——按 admin_user.id 查全部 admin_user_role 行，
     * AdminPermissionService.listPermissionsByUserId 收集 roleId 列表供下一步权限展开。
     */

    List<AdminUserRole> findByUserId(Long userId); // AdminUserRoleRepository 逻辑
} // AdminUserRoleRepository 类体结束
