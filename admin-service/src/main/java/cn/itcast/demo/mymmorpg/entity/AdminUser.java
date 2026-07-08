/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/entity/AdminUser.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/entity
 * 3) 主要职责：JPA 映射 admin_user 表，存储 GM 后台登录账号（与游戏 Account 分离）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.entity; // player-service JPA 实体，映射 GM 后台 RBAC 或游戏表

import jakarta.persistence.*; // JPA 映射注解与生命周期回调
import java.time.LocalDateTime; // GM 账号 create_time 等时间戳字段
/**
 * 后台管理用户实体：AdminPermissionService 登录校验与 RBAC 角色加载的数据源。
 */

@Entity // JPA 实体映射数据库表

@Table(name = "admin_user") // 指定表名与列约束

public class AdminUser { // AdminUser 类型定义
    @Id // 主键列
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键策略
    private Long id; // admin_user 主键，admin_user_role.user_id 外键
    @Column(name = "username", nullable = false, unique = true, length = 64) // 列名/nullable/length 约束
    private String username; // GM 登录名，全局唯一，AdminUserRepository.findByUsername 查账凭证
    @Column(name = "password", nullable = false, length = 128) // 列名/nullable/length 约束
    private String password; // BCrypt 哈希，明文仅在校验瞬间存在，不落库
    @Column(name = "enabled", nullable = false) // 列名/nullable/length 约束
    private Boolean enabled = true; // enabled=true 账号可登录后台；false 禁用（等同 status=0），保留行供审计
    @Column(name = "create_time", nullable = false) // 列名/nullable/length 约束
    private LocalDateTime createTime; // 账号创建时间，运营审计与生命周期追踪
    /** 首次 insert 前填充 createTime 与 enabled 默认值 */

    @PrePersist // insert 前填充默认值
    void prePersist() { // JPA insert 前填充 createTime/enabled 默认值
        if (createTime == null) { // AdminUser.if：createTime == null
            createTime = LocalDateTime.now(); // 新 GM 账号写入当前时间戳
        } // if 方法体结束
        if (enabled == null) { // AdminUser.if：enabled == null
            enabled = true; // 未显式指定时默认启用，避免误建禁用账号
        } // if 方法体结束
    } // prePersist 方法体结束

    public Long getId() { // 读取 Id（Id）
        return id; // RBAC 链路起点：admin_user.id
    } // getId 方法体结束

    public void setId(Long id) { // 写入 Id（Id）
        this.id = id; // DevDataLoader 种子数据指定主键时使用
    } // setId 方法体结束

    public String getUsername() { // 读取 Username（Username）
        return username; // 后台登录表单提交的用户名
    } // getUsername 方法体结束

    public void setUsername(String username) { // 写入 Username（Username）
        this.username = username; // 创建 GM 账号时设置登录名
    } // setUsername 方法体结束

    public String getPassword() { // 读取 Password（Password）
        return password; // PasswordEncoder.matches 比对用哈希
    } // getPassword 方法体结束

    public void setPassword(String password) { // 写入 Password（Password）
        this.password = password; // 注册或重置密码时写入新 BCrypt 哈希
    } // setPassword 方法体结束

    public Boolean getEnabled() { // 读取 Enabled（Enabled）
        return enabled; // AdminPermissionService 校验 false 则拒绝全部后台操作
    } // getEnabled 方法体结束

    public void setEnabled(Boolean enabled) { // 写入 Enabled（Enabled）
        this.enabled = enabled; // GM 封禁/解封账号时更新
    } // setEnabled 方法体结束

    public LocalDateTime getCreateTime() { // 读取 CreateTime（CreateTime）
        return createTime; // 运营后台展示账号开通时间
    } // getCreateTime 方法体结束

    public void setCreateTime(LocalDateTime createTime) { // 写入 CreateTime（CreateTime）
        this.createTime = createTime; // 数据迁移回填历史创建时间
    } // setCreateTime 方法体结束
} // AdminUser 类体结束
