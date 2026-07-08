/**
 * 文件说明：Buff 核心业务服务类。
 * 职责：管理 Buff 配置（buff_config / JPA + 缓存）与运行时状态（Redis），处理协议 401~407；
 *       配置了 periodic_interval 的 Buff 通过 PeriodicBuff 独立定时结算，不依赖场景 Tick。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 配置实体
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议消息 ID
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议消息
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 返回码
import cn.itcast.demo.mymmorpg.protocol.protobuf.BuffAddScNotify; // Buff 添加推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.BuffRemoveScNotify; // Buff 移除推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.BuffRuntimeInfo; // Buff 运行时信息
import cn.itcast.demo.mymmorpg.protocol.protobuf.BuffUpdateScNotify; // Buff 更新推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetEntityBuffsCsReq; // 查询实体 Buff 请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetEntityBuffsScRsp; // 查询实体 Buff 响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.RemoveBuffCsReq; // 移除 Buff 请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.RemoveBuffScRsp; // 移除 Buff 响应
import cn.itcast.demo.mymmorpg.model.PeriodicBuff; // 周期性 Buff 抽象
import cn.itcast.demo.mymmorpg.model.PeriodicBuffRegistry; // 周期 Buff 注册表
import cn.itcast.demo.mymmorpg.model.PeriodicBuffRuntime; // 周期 Buff 运行时接口
import cn.itcast.demo.mymmorpg.service.BuffEventPublisher; // Buff 事件发布器
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // 玩家仓库
import cn.itcast.demo.mymmorpg.support.BuffPolicy; // Buff 规则策略
import cn.itcast.demo.mymmorpg.port.BattleScenePort; // 战斗场景端口
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // 玩家通知端口
import cn.itcast.demo.mymmorpg.service.ConfigQueryService; // 配置查询服务
import com.fasterxml.jackson.core.type.TypeReference; // Jackson 类型引用
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 映射器
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.data.redis.core.StringRedisTemplate; // Redis 模板
import org.springframework.stereotype.Service; // Spring 服务

import java.util.ArrayList; // 可变列表
import java.util.Iterator; // 迭代器
import java.util.List; // 列表接口
import java.util.Objects; // 对象工具

/**
 * Buff 配置（buff_config / JPA + 缓存）与运行时状态（Redis），协议 401~407。
 * 配置了 {@link BuffConfig#getPeriodicInterval()} 的 Buff 由 {@link PeriodicBuff} 独立定时结算，不依赖场景 Tick。
 */
@Service // 注册为 Spring 服务
public class BuffService implements PeriodicBuffRuntime { // Buff 核心业务，同时实现周期运行时接口

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(BuffService.class); // 日志

    /** 移除原因：过期 */
    public static final int REMOVE_REASON_EXPIRE = 0; // 过期移除
    /** 移除原因：驱散 */
    public static final int REMOVE_REASON_DISPEL = 1; // 驱散移除
    /** 移除原因：替换 */
    public static final int REMOVE_REASON_REPLACE = 2; // 替换移除
    /** 移除原因：主动取消 */
    public static final int REMOVE_REASON_CANCEL = 3; // 主动取消

    /** Redis 键前缀：Buff 运行时状态 */
    private static final String REDIS_KEY = "buff:runtime:"; // Buff Redis 键前缀
    /** 永久 Buff 的过期时间戳标记 */
    private static final long PERMANENT_EXPIRE = -1L; // 永久标记

    /** 配置查询服务 */
    private final ConfigQueryService configQueryService; // 查 Buff 配置
    /** 战斗场景端口 */
    private final BattleScenePort battleScenePort; // 场景可见性
    /** 玩家数据仓库 */
    private final PlayerRepository playerRepository; // 玩家存在性校验
    /** Redis 字符串模板 */
    private final StringRedisTemplate stringRedisTemplate; // Redis 操作
    /** JSON 对象映射器 */
    private final ObjectMapper objectMapper; // 状态序列化
    /** Buff 事件发布器 */
    private final BuffEventPublisher buffEventPublisher; // MQ 事件
    /** Buff 规则策略 */
    private final BuffPolicy buffPolicy; // 施加/移除权限
    /** 玩家通知端口 */
    private final PlayerNotificationPort playerNotificationPort; // 推送通知
    /** 周期性 Buff 注册表 */
    private final PeriodicBuffRegistry periodicBuffRegistry; // 周期任务管理

