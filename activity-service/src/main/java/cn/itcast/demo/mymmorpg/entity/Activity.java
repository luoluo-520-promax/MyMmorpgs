/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/entity/Activity.java
 * 2) 所属模块：activity-service / entity（实体定义在 mmorpg-common，此处为服务侧引用）
 * 3) 主要职责：映射 MySQL activity 表，存储活动类型、开关状态与 JSON 配置
 * 4) 系统位置：JPA 持久层实体，被 ActivityRepository 与 ActivityService 使用
 * 5) 变更建议：表结构变更时同步更新列注解与 DDL
 */
package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column; // 列映射注解
import jakarta.persistence.Entity; // 实体标记
import jakarta.persistence.GeneratedValue; // 主键生成策略
import jakarta.persistence.GenerationType; // 主键生成类型枚举
import jakarta.persistence.Id; // 主键标记
import jakarta.persistence.Table; // 表名映射

import java.time.LocalDateTime; // 数据库时间戳类型

/**
 * 活动实例实体，对应 MySQL activity 表一行记录。
 */
@Entity // 标记为 JPA 实体
@Table(name = "activity") // 映射到 activity 表
public class Activity { // 活动持久化对象

    @Id // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键
    private Long id; // 活动实例 ID

    @Column(name = "type", nullable = false) // 活动类型列，非空
    private Integer type; // 活动类型编码（见 ActivityTypes）

    @Column(name = "opened", nullable = false) // 开关列，非空
    private Boolean opened = false; // 是否对玩家可见/开启，默认关闭

    @Column(name = "data", columnDefinition = "TEXT") // JSON 配置列，TEXT 类型
    private String data; // 活动配置 JSON（反序列化为 ActivityConfigPayload）

    @Column(name = "create_time", insertable = false, updatable = false) // 创建时间，由数据库维护
    private LocalDateTime createTime; // 记录创建时间

    @Column(name = "update_time", insertable = false, updatable = false) // 更新时间，由数据库维护
    private LocalDateTime updateTime; // 记录最后更新时间

    /**
     * 获取活动 ID。
     *
     * @return 活动实例 ID
     */
    public Long getId() {
        return id; // 返回主键
    }

    /**
     * 设置活动 ID。
     *
     * @param id 活动实例 ID
     */
    public void setId(Long id) {
        this.id = id; // 写入主键
    }

    /**
     * 获取活动类型。
     *
     * @return 活动类型编码
     */
    public Integer getType() {
        return type; // 返回类型
    }

    /**
     * 设置活动类型。
     *
     * @param type 活动类型编码
     */
    public void setType(Integer type) {
        this.type = type; // 写入类型
    }

    /**
     * 获取活动是否开启。
     *
     * @return true 表示已开启
     */
    public Boolean getOpened() {
        return opened; // 返回开关状态
    }

    /**
     * 设置活动是否开启。
     *
     * @param opened 开关状态
     */
    public void setOpened(Boolean opened) {
        this.opened = opened; // 写入开关状态
    }

    /**
     * 获取活动配置 JSON。
     *
     * @return data 列原始 JSON 字符串
     */
    public String getData() {
        return data; // 返回配置 JSON
    }

    /**
     * 设置活动配置 JSON。
     *
     * @param data 配置 JSON 字符串
     */
    public void setData(String data) {
        this.data = data; // 写入配置 JSON
    }

    /**
     * 获取创建时间。
     *
     * @return 数据库 create_time
     */
    public LocalDateTime getCreateTime() {
        return createTime; // 返回创建时间
    }

    /**
     * 获取更新时间。
     *
     * @return 数据库 update_time
     */
    public LocalDateTime getUpdateTime() {
        return updateTime; // 返回更新时间
    }
}
