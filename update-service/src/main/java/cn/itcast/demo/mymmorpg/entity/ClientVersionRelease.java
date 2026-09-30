package cn.itcast.demo.mymmorpg.entity; // 更新服务版本发布实体，映射客户端版本清单表

import jakarta.persistence.Column; // JPA 列映射注解，用于定义字段与数据库列的对应关系
import jakarta.persistence.Entity; // 声明这是一个 JPA 实体
import jakarta.persistence.GeneratedValue; // 自增主键生成策略注解
import jakarta.persistence.GenerationType; // 主键生成策略枚举
import jakarta.persistence.Id; // 主键标记注解
import jakarta.persistence.Lob; // 长文本字段注解，用于存储完整 manifest JSON
import jakarta.persistence.Table; // 指定数据库表名

import java.time.LocalDateTime; // 记录创建和更新时间的时间类型

/**
 * 客户端版本发布记录，manifest 列存完整 JSON 清单。
 */
@Entity // 该类对应数据库中的客户端版本发布记录
@Table(name = "client_version_release") // 显式映射到 client_version_release 表
public class ClientVersionRelease { // 更新服务持久化的版本清单实体

    @Id // 主键字段，标识唯一的版本发布记录
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 使用数据库自增主键策略
    private Long id; // 版本发布记录主键

    @Column(name = "version_code", nullable = false, unique = true, length = 32) // 版本字符串必须唯一，供导入和查询使用
    private String versionCode; // 版本代码，如 1.0.0

    @Column(name = "version_number", nullable = false) // 数值版本用于大小比较和排序
    private Long versionNumber; // 数值版本号，如 10000

    @Column(name = "min_client_version_number", nullable = false) // 小于该值时客户端必须强更
    private Long minClientVersionNumber; // 最小兼容客户端版本号

    @Column(name = "active", nullable = false) // active=true 表示当前可供客户端查询的有效版本
    private Boolean active = true; // 默认写入时立即生效，方便导入后直接可用

    @Lob // manifest 可能较长，需要以大字段形式存储完整 JSON
    @Column(name = "manifest", nullable = false, columnDefinition = "TEXT") // 以 TEXT 保存完整清单，避免长度不足
    private String manifest; // 原始版本清单 JSON 字符串

    @Column(name = "create_time", insertable = false, updatable = false) // 由数据库默认值维护创建时间
    private LocalDateTime createTime; // 记录版本发布的创建时间

    @Column(name = "update_time", insertable = false, updatable = false) // 由数据库触发器或默认机制维护更新时间
    private LocalDateTime updateTime; // 记录版本发布的最后更新时间

    public Long getId() { // 返回数据库主键
        return id; // 直接暴露实体主键值
    }

    public void setId(Long id) { // 设置数据库主键，通常仅在测试或回填场景使用
        this.id = id; // 保存主键字段
    }

    public String getVersionCode() { // 返回版本字符串
        return versionCode; // 用于导入结果展示和唯一查询
    }

    public void setVersionCode(String versionCode) { // 设置版本字符串
        this.versionCode = versionCode; // 保存业务版本号
    }

    public Long getVersionNumber() { // 返回数值版本号
        return versionNumber; // 供版本比较与排序使用
    }

    public void setVersionNumber(Long versionNumber) { // 设置数值版本号
        this.versionNumber = versionNumber; // 保存用于比较的版本值
    }

    public Long getMinClientVersionNumber() { // 返回最小兼容客户端版本号
        return minClientVersionNumber; // 用于判断是否触发强更
    }

    public void setMinClientVersionNumber(Long minClientVersionNumber) { // 设置最小兼容客户端版本号
        this.minClientVersionNumber = minClientVersionNumber; // 保存强更阈值
    }

    public Boolean getActive() { // 返回是否激活
        return active; // 表示该版本是否对外可见
    }

    public void setActive(Boolean active) { // 设置激活状态
        this.active = active; // 控制该版本是否参与最新版本查询
    }

    public String getManifest() { // 返回原始 manifest JSON
        return manifest; // 更新服务会把它反序列化成清单对象
    }

    public void setManifest(String manifest) { // 设置原始 manifest JSON
        this.manifest = manifest; // 保存完整版本清单文本
    }

    public LocalDateTime getCreateTime() { // 返回创建时间
        return createTime; // 供审计和排障查看
    }

    public LocalDateTime getUpdateTime() { // 返回更新时间
        return updateTime; // 供审计和排障查看
    }
}
