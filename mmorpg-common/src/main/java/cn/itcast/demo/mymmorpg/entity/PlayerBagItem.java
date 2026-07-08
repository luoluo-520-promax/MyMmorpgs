/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-common/src/main/java/cn/itcast/demo/mymmorpg/entity/PlayerBagItem.java
 * 2) 所属模块：mmorpg-common / entity
 * 3) 主要职责：玩家背包道具行实体，映射 player_bag_item 表。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column; // JPA 列映射
import jakarta.persistence.Entity; // JPA 实体标记
import jakarta.persistence.GeneratedValue; // 主键生成策略
import jakarta.persistence.GenerationType; // 主键生成类型枚举
import jakarta.persistence.Id; // 主键字段
import jakarta.persistence.Table; // 表名映射

/** 玩家背包中的单个道具槽位记录 */
@Entity // 标记为 JPA 实体
@Table(name = "player_bag_item") // 对应数据库表 player_bag_item
public class PlayerBagItem {

    @Id // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键
    private Long id; // 行主键

    @Column(name = "player_id", nullable = false) // 所属玩家
    private Long playerId; // 玩家 ID

    @Column(name = "item_config_id", nullable = false) // 道具配置 ID
    private Integer itemConfigId; // 道具模板 ID

    @Column(name = "count", nullable = false) // 数量列
    private Integer count = 1; // 道具数量，默认 1

    @Column(name = "bind", nullable = false) // 绑定状态列
    private Integer bind = 0; // 0=未绑定，1=已绑定

    @Column(name = "slot_index", nullable = false) // 背包槽位索引
    private Integer slotIndex = 0; // 槽位序号

    public Long getId() { // 获取主键
        return id; // 返回主键值
    }

    public void setId(Long id) { // 设置主键
        this.id = id; // 赋值主键
    }

    public Long getPlayerId() { // 获取玩家 ID
        return playerId; // 返回玩家 ID
    }

    public void setPlayerId(Long playerId) { // 设置玩家 ID
        this.playerId = playerId; // 赋值玩家 ID
    }

    public Integer getItemConfigId() { // 获取道具配置 ID
        return itemConfigId; // 返回配置 ID
    }

    public void setItemConfigId(Integer itemConfigId) { // 设置道具配置 ID
        this.itemConfigId = itemConfigId; // 赋值配置 ID
    }

    public Integer getCount() { // 获取数量
        return count; // 返回数量
    }

    public void setCount(Integer count) { // 设置数量
        this.count = count; // 赋值数量
    }

    public Integer getBind() { // 获取绑定状态
        return bind; // 返回绑定标记
    }

    public void setBind(Integer bind) { // 设置绑定状态
        this.bind = bind; // 赋值绑定标记
    }

    public Integer getSlotIndex() { // 获取槽位索引
        return slotIndex; // 返回槽位
    }

    public void setSlotIndex(Integer slotIndex) { // 设置槽位索引
        this.slotIndex = slotIndex; // 赋值槽位
    }

    public boolean isBound() { // 是否已绑定
        return bind != null && bind != 0; // 非 0 视为绑定
    }
}
