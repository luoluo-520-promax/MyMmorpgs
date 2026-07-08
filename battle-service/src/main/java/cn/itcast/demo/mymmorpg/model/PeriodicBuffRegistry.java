/**
 * 文件说明：周期性 Buff 注册表。
 * 职责：管理每个配置了 periodic_interval 的 Buff 对应的 PeriodicBuff 实例，与 Redis 状态同步。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 配置实体
import cn.itcast.demo.mymmorpg.service.BuffService; // Buff 业务服务（引用 ActiveBuffEntry）
import cn.itcast.demo.mymmorpg.service.ConfigQueryService; // 配置查询服务
import org.springframework.context.annotation.Lazy; // 延迟注入
import org.springframework.stereotype.Component; // Spring 组件

import java.util.ArrayList; // 可变列表
import java.util.HashSet; // 哈希集合
import java.util.List; // 列表接口
import java.util.Set; // 集合接口
import java.util.concurrent.ConcurrentHashMap; // 并发哈希映射

/**
 * 每个配置了 {@link BuffConfig#getPeriodicInterval()} 的 Buff 对应一个 {@link PeriodicBuff} 实例；与 Redis 状态同步。
 */
@Component // 注册为 Spring Bean
public class PeriodicBuffRegistry { // 周期 Buff 实例注册表

    /** 活跃周期 Buff 实例映射：key = entityId:buffId */
    private final ConcurrentHashMap<String, PeriodicBuff> active = new ConcurrentHashMap<>(); // 活跃实例表
    /** 周期 Buff 工厂 */
    private final PeriodicBuffFactory factory; // 创建实例
    /** 配置查询服务 */
    private final ConfigQueryService configQueryService; // 查 Buff 配置

    /**
     * 构造器注入依赖。
     *
     * @param factory             周期 Buff 工厂
     * @param configQueryService  配置查询服务
     */
    public PeriodicBuffRegistry(@Lazy PeriodicBuffFactory factory, ConfigQueryService configQueryService) { // 构造器注入
        this.factory = factory; // 保存工厂
        this.configQueryService = configQueryService; // 保存配置服务
    }

    /**
     * 在持久化 Buff 列表变更后调用：移除已不存在的周期任务，新建或更新层数。
     *
     * @param entityId 实体 ID
     * @param list     当前活跃 Buff 条目列表
     */
    public void syncEntityBuffs(long entityId, List<BuffService.ActiveBuffEntry> list) { // 同步实体 Buff 列表
        String prefix = entityId + ":"; // 该实体的 key 前缀
        Set<String> wantKeys = new HashSet<>(); // 期望保留的 key 集合
        for (BuffService.ActiveBuffEntry e : list) { // 遍历当前 Buff 列表
            BuffConfig c = configQueryService.findBuffById(e.buffId); // 查配置
            if (c != null && c.getPeriodicInterval() != null && c.getPeriodicInterval() > 0) { // 有周期间隔
                wantKeys.add(key(entityId, e.buffId)); // 加入期望集合
            }
        }
        for (String k : new ArrayList<>(active.keySet())) { // 遍历已有活跃实例（拷贝 key 防并发修改）
            if (!k.startsWith(prefix)) { // 非本实体
                continue; // 跳过
            }
            if (!wantKeys.contains(k)) { // 不再需要
                PeriodicBuff pb = active.remove(k); // 移除实例
                if (pb != null) { // 实例存在
                    pb.destroy(); // 销毁定时任务
                }
            }
        }
        for (BuffService.ActiveBuffEntry e : list) { // 再次遍历确保新建/更新
            BuffConfig c = configQueryService.findBuffById(e.buffId); // 查配置
            if (c == null || c.getPeriodicInterval() == null || c.getPeriodicInterval() <= 0) { // 无周期配置
                continue; // 跳过
            }
            String k = key(entityId, e.buffId); // 生成 key
            PeriodicBuff pb = active.get(k); // 查已有实例
            if (pb == null) { // 不存在则创建
                PeriodicBuff created = factory.create(entityId, c); // 工厂创建
                PeriodicBuff prev = active.putIfAbsent(k, created); // 并发安全放入
                if (prev == null) { // 成功放入
                    created.registerFrameTask(); // 注册定时任务
                    pb = created; // 使用新实例
                } else { // 已有其他线程创建
                    created.destroy(); // 销毁多余实例
                    pb = prev; // 使用已有实例
                }
            }
            pb.setStackCount(e.stackCount); // 同步层数
        }
    }

    /**
     * 移除指定实体上的周期 Buff 实例。
     *
     * @param entityId 实体 ID
     * @param buffId   Buff 模板 ID
     */
    public void remove(long entityId, int buffId) { // 移除周期 Buff
        PeriodicBuff pb = active.remove(key(entityId, buffId)); // 从活跃表移除
        if (pb != null) { // 实例存在
            pb.destroy(); // 销毁定时任务
        }
    }

    /**
     * 生成活跃实例映射 key。
     *
     * @param entityId 实体 ID
     * @param buffId   Buff 模板 ID
     * @return entityId:buffId 格式 key
     */
    private static String key(long entityId, int buffId) { // 生成映射 key
        return entityId + ":" + buffId; // 拼接 key
    }
}
