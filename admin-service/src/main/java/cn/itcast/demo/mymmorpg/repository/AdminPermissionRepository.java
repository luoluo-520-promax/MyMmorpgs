/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/repository/AdminPermissionRepository.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/repository
 * 3) 主要职责：admin_permission 表 JPA 仓储，定义可授权操作点 code。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.repository; // player-service Spring Data JPA 仓储接口

import cn.itcast.demo.mymmorpg.entity.AdminPermission; // JPA 实体引用
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPA Repository 基类
import java.util.Optional; // Optional，AdminPermissionRepository.java 编译依赖
/**
 * 后台权限数据访问：AdminPermissionService 将 permissionId 解析为 code 供接口鉴权。
 */

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> { // AdminPermissionRepository 接口定义
    /**
     * RBAC 查询：权限种子数据与菜单渲染——按 code（如 complaint:handle、item:manage）查 admin_permission 行，
     * DevDataLoader 幂等插入及后台权限树展示时使用。
     */

    Optional<AdminPermission> findByCode(String code); // AdminPermissionRepository 逻辑
} // AdminPermissionRepository 类体结束
