/**
 * 文件说明：战斗核心业务服务类。
 * 职责：承载状态同步战斗的完整流程——开战、回合行动、结算；怪物属性来自 monster_config，
 *       运行时状态持久化到 Redis，结算通过 RocketMQ 事件发布。
 * 注意：修改前请确认上下游依赖、协议字段与缓存键是否受影响。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.BattleSceneFactory; // 战斗场景工厂，用于创建战斗运行时状态对象
import cn.itcast.demo.mymmorpg.service.BattleEventPublisher; // 战斗事件发布器（RocketMQ 或空实现）
import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // 怪物配置实体（来自数据库/配置表）
import cn.itcast.demo.mymmorpg.entity.Player; // 玩家实体
import cn.itcast.demo.mymmorpg.support.BattlePolicy; // 战斗策略脚本（伤害、治疗计算）
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议消息 ID 常量
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议消息封装（消息 ID + 二进制载荷）
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 返回码常量（成功、失败原因等）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq; // 客户端→服务端：战斗行动请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionScRsp; // 服务端→客户端：战斗行动响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq; // 客户端→服务端：战斗结束请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndScRsp; // 服务端→客户端：战斗结束响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEntityInfo; // 战斗实体信息（血量、攻击等）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // 客户端→服务端：开始战斗请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartScRsp; // 服务端→客户端：开始战斗响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleSyncScNotify; // 服务端→客户端：战斗状态同步推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.EntityUpdate; // 实体属性更新片段（用于同步）
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward; // 道具奖励信息
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // 玩家数据仓库（JPA）
import cn.itcast.demo.mymmorpg.port.BattleScenePort; // 战斗场景端口（查怪物、移除怪物）
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // 玩家通知端口（推送同步消息）
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort; // 玩家进度端口（加经验等）
import cn.itcast.demo.mymmorpg.service.ConfigQueryService; // 配置查询服务
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 序列化/反序列化工具
import org.springframework.data.redis.core.StringRedisTemplate; // Spring Redis 字符串操作模板
import org.springframework.stereotype.Service; // 标记为 Spring 服务 Bean

import java.time.Duration; // 时间间隔类型（用于 Redis 过期时间）
import java.util.HashMap; // 哈希映射（货币奖励等）
import java.util.List;
import java.util.concurrent.atomic.AtomicLong; // 线程安全的自增长整型（战斗 ID 序列）

/**
 * 状态同步战斗：怪物属性来自 {@code monster_config}，运行时状态存 Redis、结算走 RocketMQ 事件。
 */
@Service // 注册为 Spring 单例服务，可被注入到其他组件
public class BattleService { // 战斗核心业务服务类

    /** Redis 键前缀：存储单场战斗完整状态 */
    private static final String REDIS_STATE = "battle:state:"; // Redis 键前缀：战斗状态
    /** Redis 键前缀：玩家当前进行中的战斗 ID */
    private static final String REDIS_ACTIVE = "battle:active:"; // Redis 键前缀：活跃战斗绑定
    /** 战斗相关 Redis 键的过期时间：30 分钟 */
    private static final Duration STATE_TTL = Duration.ofMinutes(30); // 状态 TTL
    /** 客户端时间戳允许偏差窗口：5 分钟，防重放 */
    private static final long TIMESTAMP_WINDOW_MS = 300_000L; // 时间戳校验窗口

    /** 同步类型：属性变更（血量、蓝量等） */
    public static final int SYNC_ATTR = 1; // 同步类型：属性
    /** 同步类型：战斗已结束 */
    public static final int SYNC_BATTLE_END = 4; // 同步类型：战斗结束

    /** 行动类型：普通攻击 */
    private static final int ACTION_NORMAL = 1; // 普通攻击
    /** 行动类型：技能 */
    private static final int ACTION_SKILL = 2; // 技能攻击
    /** 行动类型：使用道具 */
    private static final int ACTION_ITEM = 3; // 使用道具