    /**
     * 构造器注入所有依赖。
     */
    public BuffService(
            ConfigQueryService configQueryService,
            BattleScenePort battleScenePort,
            PlayerRepository playerRepository,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            BuffEventPublisher buffEventPublisher,
            BuffPolicy buffPolicy,
            PlayerNotificationPort playerNotificationPort,
            PeriodicBuffRegistry periodicBuffRegistry) {
        this.configQueryService = configQueryService; // 保存配置服务
        this.battleScenePort = battleScenePort; // 保存场景端口
        this.playerRepository = playerRepository; // 保存玩家仓库
        this.stringRedisTemplate = stringRedisTemplate; // 保存 Redis 模板
        this.objectMapper = objectMapper; // 保存 JSON 映射器
        this.buffEventPublisher = buffEventPublisher; // 保存事件发布器
        this.buffPolicy = buffPolicy; // 保存 Buff 策略
        this.playerNotificationPort = playerNotificationPort; // 保存通知端口
        this.periodicBuffRegistry = periodicBuffRegistry; // 保存周期注册表
    }

    @Override
    public boolean refreshStateForPeriodicTick(long entityId, int buffId, PeriodicBuff periodicBuff) { // 周期 tick 前刷新状态
        long now = System.currentTimeMillis(); // 当前时间戳
        List<ActiveBuffEntry> list = loadAndPrune(entityId, now); // 加载并清理过期 Buff
        int idx = findIndex(list, buffId); // 查找目标 Buff 索引
        if (idx < 0) { // Buff 不存在
            return false; // 判定为离线或不可用
        }
        periodicBuff.setStackCount(list.get(idx).stackCount); // 同步层数到周期实例
        return true; // Buff 仍有效
    }

    /**
     * 处理查询实体 Buff 列表请求。
     *
     * @param requesterPlayerId 请求者玩家 ID
     * @param req               查询请求
     * @return 协议响应
     */
    public ProtocolMessage handleGetEntityBuffs(long requesterPlayerId, GetEntityBuffsCsReq req) { // 查询实体 Buff
        if (requesterPlayerId <= 0) { // 未选角
            return getEntityBuffsRsp(RetCode.PLAYER_NOT_SELECTED, 0, List.of()); // 返回未选角
        }
        long entityId = req.getEntityId() == 0 ? requesterPlayerId : req.getEntityId(); // 默认查自己
        if (entityId != requesterPlayerId && !canSeeEntityBuffs(requesterPlayerId, entityId)) { // 查他人需可见
            return getEntityBuffsRsp(RetCode.BUFF_ENTITY_NOT_VISIBLE, entityId, List.of()); // 不可见
        }
        long now = System.currentTimeMillis(); // 当前时间
        List<ActiveBuffEntry> entries = loadAndPrune(entityId, now); // 加载并清理
        List<BuffRuntimeInfo> infos = new ArrayList<>(); // 结果列表
        for (ActiveBuffEntry e : entries) { // 遍历活跃 Buff
            BuffConfig cfg = configQueryService.findBuffById(e.buffId); // 查配置
            if (cfg == null) { // 配置不存在
                continue; // 跳过
            }
            infos.add(toRuntimeInfo(cfg, e, now)); // 转为协议信息
        }
        return getEntityBuffsRsp(RetCode.OK, entityId, infos); // 返回成功
    }

