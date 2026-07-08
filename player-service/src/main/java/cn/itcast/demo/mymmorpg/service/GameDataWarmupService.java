/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/GameDataWarmupService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：游戏配置表异步预热，并行加载地图/怪物/技能/Buff/道具/活动至本地缓存。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 启动后并行预热 map_config/item_config 等至 ConfigQueryService 缓存

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // buff_config 表实体
import cn.itcast.demo.mymmorpg.entity.ItemConfig; // item_config 表实体
import cn.itcast.demo.mymmorpg.entity.MapConfig; // map_config 表实体
import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // monster_config 表实体
import cn.itcast.demo.mymmorpg.entity.SkillConfig; // skill_config 表实体
import cn.itcast.demo.mymmorpg.repository.ActivityRepository; // activity 表，仅 activity 模块有 Bean
import cn.itcast.demo.mymmorpg.repository.BuffConfigRepository; // buff_config JPA 全表扫描
import cn.itcast.demo.mymmorpg.repository.ItemConfigRepository; // item_config JPA 全表扫描
import cn.itcast.demo.mymmorpg.repository.MapConfigRepository; // map_config JPA 全表扫描
import cn.itcast.demo.mymmorpg.repository.MonsterConfigRepository; // monster_config JPA 全表扫描
import cn.itcast.demo.mymmorpg.repository.SkillConfigRepository; // skill_config JPA 全表扫描
import org.slf4j.Logger; // 记录预热完成/失败日志
import org.slf4j.LoggerFactory; // 创建 GameDataWarmupService Logger
import org.springframework.beans.factory.ObjectProvider; // ActivityRepository 可选，无 activity 模块时为 null
import org.springframework.beans.factory.annotation.Value; // game.data-warmup.enabled 开关
import org.springframework.stereotype.Service; // GameDataWarmupStarter 注入本服务

import java.util.List; // findAll 返回的配置列表
import java.util.concurrent.CompletableFuture; // 六路配置表并行预热
import java.util.concurrent.atomic.AtomicReference; // 线程安全记录预热状态 WarmupState

/**
 * 游戏配置异步预热服务。
 */
@Service // 单例，由 GameDataWarmupStarter 在 ApplicationReady 时触发 warmupAsync
public class GameDataWarmupService { // 六路 CompletableFuture 并行预热 ConfigQueryService 本地缓存

    /** 预热生命周期状态，供监控/JMX 查询 */
    public enum WarmupState { // 预热生命周期状态，供监控/JMX 查询
        NOT_STARTED, // 尚未启动预热
        RUNNING, // 六路并行预热进行中
        DONE, // 全部配置表预热成功
        FAILED // 任一分支异常导致失败
    }

    private static final Logger log = LoggerFactory.getLogger(GameDataWarmupService.class); // 预热耗时与失败 warn/info 日志

    /** 带本地 Redis 缓存的配置查询入口，预热即逐条调用 findXxxById */
    private final ConfigQueryService configQueryService; // findMapById/findItemById 等触发缓存回填

    private final MapConfigRepository mapConfigRepository; // map_config findAll 数据源
    private final MonsterConfigRepository monsterConfigRepository; // monster_config findAll 数据源
    private final SkillConfigRepository skillConfigRepository; // skill_config findAll 数据源
    private final BuffConfigRepository buffConfigRepository; // buff_config findAll 数据源
    private final ItemConfigRepository itemConfigRepository; // item_config findAll 数据源

    /** 活动表仓储，player-service 无 activity 模块时为 null */
    private final ActivityRepository activityRepository; // findByOpenedTrue 预热活动列表，无模块则跳过

    /** game.data-warmup.enabled：false 时跳过预热 */
    private final boolean enabled; // false 时 warmupAsync 立即返回 completedFuture

    /** 当前预热状态，CAS 防重复启动 */
    private final AtomicReference<WarmupState> state = new AtomicReference<>(WarmupState.NOT_STARTED); // compareAndSet NOT_STARTED→RUNNING

    public GameDataWarmupService( // 游戏配置异步预热服务
            ConfigQueryService configQueryService, // 逐条 findXxxById 写入本地缓存
            MapConfigRepository mapConfigRepository, // 地图配置 JPA
            MonsterConfigRepository monsterConfigRepository, // 怪物配置 JPA
            SkillConfigRepository skillConfigRepository, // 技能配置 JPA
            BuffConfigRepository buffConfigRepository, // Buff 配置 JPA
            ItemConfigRepository itemConfigRepository, // 道具配置 JPA
            ObjectProvider<ActivityRepository> activityRepositoryProvider, // 可选 activity 模块
            @Value("${game.data-warmup.enabled:false}") boolean enabled) { // 启动时是否预热配置表缓存，默认关闭
        this.configQueryService = configQueryService; // ConfigQueryService 缓存回填入口
        this.mapConfigRepository = mapConfigRepository; // warmupMaps 数据源
        this.monsterConfigRepository = monsterConfigRepository; // warmupMonsters 数据源
        this.skillConfigRepository = skillConfigRepository; // warmupSkills 数据源
        this.buffConfigRepository = buffConfigRepository; // warmupBuffs 数据源
        this.itemConfigRepository = itemConfigRepository; // warmupItems 数据源
        this.activityRepository = activityRepositoryProvider.getIfAvailable(); // 无 activity 模块则为 null
        this.enabled = enabled; // false 时 warmupAsync 立即 completedFuture
    }

