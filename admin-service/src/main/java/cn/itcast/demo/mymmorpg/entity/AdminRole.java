/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/entity/AdminRole.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/entity
 * 3) 主要职责：JPA 映射 admin_role 表，定义 GM 角色编码与展示名（RBAC 角色层）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.entity; // player-service JPA 实体，映射 GM 后台 RBAC 或游戏表

import jakarta.persistence.*; // JPA 映射注解与生命周期回调
/**
 * 角色实体：通过 admin_user_role、admin_role_permission 与用户、权限多对多关联。
 */

@Entity // JPA 实体映射数据库表

@Table(name = "admin_role") // 指定表名与列约束

public class AdminRole { // AdminRole 类型定义
    @Id // 主键列
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键策略
    private Long id; // admin_role 主键，admin_user_role.role_id / admin_role_permission.role_id 外键
    /** 角色编码，如 SUPER_ADMIN、OPERATOR，AdminRoleRepository.findByCode 用于初始化脚本 */

    @Column(name = "code", nullable = false, unique = true, length = 64) // 列名/nullable/length 约束
    private String code; // 程序内 RBAC 匹配键，如 SUPER_ADMIN 拥有全部权限
    /** 展示名称，如「客服」「物品管理员」，后台 UI 与日志展示 */

    @Column(name = "name", nullable = false, length = 64) // 列名/nullable/length 约束
    private String name; // 运营后台角色下拉框展示文案
    public Long getId() { // 读取 Id（Id）
        return id; // admin_user_role / admin_role_permission 关联用 roleId
    } // getId 方法体结束

    public void setId(Long id) { // 写入 Id（Id）
        this.id = id; // 种子数据指定角色主键
    } // setId 方法体结束

    public String getCode() { // 读取 Code（Code）
        return code; // DevDataLoader 按 code 幂等插入角色
    } // getCode 方法体结束

    public void setCode(String code) { // 写入 Code（Code）
        this.code = code; // 新建角色时设置唯一编码
    } // setCode 方法体结束

    public String getName() { // 读取 Name（Name）
        return name; // 审计日志展示「操作人角色：客服」
    } // getName 方法体结束

    public void setName(String name) { // 写入 Name（Name）
        this.name = name; // 后台角色管理修改展示名
    } // setName 方法体结束
} // AdminRole 类体结束
