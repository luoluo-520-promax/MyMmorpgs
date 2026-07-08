/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/repository/AdminRolePermissionRepository.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/repository
 * 3) 主要职责：admin_role_permission 关联表仓储，批量查多角色下的 permissionId。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.repository; // player-service Spring Data JPA 仓储接口

import cn.itcast.demo.mymmorpg.entity.AdminRolePermission; // JPA 实体引用
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPA Repository 基类
import java.util.List; // 敏感词表或 YAML 解析结果列表
/**
 * 角色-权限关联：RBAC 第三层，AdminPermissionService 合并用户所有角色的权限。
 */

public interface AdminRolePermissionRepository extends JpaRepository<AdminRolePermission, Long> { // AdminRolePermissionRepository 接口定义
    /**
     * RBAC 查询：角色→权限批量展开——IN 查询用户全部 roleId 对应的 admin_role_permission 行，
     * AdminPermissionService 一次取出 permissionId 避免 N+1，再解析为 complaint:handle 等权限码。
     */

    List<AdminRolePermission> findByRoleIdIn(Iterable<Long> roleIds); // AdminRolePermissionRepository 逻辑
} // AdminRolePermissionRepository 类体结束
