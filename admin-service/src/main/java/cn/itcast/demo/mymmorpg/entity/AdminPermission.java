/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/entity/AdminPermission.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/entity
 * 3) 主要职责：JPA 映射 admin_permission 表，定义可授权操作点 code（RBAC 权限层）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.entity; // player-service JPA 实体，映射 GM 后台 RBAC 或游戏表

import jakarta.persistence.*; // JPA 映射注解与生命周期回调
/**
 * 权限实体：AdminPermissionService 聚合用户全部 role 后得到 permission code 集合，供接口鉴权匹配。
 */

@Entity // JPA 实体映射数据库表

@Table(name = "admin_permission") // 指定表名与列约束

public class AdminPermission { // AdminPermission 类型定义
    @Id // 主键列
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键策略
    private Long id; // admin_permission 主键，admin_role_permission.permission_id 外键
    /** 权限标识，如 complaint:handle、item:manage，全局唯一 */

    @Column(name = "code", nullable = false, unique = true, length = 128) // 列名/nullable/length 约束
    private String code; // hasPermission 最终比对的字符串，如 complaint:handle
    /** 人类可读描述，后台权限树与审计日志展示 */

    @Column(name = "description", nullable = false, length = 255) // 列名/nullable/length 约束
    private String description; // 运营后台权限配置页展示说明
    public Long getId() { // 读取 Id（Id）
        return id; // admin_role_permission 关联用 permissionId
    } // getId 方法体结束

    public void setId(Long id) { // 写入 Id（Id）
        this.id = id; // 种子数据指定权限主键
    } // setId 方法体结束

    public String getCode() { // 读取 Code（Code）
        return code; // AdminComplaintService 校验 complaint:handle 时使用
    } // getCode 方法体结束

    public void setCode(String code) { // 写入 Code（Code）
        this.code = code; // 新增后台操作点时注册权限码
    } // setCode 方法体结束

    public String getDescription() { // 读取 Description（Description）
        return description; // GM 权限分配界面展示「处理玩家投诉」
    } // getDescription 方法体结束

    public void setDescription(String description) { // 写入 Description（Description）
        this.description = description; // 修改权限说明文案
    } // setDescription 方法体结束
} // AdminPermission 类体结束