    /**
     * 处理移除 Buff 请求。
     *
     * @param requesterPlayerId 请求者玩家 ID
     * @param req               移除请求
     * @return 协议响应
     */
    public ProtocolMessage handleRemoveBuff(long requesterPlayerId, RemoveBuffCsReq req) { // 移除 Buff
        if (requesterPlayerId <= 0) { // 未选角
            return removeBuffRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0); // 返回未选角
        }
        long targetId = req.getTargetEntityId() == 0 ? requesterPlayerId : req.getTargetEntityId(); // 默认移除自己
        int buffId = req.getBuffId(); // Buff 模板 ID
        if (targetId != requesterPlayerId && !canSeeEntityBuffs(requesterPlayerId, targetId)) { // 移除他人 Buff 需可见
            return removeBuffRsp(RetCode.BUFF_ENTITY_NOT_VISIBLE, targetId, buffId); // 不可见
        }
        if (!buffPolicy.canRemoveBuff(requesterPlayerId, targetId, buffId)) { // 策略校验
            return removeBuffRsp(RetCode.BUFF_CANNOT_REMOVE, targetId, buffId); // 不允许移除
        }
        BuffConfig cfg = configQueryService.findBuffById(buffId); // 查配置
        if (cfg == null) { // 配置不存在
            return removeBuffRsp(RetCode.BUFF_NOT_FOUND, targetId, buffId); // Buff 未找到
        }
        int rc = removeBuffInternal(targetId, buffId, REMOVE_REASON_CANCEL); // 内部移除
        if (rc != RetCode.OK) { // 移除失败
            return removeBuffRsp(rc, targetId, buffId); // 返回错误码
        }
        return removeBuffRsp(RetCode.OK, targetId, buffId); // 返回成功
    }

    /**
     * 技能或其它系统为实体施加 Buff（如 CastSkill 效果中的 buff_id）。
     *
     * @param entityId 实体 ID
     * @param buffId   Buff 模板 ID
     */
    public void applyBuff(long entityId, int buffId) { // 施加 Buff
        if (entityId <= 0 || buffId <= 0) { // 参数无效
            return; // 直接返回
        }
        if (!buffPolicy.canApplyBuff(entityId, buffId)) { // 策略不允许
            return; // 直接返回
        }
        BuffConfig cfg = configQueryService.findBuffById(buffId); // 查配置
        if (cfg == null) { // 配置不存在
            log.debug("applyBuff 忽略：buff_config 不存在 buffId={}", buffId); // 记录调试日志
            return; // 忽略
        }
        long now = System.currentTimeMillis(); // 当前时间
        List<ActiveBuffEntry> list = loadAndPrune(entityId, now); // 加载并清理
        int idx = findIndex(list, buffId); // 查找已有 Buff
        boolean isNew = idx < 0; // 是否新 Buff
        int stackLimit = Math.max(1, cfg.getStackLimit() == null ? 1 : cfg.getStackLimit()); // 层数上限
        if (isNew) { // 新 Buff
            var e = new ActiveBuffEntry(); // 新建条目
            e.buffId = buffId; // 设置 Buff ID
            e.stackCount = 1; // 初始 1 层
            e.expireAtMs = computeExpireAt(cfg, now); // 计算过期时间
            list.add(e); // 加入列表
            persist(entityId, list); // 持久化
            var info = toRuntimeInfo(cfg, e, now); // 转协议信息
            buffEventPublisher.publishBuffAdded(entityId, buffId, e.stackCount); // 发布添加事件
            pushBuffAdd(entityId, info); // 推送客户端通知
        } else { // 已有 Buff，叠层
            ActiveBuffEntry e = list.get(idx); // 取已有条目
            e.stackCount = Math.min(stackLimit, e.stackCount + 1); // 层数加 1，不超过上限
            e.expireAtMs = computeExpireAt(cfg, now); // 刷新过期时间
            persist(entityId, list); // 持久化
            buffEventPublisher.publishBuffUpdated(entityId, buffId, remainingMs(e, now), e.stackCount); // 发布更新事件
            pushBuffUpdate(entityId, buffId, remainingMs(e, now), e.stackCount); // 推送更新通知
        }
    }

    /**
     * 供定时器/驱散调用：按模板 ID 移除 Buff。
     *
     * @param entityId 实体 ID
     * @param buffId   Buff 模板 ID
     * @param reason   移除原因码
     * @return 返回码
     */
    public int removeBuffInternal(long entityId, int buffId, int reason) { // 内部移除 Buff
        long now = System.currentTimeMillis(); // 当前时间
        List<ActiveBuffEntry> list = loadAndPrune(entityId, now); // 加载并清理
        int idx = findIndex(list, buffId); // 查找索引
        if (idx < 0) { // 不存在
            return RetCode.BUFF_NOT_ON_ENTITY; // 实体上没有该 Buff
        }
        list.remove(idx); // 从列表移除
        persist(entityId, list); // 持久化
        buffEventPublisher.publishBuffRemoved(entityId, buffId, reason); // 发布移除事件
        pushBuffRemove(entityId, buffId, reason); // 推送移除通知
        return RetCode.OK; // 成功
    }

    /**
     * 判断请求者是否可查看目标实体的 Buff。
     *
     * @param requesterPlayerId 请求者
     * @param targetEntityId    目标实体
     * @return 是否可见
     */
    private boolean canSeeEntityBuffs(long requesterPlayerId, long targetEntityId) { // 可见性校验
        if (!battleScenePort.isPlayerInScene(requesterPlayerId)) { // 请求者不在场景
            return false; // 不可见
        }
        return battleScenePort.getEntityTypeInLine(requesterPlayerId, targetEntityId).isPresent(); // 同场景同视线
    }

    /**
     * 从 Redis 加载 Buff 列表并清理过期条目。
     *
     * @param entityId 实体 ID
     * @param nowMs    当前毫秒时间戳
     * @return 活跃 Buff 列表
     */
    private List<ActiveBuffEntry> loadAndPrune(long entityId, long nowMs) { // 加载并清理过期
        String key = REDIS_KEY + entityId; // Redis 键
        String json = stringRedisTemplate.opsForValue().get(key); // 读取 JSON
        List<ActiveBuffEntry> list = new ArrayList<>(); // 结果列表
        if (json != null && !json.isBlank()) { // 有数据
            try { // 解析 JSON
                list = objectMapper.readValue(json, new TypeReference<>() {}); // 反序列化
            } catch (Exception e) { // 解析失败
                log.warn("解析 Buff Redis 失败 entityId={}", entityId, e); // 记录警告
            }
        }
        boolean changed = false; // 是否有变更
        Iterator<ActiveBuffEntry> it = list.iterator(); // 迭代器
        while (it.hasNext()) { // 遍历
            ActiveBuffEntry e = it.next(); // 取条目
            if (e.expireAtMs != PERMANENT_EXPIRE && e.expireAtMs <= nowMs) { // 已过期
                it.remove(); // 移除
                changed = true; // 标记变更
                buffEventPublisher.publishBuffRemoved(entityId, e.buffId, REMOVE_REASON_EXPIRE); // 发布过期移除
                pushBuffRemove(entityId, e.buffId, REMOVE_REASON_EXPIRE); // 推送移除通知
            }
        }
        if (changed) { // 有清理
            persist(entityId, list); // 回写 Redis
        }
        return list; // 返回列表
    }

    /**
     * 将 Buff 列表持久化到 Redis 并同步周期任务。
     *
     * @param entityId 实体 ID
     * @param list     Buff 条目列表
     */
    private void persist(long entityId, List<ActiveBuffEntry> list) { // 持久化 Buff 列表
        try { // 捕获序列化异常
            String json = objectMapper.writeValueAsString(list); // 序列化为 JSON
            stringRedisTemplate.opsForValue().set(REDIS_KEY + entityId, json); // 写入 Redis
        } catch (Exception e) { // 写入失败
            log.error("写入 Buff Redis 失败 entityId={}", entityId, e); // 记录错误
        }
        periodicBuffRegistry.syncEntityBuffs(entityId, list); // 同步周期 Buff 任务
    }

    /**
     * 在列表中查找指定 buffId 的索引。
     *
     * @return 索引，不存在返回 -1
     */
    private static int findIndex(List<ActiveBuffEntry> list, int buffId) { // 查找 Buff 索引
        for (int i = 0; i < list.size(); i++) { // 线性查找
            if (list.get(i).buffId == buffId) { // 匹配
                return i; // 返回索引
            }
        }
        return -1; // 未找到
    }

    /**
     * 根据配置计算 Buff 过期时间戳。
     *
     * @param cfg    Buff 配置
     * @param nowMs  当前毫秒时间戳
     * @return 过期时间戳，永久返回 PERMANENT_EXPIRE
     */
    private long computeExpireAt(BuffConfig cfg, long nowMs) { // 计算过期时间
        int dur = cfg.getDuration() == null ? -1 : cfg.getDuration(); // 持续时间（毫秒）
        if (dur < 0) { // 负数表示永久
            return PERMANENT_EXPIRE; // 永久标记
        }
        return nowMs + dur; // 当前时间 + 持续时间
    }

    /**
     * 计算 Buff 剩余毫秒数。
     *
     * @param e     Buff 条目
     * @param nowMs 当前毫秒时间戳
     * @return 剩余毫秒，永久返回 -1
     */
    private long remainingMs(ActiveBuffEntry e, long nowMs) { // 计算剩余时间
        if (e.expireAtMs == PERMANENT_EXPIRE) { // 永久 Buff
            return -1L;
        }
        return Math.max(0L, e.expireAtMs - nowMs); // 剩余时间，不低于 0
    }

    /**
     * 将内部条目转为协议 BuffRuntimeInfo。
     *
     * @return Protobuf 消息
     */
    private BuffRuntimeInfo toRuntimeInfo(BuffConfig cfg, ActiveBuffEntry e, long nowMs) { // 转协议信息
        var b = BuffRuntimeInfo.newBuilder() // 建造者
                .setBuffId(e.buffId) // Buff ID
                .setBuffName(Objects.toString(cfg.getName(), "")) // 名称
                .setRemainingTime(remainingMs(e, nowMs)) // 剩余时间
                .setStackCount(e.stackCount) // 层数
                .setEffectType(cfg.getEffectType() == null ? 0 : cfg.getEffectType()); // 效果类型
        if (cfg.getEffectParams() != null) { // 有效果参数
            b.setEffectParams(cfg.getEffectParams()); // 设置参数
        }
        if (cfg.getDescription() != null) { // 有描述
            b.setDescription(cfg.getDescription()); // 设置描述
        }
        return b.build(); // 构建消息
    }

    /**
     * 向玩家推送 Buff 添加通知。
     */
    private void pushBuffAdd(long entityId, BuffRuntimeInfo info) { // 推送添加通知
        if (!playerRepository.existsById(entityId)) { // 非玩家实体
            return; // 不推送
        }
        var n = BuffAddScNotify.newBuilder().
                setEntityId(entityId).
                setBuffInfo(info)
                .build(); // 构建通知
        playerNotificationPort.send(entityId, MessageId.BUFF_ADD_SC_NOTIFY, n.toByteArray()); // 发送
    }

    /**
     * 向玩家推送 Buff 更新通知。
     */
    private void pushBuffUpdate(long entityId, int buffId, long remainingMs, int stackCount) { // 推送更新通知
        if (!playerRepository.existsById(entityId)) { // 非玩家实体
            return; // 不推送
        }
        var b = BuffUpdateScNotify.newBuilder()
                .setEntityId(entityId)
                .setBuffId(buffId)
                .setRemainingTime(remainingMs) // 剩余时间
                .setStackCount(stackCount)// 层数
                .build();
        playerNotificationPort.send(entityId, MessageId.BUFF_UPDATE_SC_NOTIFY, b.toByteArray()); // 发送
    }

    /**
     * 向玩家推送 Buff 移除通知。
     */
    private void pushBuffRemove(long entityId, int buffId, int reason) { // 推送移除通知
        if (!playerRepository.existsById(entityId)) { // 非玩家实体
            return; // 不推送
        }
        var n = BuffRemoveScNotify.newBuilder() // 建造者
                .setEntityId(entityId) // 实体 ID
                .setBuffId(buffId) // Buff ID
                .setReason(reason) // 移除原因
                .build(); // 构建
        playerNotificationPort.send(entityId, MessageId.BUFF_REMOVE_SC_NOTIFY, n.toByteArray()); // 发送
    }

    /**
     * 组装查询实体 Buff 响应。
     *
     * @return 协议消息
     */
    private static ProtocolMessage getEntityBuffsRsp(int code, long entityId, List<BuffRuntimeInfo> buffs) { // 查询响应
        var b = GetEntityBuffsScRsp.newBuilder()
                .setRetcode(code)
                .setEntityId(entityId); // 基础字段
        if (code == RetCode.OK) { // 成功
            b.addAllBuffs(buffs); // 添加 Buff 列表
        }
        return new ProtocolMessage(MessageId.GET_ENTITY_BUFFS_SC_RSP, b.build().toByteArray()); // 返回消息
    }

    /**
     * 组装移除 Buff 响应。
     *
     * @return 协议消息
     */
    private static ProtocolMessage removeBuffRsp(int code, long targetEntityId, int buffId) { // 移除响应
        var rsp = RemoveBuffScRsp.newBuilder() // 建造者
                .setRetcode(code) // 返回码
                .setTargetEntityId(targetEntityId) // 目标实体
                .setBuffId(buffId) // Buff ID
                .build(); // 构建
        return new ProtocolMessage(MessageId.REMOVE_BUFF_SC_RSP, rsp.toByteArray()); // 返回消息
    }

    /**
     * Redis 中存储的单实体 Buff 条目（JSON 序列化）。
     */
    public static final class ActiveBuffEntry { // 活跃 Buff 条目

        /** Buff 模板 ID */
        public int buffId; // Buff ID
        /** 当前层数，默认 1 */
        public int stackCount = 1; // 层数
        /** 过期时间戳（毫秒）；-1 表示永久 */
        public long expireAtMs = PERMANENT_EXPIRE; // 过期时间
    }
}