    /** 启动异步预热：六路 CompletableFuture 并行，不阻塞调用线程 */
    public CompletableFuture<Void> warmupAsync() { // 启动异步预热：六路 CompletableFuture 并行，不阻塞调用线程
        if (!enabled) { // game.data-warmup.enabled=false
            return CompletableFuture.completedFuture(null); // 开关关闭，立即返回已完成 Future
        }
        if (!state.compareAndSet(WarmupState.NOT_STARTED, WarmupState.RUNNING)) { // 已在 RUNNING/DONE/FAILED
            return CompletableFuture.completedFuture(null); // CAS 失败，防重复启动
        }
        long startMs = System.currentTimeMillis(); // 记录预热耗时起点 epochMillis
        return CompletableFuture.allOf( // 游戏配置异步预热服务
                        CompletableFuture.runAsync(this::warmupMaps), // 并行预热 map_config 至 ConfigQueryService
                        CompletableFuture.runAsync(this::warmupMonsters), // 并行预热 monster_config
                        CompletableFuture.runAsync(this::warmupSkills), // 并行预热 skill_config
                        CompletableFuture.runAsync(this::warmupBuffs), // 并行预热 buff_config
                        CompletableFuture.runAsync(this::warmupItems), // 并行预热 item_config
                        CompletableFuture.runAsync(this::warmupActivities)) // 并行预热 activity opened=true 列表
                .whenComplete((unused, ex) -> { // 游戏配置异步预热服务
                    if (ex != null) { // 任一分支抛异常
                        state.set(WarmupState.FAILED); // 标记 WarmupState.FAILED
                        log.warn("game data warmup failed err={}", ex.toString()); // 记录失败原因，不阻断主流程
                    } else { // 游戏配置异步预热服务
                        state.set(WarmupState.DONE); // 六路全部成功
                        log.info("game data warmup done costMs={}", System.currentTimeMillis() - startMs); // 输出总耗时 ms
                    }
                }); // lambda/匿名比较器结束，供排序或 stream 使用
    }

    /** 查询当前预热状态 WarmupState */
    public WarmupState state() { // 查询当前预热状态 WarmupState
        return state.get(); // 供 GameDataWarmupStarter 日志或健康检查读取
    }

    /** 预热地图：findAll 后逐条 findMapById 写入 ConfigQueryService 缓存 */
    private void warmupMaps() { // 预热地图：findAll 后逐条 findMapById 写入 ConfigQueryService 缓存
        List<MapConfig> list = mapConfigRepository.findAll(); // SELECT * FROM map_config
        for (MapConfig config : list) { // 逐条预热单地图配置
            configQueryService.findMapById(config.getId()); // 触发 map_config.id 单条缓存加载
        }
    }

    /** 预热怪物：listAllMonsters 全量索引 + 逐条 findMonsterById */
    private void warmupMonsters() { // 预热怪物：listAllMonsters 全量索引 + 逐条 findMonsterById
        List<MonsterConfig> list = monsterConfigRepository.findAll(); // SELECT * FROM monster_config
        configQueryService.listAllMonsters(); // 预热 monster_config 列表索引
        for (MonsterConfig config : list) { // 逐条预热单怪物配置
            configQueryService.findMonsterById(config.getId()); // 写入 ConfigQueryService 怪物缓存
        }
    }

    /** 预热技能：逐条 findSkillById 加载 skill_config */
    private void warmupSkills() { // 预热技能：逐条 findSkillById 加载 skill_config
        for (SkillConfig config : skillConfigRepository.findAll()) { // SELECT * FROM skill_config
            configQueryService.findSkillById(config.getId()); // 预热 skill_config.id 单条缓存
        }
    }

    /** 预热 Buff：逐条 findBuffById 加载 buff_config */
    private void warmupBuffs() { // 预热 Buff：逐条 findBuffById 加载 buff_config
        for (BuffConfig config : buffConfigRepository.findAll()) { // SELECT * FROM buff_config
            configQueryService.findBuffById(config.getId()); // 预热 buff_config.id 单条缓存
        }
    }

    /** 预热道具：逐条 findItemById 加载 item_config（背包/商店依赖） */
    private void warmupItems() { // 预热道具：逐条 findItemById 加载 item_config（背包/商店依赖）
        for (ItemConfig config : itemConfigRepository.findAll()) { // SELECT * FROM item_config
            configQueryService.findItemById(config.getId()); // 预热 item_config.id 单条缓存
        }
    }

    /** 预热活动：仅 activity 模块存在时查 opened=true 活动 */
    private void warmupActivities() { // 预热活动：仅 activity 模块存在时查 opened=true 活动
        if (activityRepository == null) { // player-service 无 activity 模块 Bean
            return; // 跳过 activity 表预热
        }
        activityRepository.findByOpenedTrue(); // SELECT activity WHERE opened=true，预热 JPA 查询计划
    }
}
