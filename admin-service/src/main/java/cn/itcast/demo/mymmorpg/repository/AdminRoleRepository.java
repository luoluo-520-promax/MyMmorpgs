/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/repository/AdminRoleRepository.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/repository
 * 3) 主要职责：admin_role 表 JPA 仓储，按 code 查角色定义（SUPER_ADMIN 等）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.repository; // player-service Spring Data JPA 仓储接口

import cn.itcast.demo.mymmorpg.entity.AdminRole; // JPA 实体引用
import org.springframework.data.jpa.repository.JpaRepository; // Spring Data JPA Repository 基类
import java.util.Optional; // Optional，AdminRoleRepository.java 编译依赖
/**
 * 后台角色数据访问：DevDataLoader 种子数据与 RBAC 角色定义维护。
 */

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> { // AdminRoleRepository 接口定义
    /**
     * RBAC 查询：角色种子数据幂等插入——按 code（如 SUPER_ADMIN、OPERATOR）查 admin_role 行，
     * 获取 roleId 供 admin_user_role / admin_role_permission 关联写入。
     */

    Optional<AdminRole> findByCode(String code); // AdminRoleRepository 逻辑
} // AdminRoleRepository 类体结束
