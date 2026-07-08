/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/entity/AdminUserRole.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/entity
 * 3) 主要职责：JPA 映射 admin_user_role 关联表，连接 AdminUser 与 AdminRole（RBAC 用户-角色）。
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
 * 用户-角色关联：一管理员可绑定多角色，AdminUserRoleRepository.findByUserId 查 roleId 列表。
 */

@Entity // JPA 实体映射数据库表

@Table(name = "admin_user_role") // 指定表名与列约束

public class AdminUserRole { // AdminUserRole 类型定义
    @Id // 主键列
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键策略
    private Long id; // 关联行自增主键，无业务含义
    @Column(name = "user_id", nullable = false) // 列名/nullable/length 约束
    private Long userId; // 指向 admin_user.id，GM 账号
    @Column(name = "role_id", nullable = false) // 列名/nullable/length 约束
    private Long roleId; // 指向 admin_role.id，如 SUPER_ADMIN / OPERATOR
    public Long getId() { // 读取 Id（Id）
        return id; // 关联表行标识
    } // getId 方法体结束

    public void setId(Long id) { // 写入 Id（Id）
        this.id = id; // 数据迁移指定主键
    } // setId 方法体结束

    public Long getUserId() { // 读取 UserId（UserId）
        return userId; // AdminUserRoleRepository.findByUserId 查询键
    } // getUserId 方法体结束

    public void setUserId(Long userId) { // 写入 UserId（UserId）
        this.userId = userId; // 为 GM 分配角色时写入
    } // setUserId 方法体结束

    public Long getRoleId() { // 读取 RoleId（RoleId）
        return roleId; // 下一步 AdminRolePermissionRepository.findByRoleIdIn 入参来源
    } // getRoleId 方法体结束

    public void setRoleId(Long roleId) { // 写入 RoleId（RoleId）
        this.roleId = roleId; // 绑定 GM 到指定角色
    } // setRoleId 方法体结束
} // AdminUserRole 类体结束
