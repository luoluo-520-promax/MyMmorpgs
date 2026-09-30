/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/AdminPermissionService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：后台 RBAC 权限服务，用户→角色→权限链路计算并校验权限标识。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 后台 RBAC 五表链路：admin_user→role→permission

import cn.itcast.demo.mymmorpg.entity.AdminPermission; // admin_permission 表实体，含 code 权限标识
import cn.itcast.demo.mymmorpg.repository.AdminPermissionRepository; // 按 permIds 批量查 admin_permission
import cn.itcast.demo.mymmorpg.repository.AdminRolePermissionRepository; // admin_role_permission 角色-权限关联
import cn.itcast.demo.mymmorpg.repository.AdminUserRepository; // 查 admin_user 是否存在且 enabled
import cn.itcast.demo.mymmorpg.repository.AdminUserRoleRepository; // admin_user_role 用户-角色关联
import org.springframework.stereotype.Service; // AdminComplaintService 等后台服务调用 hasPermission
import org.springframework.transaction.annotation.Transactional; // 只读事务包裹多表 RBAC 查询

import java.util.HashSet; // 收集去重后的 admin_permission.code 集合
import java.util.List; // 承接 roleIds / permIds 中间列表
import java.util.Set; // 对外暴露权限码集合
import java.util.stream.Collectors; // stream 提取 roleId / permissionId

/**
 * 基于 RBAC 的后台权限服务。
 *
 * 五张表：admin_user、admin_role、admin_permission、admin_user_role、admin_role_permission。
 * 通过 用户→角色→权限 链路计算权限标识，提供统一 hasPermission 入口。
 */
@Service // 后台各业务服务在操作前调用本类校验 admin_permission.code
public class AdminPermissionService { // admin_user→admin_user_role→admin_role_permission→admin_permission 链路

    /** admin_user 表仓储，校验用户存在且 enabled=true */
    private final AdminUserRepository adminUserRepository; // findById 查 admin_user.enabled

    /** admin_user_role 表仓储，按 userId 查角色 ID 列表 */
    private final AdminUserRoleRepository adminUserRoleRepository; // findByUserId → admin_role.id 列表

    /** admin_role_permission 表仓储，按 roleIds 查权限 ID 列表 */
    private final AdminRolePermissionRepository adminRolePermissionRepository; // findByRoleIdIn → permissionId

    /** admin_permission 表仓储，按 permIds 查权限 code */
    private final AdminPermissionRepository adminPermissionRepository; // findAllById → admin_permission.code

    public AdminPermissionService(AdminUserRepository adminUserRepository, // 查 admin_user 存在与 enabled 状态
                                  AdminUserRoleRepository adminUserRoleRepository, // admin_user_role 用户→角色
                                  AdminRolePermissionRepository adminRolePermissionRepository, // admin_role_permission 角色→权限
                                  AdminPermissionRepository adminPermissionRepository) { // admin_permission.code 如 complaint:handle
        this.adminUserRepository = adminUserRepository; // RBAC 链路起点：admin_user
        this.adminUserRoleRepository = adminUserRoleRepository; // 用户关联的 admin_role.id 列表
        this.adminRolePermissionRepository = adminRolePermissionRepository; // 角色拥有的 admin_permission.id
        this.adminPermissionRepository = adminPermissionRepository; // 权限码如 complaint:handle
    }

    /**
     * 计算指定后台用户拥有的全部权限标识（permission.code）。
     */
    @Transactional(readOnly = true) // 只读事务，多表 RBAC 查询一致性
    public Set<String> listPermissionsByUserId(Long userId) { // 计算指定后台用户拥有的全部权限标识（permission.code）
        if (userId == null) { // 未传 adminUserId
            return Set.of(); // 视为无任何后台权限
        }
        var userOpt = adminUserRepository.findById(userId); // SELECT admin_user WHERE id=?
        if (userOpt.isEmpty() || Boolean.FALSE.equals(userOpt.get().getEnabled())) { // admin_user 不存在或 enabled=false
            return Set.of(); // 拒绝全部后台操作
        }
        var userRoles = adminUserRoleRepository.findByUserId(userId); // SELECT admin_user_role WHERE user_id=?
        if (userRoles.isEmpty()) { // admin_user 未分配 admin_role
            return Set.of(); // 无任何 admin_permission.code
        }
        List<Long> roleIds = userRoles.stream() // 查 admin_user_role 得角色 ID 列表
                .map(r -> r.getRoleId()) // 提取 admin_role.id
                .collect(Collectors.toList()); // 收集为 List 供 IN 查询
        var rolePerms = adminRolePermissionRepository.findByRoleIdIn(roleIds); // SELECT admin_role_permission WHERE role_id IN (?)
        if (rolePerms.isEmpty()) { // admin_role 未配置 permission
            return Set.of(); // 空权限集
        }
        List<Long> permIds = rolePerms.stream() // 查 admin_role_permission 得权限 ID 列表
                .map(p -> p.getPermissionId()) // 提取 admin_permission.id
                .distinct() // 多角色可能重复授予同一 permission，去重
                .collect(Collectors.toList()); // 批量查 admin_permission 定义
        var perms = adminPermissionRepository.findAllById(permIds); // SELECT admin_permission WHERE id IN (?)
        Set<String> codes = new HashSet<>(); // 最终权限码集合，如 complaint:handle
        for (AdminPermission p : perms) { // 遍历 admin_permission 实体
            if (p.getCode() != null && !p.getCode().isEmpty()) { // 跳过空 admin_permission.code
                codes.add(p.getCode()); // 收集非空 permission.code
            }
        }
        return codes; // 该 admin_user 可用的全部 RBAC 权限标识
    }

    /**
     * 检查用户是否拥有指定权限标识；后台操作入口应调用此方法，false 则拒绝访问。
     * 拥有 {@code *}（SUPERADMIN 预设）时视为全部放行。
     */
    @Transactional(readOnly = true) // 只读事务包裹 RBAC 链路查询
    public boolean hasPermission(Long userId, String permissionCode) { // 检查用户是否拥有指定权限标识；后台操作入口应调用此方法，false 则拒绝访问
        if (permissionCode == null || permissionCode.isEmpty()) { // 空 admin_permission.code 无法匹配
            return false; // 直接拒绝后台操作
        }
        Set<String> perms = listPermissionsByUserId(userId); // 计算 admin_user 全部 permission.code
        if (perms.contains("*")) { // SUPERADMIN 通配
            return true;
        }
        return perms.contains(permissionCode); // 是否包含目标权限如 complaint:handle
    }
}
