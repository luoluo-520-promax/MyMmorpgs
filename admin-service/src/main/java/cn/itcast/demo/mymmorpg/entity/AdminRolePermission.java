/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/entity/AdminRolePermission.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/entity
 * 3) 主要职责：JPA 映射 admin_role_permission 关联表，连接 AdminRole 与 AdminPermission（RBAC 角色-权限）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.entity; // player-service JPA 实体，映射 GM 后台 RBAC 或游戏表

import jakarta.persistence.Column; // JPA 映射注解与生命周期回调
import jakarta.persistence.Entity; // JPA 映射注解与生命周期回调
import jakarta.persistence.GeneratedValue; // JPA 映射注解与生命周期回调
import jakarta.persistence.GenerationType; // JPA 映射注解与生命周期回调
import jakarta.persistence.Id; // JPA 映射注解与生命周期回调
import jakarta.persistence.Table; // JPA 映射注解与生命周期回调
/**
 * 角色-权限关联：AdminRolePermissionRepository.findByRoleIdIn 批量查多角色下的 permissionId。
 */

@Entity // JPA 实体映射数据库表

@Table(name = "admin_role_permission") // 指定表名与列约束

public class AdminRolePermission { // AdminRolePermission 类型定义
    @Id // 主键列
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键策略
    private Long id; // 关联行自增主键，无业务含义
    @Column(name = "role_id", nullable = false) // 列名/nullable/length 约束
    private Long roleId; // 指向 admin_role.id，角色侧外键
    @Column(name = "permission_id", nullable = false) // 列名/nullable/length 约束
    private Long permissionId; // 指向 admin_permission.id，权限侧外键
    public Long getId() { // 读取 Id（Id）
        return id; // 关联表行标识
    } // getId 方法体结束

    public void setId(Long id) { // 写入 Id（Id）
        this.id = id; // 数据迁移指定主键
    } // setId 方法体结束

    public Long getRoleId() { // 读取 RoleId（RoleId）
        return roleId; // AdminRolePermissionRepository.findByRoleIdIn 匹配键
    } // getRoleId 方法体结束

    public void setRoleId(Long roleId) { // 写入 RoleId（RoleId）
        this.roleId = roleId; // 为角色授予权限时写入
    } // setRoleId 方法体结束

    public Long getPermissionId() { // 读取 PermissionId（PermissionId）
        return permissionId; // AdminPermissionRepository.findAllById 解析为 code
    } // getPermissionId 方法体结束

    public void setPermissionId(Long permissionId) { // 写入 PermissionId（PermissionId）
        this.permissionId = permissionId; // 绑定 complaint:handle 等权限到角色
    } // setPermissionId 方法体结束
} // AdminRolePermission 类体结束