    /** 配置查询服务 */
    private final ConfigQueryService configQueryService; // 查怪物等配置
    /** 玩家数据仓库 */
    private final PlayerRepository playerRepository; // JPA 玩家仓库
    /** 战斗场景端口 */
    private final BattleScenePort battleScenePort; // 场景怪物查询
    /** Redis 字符串模板 */
    private final StringRedisTemplate stringRedisTemplate; // Redis 操作
    /** JSON 对象映射器 */
    private final ObjectMapper objectMapper; // 状态序列化
    /** 战斗数值策略 */
    private final BattlePolicy battlePolicy; // 伤害/治疗计算
    /** 战斗事件发布器 */
    private final BattleEventPublisher battleEventPublisher; // MQ 事件
    /** 玩家通知端口 */
    private final PlayerNotificationPort playerNotificationPort; // 推送同步
    /** 玩家进度端口 */
    private final PlayerProgressPort playerProgressPort; // 加经验
    /** 战斗场景工厂 */
    private final BattleSceneFactory battleSceneFactory; // 创建状态对象

    /** 战斗 ID 自增序列，起始值 10000 */
    private final AtomicLong battleIdSeq = new AtomicLong(10_000L); // 战斗 ID 序列

    /**
     * JMX：已分配的最大战斗 ID（单调递增）。
     *
     * @return 当前序列值
     */
    public long getIssuedBattleIdMax() { // 供监控暴露：当前已发出的最大 battleId
        return battleIdSeq.get(); // 读取原子变量当前值（不递增）
    }

    /**
     * JMX：当前 Redis 中玩家进行中的战斗绑定数量。
     *
     * @return 活跃绑定数
     */
    public int countActiveBattleBindings() { // 供监控暴露：有多少玩家正处于战斗
        var keys = stringRedisTemplate.keys(REDIS_ACTIVE + "*"); // 扫描所有 battle:active:* 键
        return keys == null ? 0 : keys.size(); // 无键返回 0，否则返回键数量
    }

    /**
     * 构造器注入所有依赖。
     *
     * @param configQueryService       配置查询服务
     * @param playerRepository         玩家仓库
     * @param battleScenePort          战斗场景端口
     * @param stringRedisTemplate      Redis 模板
     * @param objectMapper             JSON 映射器
     * @param battlePolicy             战斗策略
     * @param battleEventPublisher     事件发布器
     * @param playerNotificationPort   通知端口
     * @param playerProgressPort       进度端口
     * @param battleSceneFactory       场景工厂
     */
    public BattleService(
            ConfigQueryService configQueryService,
            PlayerRepository playerRepository,
            BattleScenePort battleScenePort,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            BattlePolicy battlePolicy,
            BattleEventPublisher battleEventPublisher,
            PlayerNotificationPort playerNotificationPort,
            PlayerProgressPort playerProgressPort,
            BattleSceneFactory battleSceneFactory) {
        this.configQueryService = configQueryService; // 保存配置服务
        this.playerRepository = playerRepository; // 保存玩家仓库
        this.battleScenePort = battleScenePort; // 保存场景端口
        this.stringRedisTemplate = stringRedisTemplate; // 保存 Redis 模板
        this.objectMapper = objectMapper; // 保存 JSON 映射器
        this.battlePolicy = battlePolicy; // 保存战斗策略
        this.battleEventPublisher = battleEventPublisher; // 保存事件发布器
        this.playerNotificationPort = playerNotificationPort; // 保存通知端口
        this.playerProgressPort = playerProgressPort; // 保存进度端口
        this.battleSceneFactory = battleSceneFactory; // 保存场景工厂
    }

