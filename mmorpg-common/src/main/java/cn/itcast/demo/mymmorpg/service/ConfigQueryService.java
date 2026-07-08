/**
 * 游戏静态配置查询服务：地图、怪物、技能、Buff、道具等策划表数据，
 * 通过 Spring Cache + Redis 缓存热点配置，减轻数据库读压力。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 效果配置（持续时间、叠加规则、属性修正）
import cn.itcast.demo.mymmorpg.entity.ItemConfig; // 道具模板配置（类型、堆叠、使用效果）
import cn.itcast.demo.mymmorpg.entity.MapConfig; // 地图/场景配置（尺寸、分线数、传送点）
import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // 怪物模板配置（等级、属性、掉落表引用）
import cn.itcast.demo.mymmorpg.entity.SkillConfig; // 技能配置（冷却、消耗、伤害公式参数）
import cn.itcast.demo.mymmorpg.repository.BuffConfigRepository; // Buff 策划表 JPA 仓储
import cn.itcast.demo.mymmorpg.repository.ItemConfigRepository; // 道具策划表 JPA 仓储
import cn.itcast.demo.mymmorpg.repository.MapConfigRepository; // 地图策划表 JPA 仓储
import cn.itcast.demo.mymmorpg.repository.MonsterConfigRepository; // 怪物策划表 JPA 仓储
import cn.itcast.demo.mymmorpg.repository.SkillConfigRepository; // 技能策划表 JPA 仓储

import org.springframework.cache.annotation.Cacheable; // 方法结果写入 Redis，命中后跳过 DB 查询
import org.springframework.stereotype.Service; // 注册为 Spring 业务 Bean，供战斗/场景等模块注入
import org.springframework.lang.Nullable; // 标记可能返回 null（配置 ID 不存在时）

import java.util.List; // 怪物全量列表返回类型

/**
 * 带 Redis 缓存的策划表查询：表数据变更频率低，适合按 ID 或整表缓存。
 */
@Service // 注入到 BattleService、SceneService 等需要读策划表的组件
public class ConfigQueryService {

    /** 地图配置仓储，查场景进入规则、分线上限、传送点坐标 */
    private final MapConfigRepository mapConfigRepository;
    /** 怪物配置仓储，查刷怪参数、战斗属性、AI 技能列表 */
    private final MonsterConfigRepository monsterConfigRepository;
    /** 技能配置仓储，查技能释放条件、CD、MP 消耗与效果数值 */
    private final SkillConfigRepository skillConfigRepository;
    /** Buff 配置仓储，查状态效果定义与叠加规则 */
    private final BuffConfigRepository buffConfigRepository;
    /** 道具配置仓储，查物品模板（非玩家背包实例 uid） */
    private final ItemConfigRepository itemConfigRepository;

    /** 构造器注入各配置仓储，便于单元测试替换 Mock Repository */
    public ConfigQueryService(
            MapConfigRepository mapConfigRepository,
            MonsterConfigRepository monsterConfigRepository,
            SkillConfigRepository skillConfigRepository,
            BuffConfigRepository buffConfigRepository,
            ItemConfigRepository itemConfigRepository) {
        this.mapConfigRepository = mapConfigRepository;
        this.monsterConfigRepository = monsterConfigRepository;
        this.skillConfigRepository = skillConfigRepository;
        this.buffConfigRepository = buffConfigRepository;
        this.itemConfigRepository = itemConfigRepository;
    }

    /**
     * 按地图 ID 查询 MapConfig；缓存 key 为 id，不存在时不缓存 null。
     * 玩家切场景时校验 mapId 合法性并加载碰撞/传送数据。
     */
    @Nullable // 调用方需处理 mapId 不存在的情况
    @Cacheable(cacheNames = "mapConfigById", key = "#id", unless = "#result == null") // Redis key=mapConfigById::id
    public MapConfig findMapById(int id) {
        return mapConfigRepository.findById(id).orElse(null); // 无记录返回 null，由上层返回「地图不存在」给客户端
    }

    /** 加载全部怪物配置；整表缓存，供刷怪系统、GM 工具、战斗匹配一次性读取 */
    @Cacheable(cacheNames = "allMonsterConfigs") // 整表缓存，策划热更后需 evict 或 TTL 过期
    public List<MonsterConfig> listAllMonsters() {
        return monsterConfigRepository.findAll(); // 返回 DB 中全部怪物模板
    }

    /** 按怪物模板 ID 查单条；战斗开始时加载怪物 HP/攻击/技能列表 */
    @Nullable
    @Cacheable(cacheNames = "monsterConfigById", key = "#id", unless = "#result == null")
    public MonsterConfig findMonsterById(int id) {
        return monsterConfigRepository.findById(id).orElse(null);
    }

    /** 按技能 ID 查 SkillConfig；释放技能前校验冷却、MP 消耗、目标类型 */
    @Nullable
    @Cacheable(cacheNames = "skillConfigById", key = "#id", unless = "#result == null")
    public SkillConfig findSkillById(int id) {
        return skillConfigRepository.findById(id).orElse(null);
    }

    /** 按 Buff ID 查 BuffConfig；附加/移除 Buff 时读取持续时间与效果类型 */
    @Nullable
    @Cacheable(cacheNames = "buffConfigById", key = "#id", unless = "#result == null")
    public BuffConfig findBuffById(int id) {
        return buffConfigRepository.findById(id).orElse(null);
    }

    /** 按道具模板 ID 查 ItemConfig；背包展示、使用/出售前读取物品属性与价格 */
    @Nullable
    @Cacheable(cacheNames = "itemConfigById", key = "#id", unless = "#result == null")
    public ItemConfig findItemById(int id) {
        return itemConfigRepository.findById(id).orElse(null);
    }
}