    /**
     * 处理「开始战斗」请求。
     *
     * @param playerId 玩家 ID
     * @param req      开始战斗请求
     * @return 协议响应消息
     */
    public ProtocolMessage handleBattleStart(long playerId, BattleStartCsReq req) { // 处理开始战斗
        if (playerId <= 0) { // 未选择角色或会话无效
            return startRsp(RetCode.PLAYER_NOT_SELECTED, 0, null, null, 0); // 返回未选角错误码
        }
        if (req.getLineupId() == 0) { // 阵容 ID 为 0 表示无效
            return startRsp(RetCode.BATTLE_LINEUP_INVALID, 0, null, null, 0); // 返回阵容无效
        }
        long enemyEntityId = Integer.toUnsignedLong(req.getEnemyId()); // 将请求中的敌人 ID 转为无符号 long
        var refOpt = battleScenePort.findMonsterForBattle(playerId, enemyEntityId); // 查场景中怪物引用
        if (refOpt.isEmpty()) { // 场景中没有该敌人
            return startRsp(RetCode.BATTLE_ENEMY_NOT_FOUND, 0, null, null, 0); // 返回敌人不存在
        }
        var ref = refOpt.get(); // 取出怪物场景引用（含 sceneId、模板 ID 等）
        MonsterConfig mc = configQueryService.findMonsterById(ref.monsterTemplateId()); // 按模板 ID 查怪物配置
        if (mc == null) { // 配置表无此怪物
            return startRsp(RetCode.BATTLE_ENEMY_NOT_FOUND, 0, null, null, 0); // 仍按敌人不存在处理
        }
        var playerOpt = playerRepository.findById(playerId); // 从数据库加载玩家
        if (playerOpt.isEmpty()) { // 玩家不存在
            return startRsp(RetCode.PLAYER_NOT_FOUND, 0, null, null, 0); // 返回玩家未找到
        }
        Player player = playerOpt.get(); // 取出玩家实体
        String activeKey = REDIS_ACTIVE + playerId; // 该玩家「进行中战斗」的 Redis 键
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(activeKey))) { // 已有进行中的战斗
            return startRsp(RetCode.BATTLE_ALREADY_ACTIVE, 0, null, null, 0); // 不允许重复开战
        }

        int[] ps = playerCombatStats(player.getLevel() == null ? 1 : player.getLevel()); // 按等级计算玩家战斗四维
        long battleId = battleIdSeq.incrementAndGet(); // 分配新的全局唯一战斗 ID
        var state = battleSceneFactory.createState( // 工厂创建状态骨架
                battleId,
                ref.sceneId(),
                playerId,
                Integer.toUnsignedLong(req.getLineupId()),
                enemyEntityId,
                mc,
                player,
                mc.getName(),
                mc.getLevel() == null ? 1 : mc.getLevel(),
                mc.getExpReward() == null ? 0 : mc.getExpReward(),
                req.getBattleType()
        );
        state.playerHpMax = ps[0]; // 设置玩家最大 HP
        state.playerHp = ps[0]; // 设置玩家当前 HP
        state.playerMpMax = ps[1]; // 设置玩家最大 MP
        state.playerMp = ps[1]; // 设置玩家当前 MP
        state.playerAttack = ps[2]; // 设置玩家攻击力
        state.playerDefense = ps[3]; // 设置玩家防御力
        state.enemyHpMax = mc.getHpMax() == null ? 100 : mc.getHpMax(); // 敌人最大 HP
        state.enemyHp = state.enemyHpMax; // 敌人当前 HP 满血
        state.enemyMpMax = mc.getMpMax() == null ? 0 : mc.getMpMax(); // 敌人最大 MP
        state.enemyMp = state.enemyMpMax; // 敌人当前 MP
        state.enemyAttack = mc.getAttack() == null ? 10 : mc.getAttack(); // 敌人攻击力
        state.enemyDefense = mc.getDefense() == null ? 5 : mc.getDefense(); // 敌人防御力
        state.nextActionId = 1L; // 初始化行动序号
        saveState(state); // 将完整状态写入 Redis
        stringRedisTemplate.opsForValue().set(activeKey, String.valueOf(battleId), STATE_TTL); // 记录玩家→战斗 ID 绑定

        battleEventPublisher.publishBattleStarted(playerId, battleId, ref.sceneId(), enemyEntityId, ref.monsterTemplateId()); // 发送战斗开始 MQ 事件

        BattleEntityInfo pe = buildEntityInfo(playerId, state.playerName, state.playerLevel, // 构建协议中的玩家实体信息
                state.playerHp, state.playerHpMax, state.playerMp, state.playerMpMax,
                state.playerAttack, state.playerDefense);
        BattleEntityInfo ee = buildEntityInfo(enemyEntityId, state.enemyName, state.enemyLevel, // 构建敌人实体信息
                state.enemyHp, state.enemyHpMax, state.enemyMp, state.enemyMpMax,
                state.enemyAttack, state.enemyDefense);

        return startRsp(RetCode.OK, battleId, ee, pe, ref.sceneId()); // 返回成功及双方属性、场景 ID
    }

    /**
     * 处理「战斗行动」（攻击/技能/道具）。
     *
     * @param playerId 玩家 ID
     * @param req      战斗行动请求
     * @return 协议响应消息
     */
    public ProtocolMessage handleBattleAction(long playerId, BattleActionCsReq req) { // 处理战斗行动
        if (playerId <= 0) { // 玩家 ID 无效
            return actionRsp(RetCode.PLAYER_NOT_SELECTED, req.getBattleId(), 0, 0, 0); // 未选角
        }
        BattleRuntimeState state = loadState(req.getBattleId()); // 从 Redis 加载战斗状态
        if (state == null) { // 战斗不存在或已过期
            return actionRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, 0, 0); // 战斗未找到
        }
        if (state.playerId != playerId) { // 非本场战斗的拥有者
            return actionRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, 0, 0); // 防越权，同样返回未找到
        }
        if (state.ended) { // 战斗已结束不能再操作
            return actionRsp(RetCode.BATTLE_ALREADY_ENDED, req.getBattleId(), 0, 0, 0); // 已结束
        }
        long now = System.currentTimeMillis(); // 服务端当前毫秒时间戳
        if (Math.abs(now - req.getTimestamp()) > TIMESTAMP_WINDOW_MS) { // 客户端时间与服务器差过大
            return actionRsp(RetCode.BATTLE_INVALID_ACTION, req.getBattleId(), 0, 0, 0); // 视为非法行动
        }

        int actionType = req.getActionType(); // 读取行动类型
        if (actionType < ACTION_NORMAL || actionType > ACTION_ITEM) { // 不在 1~3 合法范围
            return actionRsp(RetCode.BATTLE_INVALID_ACTION, req.getBattleId(), 0, 0, 0); // 非法行动类型
        }

        long targetId = req.getTargetEntityId(); // 目标实体 ID
        int damageDealt = 0; // 本次玩家造成的伤害（累计到响应）
        int healDone = 0; // 本次治疗量
        if (actionType == ACTION_ITEM) { // 使用道具只能对自己
            if (targetId != playerId) { // 目标不是自己
                return actionRsp(RetCode.BATTLE_TARGET_INVALID, req.getBattleId(), 0, 0, 0); // 目标无效
            }
        } else if (actionType == ACTION_NORMAL || actionType == ACTION_SKILL) { // 攻击类只能打敌人
            if (targetId != state.enemyEntityId) { // 目标不是敌人
                return actionRsp(RetCode.BATTLE_TARGET_INVALID, req.getBattleId(), 0, 0, 0); // 目标无效
            }
        }

        long actionId = state.nextActionId++; // 分配本回合行动 ID 并自增
        if (actionType == ACTION_ITEM) { // 使用道具
            healDone = battlePolicy.computeHeal(actionType, req.getItemId()); // 计算治疗量
            state.playerHp = Math.min(state.playerHpMax, state.playerHp + healDone); // 加血不超过上限
        } else { // 攻击类行动
            damageDealt = battlePolicy.computeDamage( // 计算伤害
                    state.playerAttack, state.enemyDefense, actionType, req.getSkillId());
            state.enemyHp = Math.max(0, state.enemyHp - damageDealt); // 扣敌人血，不低于 0
        }

        if (state.enemyHp > 0) { // 敌人仍存活则怪物反击
            int monsterDamage = battlePolicy.computeDamage(state.enemyAttack, state.playerDefense, ACTION_NORMAL, 0); // 怪物普攻
            state.playerHp = Math.max(0, state.playerHp - monsterDamage); // 扣玩家血
        }

        if (state.enemyHp <= 0) { // 敌人死亡
            state.ended = true; // 标记战斗结束
        } else if (state.playerHp <= 0) { // 玩家死亡
            state.ended = true; // 标记战斗结束
        }

        saveState(state); // 回写 Redis
        refreshActiveTtl(playerId, state.battleId); // 刷新玩家活跃战斗键的 TTL

        var syncBuilder = BattleSyncScNotify.newBuilder() // 构建状态同步推送
                .setBattleId(state.battleId) // 设置战斗 ID
                .setSyncType(SYNC_ATTR) // 默认同步类型为属性
                .addEntityUpdates(
                        EntityUpdate.newBuilder()
                        .setEntityId(playerId)
                        .setHp(state.playerHp)
                        .setMp(state.playerMp)) // 玩家血蓝
                .addEntityUpdates(
                        EntityUpdate.newBuilder()
                        .setEntityId(state.enemyEntityId)
                        .setHp(state.enemyHp)
                        .setMp(state.enemyMp)); // 敌人血蓝
        if (state.ended) { // 若本回合导致结束
            syncBuilder.setSyncType(SYNC_BATTLE_END); // 同步类型改为战斗结束
        }
        playerNotificationPort.send(playerId, MessageId.BATTLE_SYNC_SC_NOTIFY, syncBuilder.build().toByteArray()); // 推送同步通知

        return actionRsp(RetCode.OK, state.battleId, actionId, damageDealt, healDone); // 返回行动结果给请求方
    }

    /**
     * 处理「战斗结束/结算」请求。
     *
     * @param playerId 玩家 ID
     * @param req      战斗结束请求
     * @return 协议响应消息
     */
    public ProtocolMessage handleBattleEnd(long playerId, BattleEndCsReq req) { // 处理战斗结算
        if (playerId <= 0) { // 玩家 ID 无效
            return endRsp(RetCode.PLAYER_NOT_SELECTED, req.getBattleId(), 0, null, null); // 未选角
        }
        BattleRuntimeState state = loadState(req.getBattleId()); // 加载战斗状态用于校验结果
        if (state == null) { // 战斗不存在
            return endRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, null, null); // 未找到
        }
        if (state.playerId != playerId) { // 校验归属
            return endRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, null, null); // 非本人战斗
        }

        int clientResult = req.getResult(); // 客户端声称的结果：0 败 1 胜 2 平局/逃跑
        if (!validateResult(state, clientResult)) { // 与服务端状态比对，防作弊
            return endRsp(RetCode.BATTLE_RESULT_MISMATCH, req.getBattleId(), 0, null, null); // 结果不一致
        }

        int exp = 0; // 经验奖励
        var currency = new HashMap<Integer, Integer>(); // 货币奖励
        List<ItemReward> items = List.of(); // 道具奖励

        if (clientResult == 1) { // 胜利
            exp = state.expReward; // 取配置经验
            currency.put(1, 500); // 固定货币奖励
            items = List.of(
                    ItemReward.newBuilder()
                    .setItemId(1001)
                    .setCount(1)
                    .build()); // 固定道具奖励
            battleScenePort.removeMonsterFromScene(playerId, state.enemyEntityId); // 从场景移除怪物
            final int expReward = exp; // lambda 需要 effectively final
            if (expReward > 0) { // 有经验才写库
                playerRepository.findById(playerId).ifPresent(p -> playerProgressPort.addExp(p, expReward)); // 加经验
            }
        }

        battleEventPublisher.publishBattleEnded(playerId, state.battleId, clientResult, exp, req.getDuration()); // MQ：战斗结束
        deleteState(state.battleId); // 删除 Redis 中的战斗状态
        stringRedisTemplate.delete(REDIS_ACTIVE + playerId); // 删除玩家进行中战斗绑定
        var currencyMap = new HashMap<Integer, Integer>(); // 复制一份用于响应（避免外部修改）
        currencyMap.putAll(currency); // 拷贝货币 map

        return endRsp(RetCode.OK, state.battleId, exp, currencyMap, items); // 返回结算奖励
    }

    /**
     * 校验客户端上报结果是否与服务器状态一致。
     *
     * @param state        战斗运行时状态
     * @param clientResult 客户端结果码
     * @return 是否一致
     */
    private static boolean validateResult(BattleRuntimeState state, int clientResult) { // 校验结果一致性
        return switch (clientResult) { // Java switch 表达式
            case 0 -> state.playerHp <= 0; // 失败：玩家应已死
            case 1 -> state.enemyHp <= 0; // 胜利：敌人应已死
            case 2 -> state.playerHp > 0 && state.enemyHp > 0; // 平局/未分胜负：双方都还有血
            default -> false; // 其他结果码非法
        };
    }

    /**
     * 每次行动后延长「进行中战斗」键的生存时间。
     *
     * @param playerId 玩家 ID
     * @param battleId 战斗 ID
     */
    private void refreshActiveTtl(long playerId, long battleId) { // 刷新活跃键 TTL
        stringRedisTemplate.opsForValue().set(REDIS_ACTIVE + playerId, String.valueOf(battleId), STATE_TTL); // 重写并续期
    }

    /**
     * 将战斗运行时状态序列化存入 Redis。
     *
     * @param state 战斗状态
     */
    private void saveState(BattleRuntimeState state) { // 持久化战斗状态
        try { // 捕获序列化异常
            String json = objectMapper.writeValueAsString(state); // 对象转 JSON 字符串
            stringRedisTemplate.opsForValue().set(REDIS_STATE + state.battleId, json, STATE_TTL); // 键：battle:state:{id}
        } catch (Exception e) { // 写入失败
            throw new IllegalStateException("战斗状态写入 Redis 失败", e); // 抛出运行时异常
        }
    }

    /**
     * 按战斗 ID 从 Redis 反序列化状态。
     *
     * @param battleId 战斗 ID
     * @return 战斗状态，不存在或解析失败返回 null
     */
    private BattleRuntimeState loadState(long battleId) { // 加载战斗状态
        try { // 捕获反序列化异常
            String json = stringRedisTemplate.opsForValue().get(REDIS_STATE + battleId); // 读取 JSON
            if (json == null) { // 键不存在或已过期
                return null;
            }
            return objectMapper.readValue(json, BattleRuntimeState.class); // JSON 转对象
        } catch (Exception e) { // 解析失败
            return null; // 视为无此战斗（不向外抛错，由上层返回 BATTLE_NOT_FOUND）
        }
    }

    /**
     * 战斗结束后删除状态键。
     *
     * @param battleId 战斗 ID
     */
    private void deleteState(long battleId) { // 删除战斗状态
        stringRedisTemplate.delete(REDIS_STATE + battleId); // 删除 Redis 键
    }

    /**
     * 将内部数值组装为协议 Protobuf 实体信息。
     *
     * @return BattleEntityInfo 消息
     */
    private static BattleEntityInfo buildEntityInfo( // 构建实体信息
            long entityId, String name, int level,
            int hp, int hpMax, int mp, int mpMax, int atk, int def) {
        return BattleEntityInfo.newBuilder() // Protobuf 建造者模式
                .setEntityId(entityId) // 实体 ID
                .setName(name == null ? "" : name) // 空名用空串
                .setLevel(level) // 等级
                .setHp(hp) // 当前 HP
                .setHpMax(hpMax) // 最大 HP
                .setMp(mp) // 当前 MP
                .setMpMax(mpMax) // 最大 MP
                .setAttack(atk) // 攻击力
                .setDefense(def) // 防御力
                .build(); // 生成不可变消息对象
    }

    /**
     * 根据等级计算 [最大HP, 最大MP, 攻击, 防御]。
     *
     * @param level 玩家等级
     * @return 四维属性数组
     */
    private static int[] playerCombatStats(int level) { // 计算玩家战斗属性
        int lv = Math.max(1, level); // 等级至少为 1
        return new int[]{ // 返回四维数组
                100 + lv * 50, // 最大 HP
                lv * 10, // 最大 MP
                50 + lv * 5, // 攻击力
                20 + lv * 2 // 防御力
        };
    }

    /**
     * 组装开始战斗结果：成功时带 battleId、双方属性与 sceneId。
     *
     * @return 协议消息
     */
    private static ProtocolMessage startRsp( // 开始战斗响应
            int code, long battleId, BattleEntityInfo enemy, BattleEntityInfo player, int sceneId) {
        var b = BattleStartScRsp.newBuilder().
                setRetcode(code).
                setServerTime(System.currentTimeMillis() / 1000); // 客户端可对时
        if (code == RetCode.OK) { // 仅成功时填充战斗数据
            b.setBattleId(battleId)
             .setSceneId(sceneId)
             .setEnemyInfo(enemy)
             .setPlayerInfo(player); // 填充战斗信息
        }
        return new ProtocolMessage(MessageId.BATTLE_START_SC_RSP, b.build().toByteArray()); // 供客户端进入战斗 UI
    }

    /**
     * 组装回合行动结果。
     *
     * @return 协议消息
     */
    private static ProtocolMessage actionRsp(int code, long battleId, long actionId, int damage, int heal) { // 行动响应
        var b = BattleActionScRsp.newBuilder()
                .setRetcode(code)
                .setBattleId(battleId)
                .setActionId(actionId); // 基础字段
        if (damage > 0) { // 有伤害才设置字段（Protobuf 可选语义）
            b.setDamage(damage); // 设置伤害
        }
        if (heal > 0) { // 有治疗才设置
            b.setHeal(heal); // 设置治疗
        }
        return new ProtocolMessage(MessageId.BATTLE_ACTION_SC_RSP, b.build().toByteArray()); // 供客户端播放伤害/治疗表现
    }

    /**
     * 组装战斗结算：经验、货币与掉落道具。
     *
     * @return 协议消息
     */
    private static ProtocolMessage endRsp( // 结束战斗响应
            int code, long battleId, int exp, java.util.Map<Integer, Integer> currency, java.util.List<ItemReward> items) {
        var b = BattleEndScRsp.newBuilder()
                .setRetcode(code)
                .setBattleId(battleId); // 基础字段
        if (code == RetCode.OK) { // 成功才带奖励
            if (exp > 0) { // 有经验
                b.setPlayerExp(exp); // 设置经验
            }
            if (currency != null) { // 有货币
                b.putAllCurrencyRewards(currency); // 批量放入货币 map
            }
            if (items != null) { // 有道具
                b.addAllItemRewards(items); // 道具列表
            }
        }
        return new ProtocolMessage(MessageId.BATTLE_END_SC_RSP, b.build().toByteArray()); // 供背包与等级系统入账
    }

    /**
     * Redis 持久化的战斗运行时状态（Jackson 序列化）。
     */
    public static class BattleRuntimeState { // 单场战斗在 Redis 中的完整快照

        /** 战斗唯一 ID */
        public long battleId; // 战斗 ID
        /** 参与玩家 ID */
        public long playerId; // 玩家 ID
        /** 所在场景 ID */
        public int sceneId; // 场景 ID
        /** 阵容 ID */
        public long lineupId; // 阵容 ID
        /** 战斗类型（PVE/PVP 等，由协议定义） */
        public int battleType; // 战斗类型
        /** 场景中敌人实体实例 ID */
        public long enemyEntityId; // 敌人实体 ID
        /** 怪物配置模板 ID */
        public int monsterTemplateId; // 怪物模板 ID
        /** 胜利可获得的经验（来自配置） */
        public int expReward; // 经验奖励
        /** 玩家显示名 */
        public String playerName; // 玩家名称
        /** 敌人显示名 */
        public String enemyName; // 敌人名称
        /** 玩家等级 */
        public int playerLevel; // 玩家等级
        /** 敌人等级 */
        public int enemyLevel; // 敌人等级
        /** 玩家当前生命值 */
        public int playerHp; // 玩家当前 HP
        /** 玩家最大生命值 */
        public int playerHpMax; // 玩家最大 HP
        /** 玩家当前法力值 */
        public int playerMp; // 玩家当前 MP
        /** 玩家最大法力值 */
        public int playerMpMax; // 玩家最大 MP
        /** 玩家攻击力 */
        public int playerAttack; // 玩家攻击
        /** 玩家防御力 */
        public int playerDefense; // 玩家防御
        /** 敌人当前生命值 */
        public int enemyHp; // 敌人当前 HP
        /** 敌人最大生命值 */
        public int enemyHpMax; // 敌人最大 HP
        /** 敌人当前法力值 */
        public int enemyMp; // 敌人当前 MP
        /** 敌人最大法力值 */
        public int enemyMpMax; // 敌人最大 MP
        /** 敌人攻击力 */
        public int enemyAttack; // 敌人攻击
        /** 敌人防御力 */
        public int enemyDefense; // 敌人防御
        /** 下一条客户端行动序号（递增） */
        public long nextActionId; // 行动序号
        /** 是否已在服务端判定结束（尚未调用 end 结算） */
        public boolean ended; // 结束标志
    }
}
