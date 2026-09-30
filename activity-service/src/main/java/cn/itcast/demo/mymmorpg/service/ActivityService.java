/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/service/ActivityService.java
 * 2) 所属模块：activity-service / service
 * 3) 主要职责：活动核心业务——列表(801/802)、详情(806/807)、领奖(803/804)，整合 MySQL、Redis、背包与 MQ
 * 4) 系统位置：活动微服务业务层，被 InternalActivityController 与 ActivityServiceJmx 调用
 * 5) 风险提示：领奖流程含事务；依赖 ActivityPlayerProgressStore、ActivityItemGrantPort 等外部端口
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.activity.ActivitySnapshotManager;
import cn.itcast.demo.mymmorpg.activity.PostMortemProcessor;
import cn.itcast.demo.mymmorpg.model.ActivityConditionEvaluator;
import cn.itcast.demo.mymmorpg.model.RewardTierPayload; // 单档奖励配置模型
import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress; // 玩家活动进度模型
import cn.itcast.demo.mymmorpg.entity.Activity; // MySQL activity 表实体
import cn.itcast.demo.mymmorpg.protocol.*; // 协议常量与返回码
import cn.itcast.demo.mymmorpg.protocol.protobuf.ActivityBriefInfo; // Protobuf 807 活动简要信息
import cn.itcast.demo.mymmorpg.protocol.protobuf.ActivityStatusScNotify; // Protobuf 活动状态推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq; // Protobuf 领奖请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardScRsp; // Protobuf 领奖响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq; // Protobuf 详情请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailScRsp; // Protobuf 详情响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // Protobuf 列表请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListScRsp; // Protobuf 列表响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward; // Protobuf 道具奖励
import cn.itcast.demo.mymmorpg.protocol.protobuf.RewardStatus; // Protobuf 档位领奖状态
import cn.itcast.demo.mymmorpg.repository.ActivityRepository; // 活动 JPA 仓库
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // 玩家 JPA 仓库
import cn.itcast.demo.mymmorpg.support.ActivityPolicy; // Groovy/可扩展领奖策略
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort; // 背包发道具端口
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort; // 玩家数据预热端口
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // 玩家通知推送端口
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson JSON 序列化
import com.fasterxml.jackson.databind.node.ObjectNode; // Jackson 可变 JSON 节点
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value; // 注入配置属性
import org.springframework.stereotype.Service; // 注册 Spring Service Bean
import org.springframework.transaction.annotation.Transactional; // 声明式事务

import java.util.ArrayList; // 可变列表
import java.util.Comparator; // 比较器，用于按 activityId 排序
import java.util.HashMap; // 哈希 Map，策略上下文
import java.util.List; // 列表接口
import java.util.Map; // Map 接口
import java.util.Objects; // 空安全工具
import java.util.Optional; // 可选返回值

/**
 * 活动核心服务：MySQL activity 表 + Redis 玩家进度，处理 801/806/803 等协议。
 */
@Service // 注册为 Spring Bean，供 Controller 注入
public class ActivityService { // 活动业务核心类

    /** 活动状态：未开始。 */
    public static final int STATUS_NOT_STARTED = 0; // 当前时间早于 startTime
    /** 活动状态：进行中。 */
    public static final int STATUS_IN_PROGRESS = 1; // 当前时间在 startTime 与 endTime 之间
    /** 活动状态：已结束。 */
    public static final int STATUS_ENDED = 2; // 当前时间晚于 endTime

    /** 一天的毫秒数，用于计算活动内第几天。 */
    private static final long DAY_MS = 86_400_000L; // 24 * 60 * 60 * 1000

    /** 活动持久化仓库。 */
    private final ActivityRepository activityRepository; // 读写 activity 表
    /** 玩家持久化仓库。 */
    private final PlayerRepository playerRepository; // 校验玩家是否存在
    /** Redis 玩家进度存储。 */
    private final ActivityPlayerProgressStore progressStore; // 读写 Redis 进度
    /** 背包发道具端口。 */
    private final ActivityItemGrantPort activityItemGrantPort; // 领奖时发放道具
    /** 发奖履约（Outbox 优先）。 */
    private final ObjectProvider<ActivityRewardFulfillmentService> rewardFulfillmentService;
    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper; // 解析 activity.data 与 detail JSON
    /** 可扩展领奖策略。 */
    private final ActivityPolicy activityPolicy; // Groovy/Java 扩展校验
    /** 活动事件发布器（MQ 或空实现）。 */
    private final ActivityEventPublisher activityEventPublisher; // 领奖后发 MQ 事件
    /** 玩家通知推送端口。 */
    private final PlayerNotificationPort playerNotificationPort; // 广播 807 状态通知
    /** 玩家数据预热加载端口。 */
    private final PlayerDataLoadPort playerDataLoadPort; // 判断 ACTIVITY 数据是否就绪
    /** 是否启用玩家数据预加载（未就绪时返回 loading）。 */
    private final boolean preloadEnabled; // game.player-preload.enabled 配置

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ActivitySnapshotManager activitySnapshotManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PostMortemProcessor postMortemProcessor;

    /**
     * 单测兼容构造器。
     */
    public ActivityService(
            ActivityRepository activityRepository,
            PlayerRepository playerRepository,
            ActivityPlayerProgressStore progressStore,
            ActivityItemGrantPort activityItemGrantPort,
            ObjectMapper objectMapper,
            ActivityPolicy activityPolicy,
            ActivityEventPublisher activityEventPublisher,
            PlayerNotificationPort playerNotificationPort,
            PlayerDataLoadPort playerDataLoadPort,
            @Value("${game.player-preload.enabled:false}") boolean preloadEnabled) {
        this(activityRepository, playerRepository, progressStore, activityItemGrantPort,
                null, objectMapper, activityPolicy, activityEventPublisher,
                playerNotificationPort, playerDataLoadPort, preloadEnabled);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ActivityService(
            ActivityRepository activityRepository,
            PlayerRepository playerRepository,
            ActivityPlayerProgressStore progressStore,
            ActivityItemGrantPort activityItemGrantPort,
            ObjectProvider<ActivityRewardFulfillmentService> rewardFulfillmentService,
            ObjectMapper objectMapper,
            ActivityPolicy activityPolicy,
            ActivityEventPublisher activityEventPublisher,
            PlayerNotificationPort playerNotificationPort,
            PlayerDataLoadPort playerDataLoadPort,
            @Value("${game.player-preload.enabled:false}") boolean preloadEnabled) {
        this.activityRepository = activityRepository;
        this.playerRepository = playerRepository;
        this.progressStore = progressStore;
        this.activityItemGrantPort = activityItemGrantPort;
        this.rewardFulfillmentService = rewardFulfillmentService;
        this.objectMapper = objectMapper;
        this.activityPolicy = activityPolicy;
        this.activityEventPublisher = activityEventPublisher;
        this.playerNotificationPort = playerNotificationPort;
        this.playerDataLoadPort = playerDataLoadPort;
        this.preloadEnabled = preloadEnabled;
    }

    /**
     * 处理获取活动列表请求；预热未就绪时返回 loading，否则立即加载列表。
     */
    public ProtocolMessage handleGetActivityList(long playerId, GetActivityListCsReq req) {
        // 启用预加载且玩家 ACTIVITY 数据未就绪时，先返回 loading 响应
        if (preloadEnabled && playerId > 0
                && !playerDataLoadPort.isReady(playerId, PlayerDataLoadPort.DataType.ACTIVITY)) {
            return listLoadingRsp();
        } // if 预加载未就绪 结束
        return loadActivityListNow(playerId); // 数据就绪，立即查库组装列表
    } // handleGetActivityList 结束

    /**
     * 从 DB 与 Redis 加载并组装玩家可见的活动简要列表。
     */
    public ProtocolMessage loadActivityListNow(long playerId) {
        if (playerId <= 0) { // 未选择角色或会话无效
            return listRsp(RetCode.PLAYER_NOT_SELECTED, List.of()); // 未选角色
        } // if playerId 无效 结束
        if (playerRepository.findById(playerId).isEmpty()) {
            return listRsp(RetCode.PLAYER_NOT_FOUND, List.of()); // 玩家不存在
        } // if 玩家不存在 结束
        long now = System.currentTimeMillis(); // 当前时间戳，用于三态与可见性判断
        List<ActivityBriefInfo> out = new ArrayList<>(); // 输出列表
        for (Activity a : activityRepository.findByOpenedTrue()) { // 遍历所有已开启活动
            ActivityConfigPayload cfg = parseConfig(a); // 解析 data JSON 配置
            PlayerActivityProgress prog = progressStore.loadOrCreate(playerId, a.getId()); // 加载或创建玩家进度
            if (!visibleInList(a, cfg, now, prog, playerId)) {
                continue; // 不可见则跳过
            } // if 不可见 结束
            int st = computeStatus(now, cfg); // 计算活动三态
            out.add(ActivityBriefInfo.newBuilder()
                    .setActivityId(a.getId())
                    .setType(a.getType() == null ? 0 : a.getType())
                    .setName(cfg.name != null ? cfg.name : "")
                    .setStatus(st)
                    .setStartTime(cfg.startTime)
                    .setEndTime(cfg.endTime)
                    .setBriefDesc(cfg.briefDesc != null ? cfg.briefDesc : "")
                    .build()); // 组装一条 ActivityBriefInfo
        } // for 活动 结束
        out.sort(Comparator.comparingLong(ActivityBriefInfo::getActivityId)); // 按 activityId 升序排序
        return listRsp(ActivityRetCode.OK, out); // 返回成功列表
    } // loadActivityListNow 结束

    /**
     * 处理获取活动详情请求；预热未就绪时返回 loading，否则填充 detail JSON 与档位状态。
     */
    public ProtocolMessage handleGetActivityDetail(long playerId, GetActivityDetailCsReq req) {
        if (preloadEnabled && playerId > 0
                && !playerDataLoadPort.isReady(playerId, PlayerDataLoadPort.DataType.ACTIVITY)) {
            return detailLoadingRsp(req.getActivityId()); // 占位 + loading=true
        } // if 预加载未就绪 结束
        if (playerId <= 0) { // 未选择角色或会话无效
            return detailRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, "", List.of()); // 未选角色
        } // if 玩家无效 结束
        if (playerRepository.findById(playerId).isEmpty()) {
            return detailRsp(RetCode.PLAYER_NOT_FOUND, 0, 0, 0, "", List.of()); // 玩家不存在
        } // if 玩家不存在 结束
        if (activitySnapshotManager != null) {
            activitySnapshotManager.trackActivePlayer(playerId);
        }
        long aid = req.getActivityId(); // 请求的活动 ID
        Optional<Activity> opt = activityRepository.findById(aid); // 查库
        if (opt.isEmpty()) { // 数据库中无对应记录
            return detailRsp(ActivityRetCode.ACTIVITY_NOT_FOUND, aid, 0, 0, "", List.of()); // 活动不存在
        } // if 活动不存在 结束
        Activity a = opt.get(); // 取出活动实体
        if (!Boolean.TRUE.equals(a.getOpened())) {
            return detailRsp(ActivityRetCode.ACTIVITY_CLOSED, aid, a.getType() == null ? 0 : a.getType(), 0, "", List.of()); // 活动已关闭
        } // if 未开启 结束
        ActivityConfigPayload cfg = parseConfig(a); // 解析配置
        if (!isPublishedForPlayer(cfg, playerId)) {
            return detailRsp(ActivityRetCode.ACTIVITY_CLOSED, aid, a.getType() == null ? 0 : a.getType(), 0, "", List.of());
        }
        long now = System.currentTimeMillis(); // 当前时间
        int st = computeStatus(now, cfg); // 活动三态
        PlayerActivityProgress p = progressStore.loadOrCreate(playerId, aid); // 玩家进度
        String detailJson = buildDetailJson(a, cfg, p, now); // 构建详情 JSON 字符串
        List<RewardStatus> rewards = buildRewardStatuses(a, cfg, p, now, playerId); // 各档领奖状态
        return detailRsp(ActivityRetCode.OK, aid, a.getType() == null ? 0 : a.getType(), st, detailJson, rewards); // 成功响应
    } // handleGetActivityDetail 结束

    /**
     * 处理领取活动奖励；rewardIndex==0 表示一键领取所有可领档位。
     */
    @Transactional // 领奖涉及背包与进度写入，需要事务
    public ProtocolMessage handleClaimActivityReward(long playerId, ClaimActivityRewardCsReq req) {
        long aid = req.getActivityId(); // 活动 id
        int rewardIndex = req.getRewardIndex(); // 0 表示一键领取，非 0 表示指定档位 index
        if (playerId <= 0) { // 未选择角色或会话无效
            return claimRsp(RetCode.PLAYER_NOT_SELECTED, aid, rewardIndex, List.of(), 0); // 未选角色
        } // if 玩家无效 结束
        if (playerRepository.findById(playerId).isEmpty()) {
            return claimRsp(RetCode.PLAYER_NOT_FOUND, aid, rewardIndex, List.of(), 0); // 玩家不存在
        } // if 玩家不存在 结束
        Optional<Activity> opt = activityRepository.findById(aid); // 查活动
        if (opt.isEmpty()) { // 数据库中无对应记录
            return claimRsp(ActivityRetCode.ACTIVITY_NOT_FOUND, aid, rewardIndex, List.of(), 0); // 活动不存在
        } // if 活动不存在 结束
        Activity a = opt.get(); // 活动实体
        if (!Boolean.TRUE.equals(a.getOpened())) {
            return claimRsp(ActivityRetCode.ACTIVITY_CLOSED, aid, rewardIndex, List.of(), 0); // 活动关闭
        } // if 未开启 结束
        ActivityConfigPayload cfg = parseConfig(a); // 解析配置
        long now = System.currentTimeMillis(); // 当前时间
        int st = computeStatus(now, cfg); // 活动三态
        if (st == STATUS_NOT_STARTED) {
            return claimRsp(ActivityRetCode.CONDITION_NOT_MET, aid, rewardIndex, List.of(), 0); // 未开始不可领
        } // if 未开始 结束
        PlayerActivityProgress p = progressStore.loadOrCreate(playerId, aid); // 加载进度，后续更新 claimed 集合

        List<RewardTierPayload> toGrant = new ArrayList<>(); // 待发放档位列表
        if (rewardIndex == 0) {
            // 一键领取：遍历所有档位，收集可领且策略允许的
            for (RewardTierPayload tier : cfg.rewardTiers) { // 逐项处理集合元素
                if (canClaimTier(a, cfg, tier, p, now)
                        && policyAllows(a, playerId, tier, p)) {
                    toGrant.add(tier); // 加入待发放
                } // if 可领 结束
            } // for 档位 结束
            if (toGrant.isEmpty()) {
                return claimRsp(ActivityRetCode.CONDITION_NOT_MET, aid, 0, List.of(), 0); // 无可领档位
            } // if 空列表 结束
        } else {
            RewardTierPayload tier = findTier(cfg, rewardIndex); // 查找指定档位
            if (tier == null) { // 前置查询无结果
                return claimRsp(ActivityRetCode.INVALID_REWARD_INDEX, aid, rewardIndex, List.of(), 0); // 档位不存在
            } // if 无效 index 结束
            if (p.claimed.contains(tier.index)) {
                return claimRsp(ActivityRetCode.REWARD_ALREADY_CLAIMED, aid, rewardIndex, List.of(), 0); // 已领取
            } // if 已领 结束
            if (!canClaimTier(a, cfg, tier, p, now) || !policyAllows(a, playerId, tier, p)) {
                return claimRsp(ActivityRetCode.CONDITION_NOT_MET, aid, rewardIndex, List.of(), 0); // 条件不满足
            } // if 不可领 结束
            toGrant.add(tier); // 单档领取
        } // if-else 领取模式 结束

        List<ItemReward> grantList = new ArrayList<>(); // Protobuf 道具列表
        for (RewardTierPayload t : toGrant) { // 逐项处理集合元素
            grantList.add(ItemReward.newBuilder()
                    .setItemId(t.itemId)
                    .setCount(t.count)
                    .build()); // 构建 ItemReward
        } // for 构建 grantList 结束

        StringBuilder tiersKey = new StringBuilder();
        for (RewardTierPayload t : toGrant) {
            if (tiersKey.length() > 0) {
                tiersKey.append(',');
            }
            tiersKey.append(t.index);
        }
        String grantIdemKey = "activity:" + aid + ":tiers:" + tiersKey;
        int bagRc;
        ActivityRewardFulfillmentService fulfillment =
                rewardFulfillmentService == null ? null : rewardFulfillmentService.getIfAvailable();
        if (fulfillment != null) {
            bagRc = fulfillment.fulfill(playerId, aid, grantIdemKey, toGrant, grantList);
        } else {
            bagRc = activityItemGrantPort.grantItemsForActivity(playerId, grantIdemKey, grantList); // 调用背包发道具
        }
        if (bagRc == BagRetCode.BAG_FULL) {
            return claimRsp(ActivityRetCode.BAG_FULL, aid, rewardIndex, List.of(), 0); // 背包满
        } // if 背包满 结束
        if (bagRc != BagRetCode.OK) {
            return claimRsp(ActivityRetCode.CONDITION_NOT_MET, aid, rewardIndex, List.of(), 0); // 背包其他错误
        } // if 非 OK 结束

        for (RewardTierPayload t : toGrant) {
            p.claimed.add(t.index);
            if (t.signDay != null) {
                p.signDays.add(t.signDay);
            }
            if (t.tokenAmount != null && t.tokenAmount > 0) {
                p.tokenAmount += t.tokenAmount;
            }
        }
        progressStore.save(playerId, aid, p); // 持久化到 Redis
        int idxOut = rewardIndex == 0 ? 0 : rewardIndex; // 响应中 rewardIndex：一键领取仍为 0
        activityEventPublisher.publishRewardClaimed(playerId, aid, a.getType() == null ? 0 : a.getType(), idxOut, toGrant.size()); // 发 MQ 事件
        return claimRsp(ActivityRetCode.OK, aid, idxOut, grantList, toGrant.size()); // 返回成功及发放道具
    } // handleClaimActivityReward 结束

    /**
     * 活动开关变更后广播 807 ActivityStatusScNotify。
     */
    public void setActivityOpenedAndNotify(Activity activity) {
        broadcastActivityStatusNotify(activity, parseConfig(activity)); // 解析配置并广播
    } // setActivityOpenedAndNotify 结束

    /**
     * 增加玩家在某活动下的充值进度（首充类联调用）。
     */
    public void addRechargeProgress(long playerId, long activityId, long delta) {
        PlayerActivityProgress p = progressStore.loadOrCreate(playerId, activityId); // 加载或创建进度
        p.rechargeAmount += delta; // 累加充值金额
        progressStore.save(playerId, activityId, p); // 写回 Redis
    } // addRechargeProgress 结束

    /**
     * 战斗胜利次数 +delta（战斗结束 MQ / 内部 API 投影）。
     */
    public void addBattleWinProgress(long playerId, long activityId, int delta) {
        if (delta <= 0) {
            return;
        }
        PlayerActivityProgress p = progressStore.loadOrCreate(playerId, activityId);
        p.battleWins += delta;
        progressStore.save(playerId, activityId, p);
    }

    /**
     * 向全服在线玩家广播活动状态变更通知（807），供 JMX 等调用。
     */
    public void broadcastActivityStatusNotify(Activity activity, ActivityConfigPayload cfg) {
        if (activity == null || cfg == null) { // 前置查询无结果
            return; // 空参数直接返回，避免 NPE
        } // if 空参 结束
        long now = System.currentTimeMillis(); // 当前时间
        int st = computeStatus(now, cfg); // 计算活动三态
        byte[] payload = ActivityStatusScNotify.newBuilder()
                .setActivityId(activity.getId())
                .setType(activity.getType() == null ? 0 : activity.getType())
                .setStatus(st)
                .setStartTime(cfg.startTime)
                .setEndTime(cfg.endTime)
                .build()
                .toByteArray(); // 807 推送：活动 id、类型、三态、起止时间
        playerNotificationPort.broadcastAllOnline(MessageId.ACTIVITY_STATUS_SC_NOTIFY, payload, null); // 全服广播
    } // broadcastActivityStatusNotify 结束

    /**
     * 调用 ActivityPolicy 判断策略是否允许领取指定档位。
     */
    private boolean policyAllows(Activity a, long playerId, RewardTierPayload tier, PlayerActivityProgress p) {
        Map<String, Object> ctx = new HashMap<>(); // ActivityPolicy 上下文 Map
        ctx.put("tier", tier); // 当前档位
        ctx.put("progress", p); // 玩家进度
        return activityPolicy.allowClaimReward(
                a.getType() == null ? 0 : a.getType(),
                a.getId(),
                playerId,
                tier.index,
                ctx); // 调用 Groovy/Java 策略
    } // policyAllows 结束

    /**
     * 按 index 查找奖励档位，未找到返回 null。
     */
    private RewardTierPayload findTier(ActivityConfigPayload cfg, int index) {
        for (RewardTierPayload t : cfg.rewardTiers) { // 逐项处理集合元素
            if (t.index == index) {
                return t; // 匹配到档位
            } // if 匹配 结束
        } // for 结束
        return null; // 未找到
    } // findTier 结束

    /**
     * 判断活动是否应在列表中展示：已开启且（进行中/未开始，或已结束但仍有可领档位）。
     */
    private boolean visibleInList(Activity a, ActivityConfigPayload cfg, long nowMs,
                                  PlayerActivityProgress prog, long playerId) {
        if (!Boolean.TRUE.equals(a.getOpened())) {
            return false; // 未开启不展示
        } // if 未开启 结束
        if (!isPublishedForPlayer(cfg, playerId)) {
            return false;
        }
        int st = computeStatus(nowMs, cfg); // 活动三态
        if (st == STATUS_NOT_STARTED || st == STATUS_IN_PROGRESS) {
            return true; // 未开始或进行中始终可见
        } // if 未结束 结束
        for (RewardTierPayload tier : cfg.rewardTiers) { // 逐项处理集合元素
            if (canClaimTier(a, cfg, tier, prog, nowMs)) {
                return true; // 已结束但仍有可领档位则继续展示
            } // if 可领 结束
        } // for 结束
        return false; // 已结束且无待领档位，隐藏
    } // visibleInList 结束

    /** DRAFT / WhiteList_Only 灰度可见性。 */
    static boolean isPublishedForPlayer(ActivityConfigPayload cfg, long playerId) {
        if (cfg == null) {
            return true;
        }
        String status = cfg.publishStatus == null ? "PUBLISHED" : cfg.publishStatus.trim().toUpperCase();
        if ("DRAFT".equals(status)) {
            return cfg.whiteListOnly && inWhiteList(cfg, playerId);
        }
        if (cfg.whiteListOnly) {
            return inWhiteList(cfg, playerId);
        }
        return true;
    }

    private static boolean inWhiteList(ActivityConfigPayload cfg, long playerId) {
        if (cfg.whiteListPlayerIds == null || cfg.whiteListPlayerIds.isEmpty()) {
            return false;
        }
        return cfg.whiteListPlayerIds.contains(playerId);
    }

    /**
     * 构建各档位的 RewardStatus（已领/可领标志）。
     */
    private List<RewardStatus> buildRewardStatuses(
            Activity a, ActivityConfigPayload cfg, PlayerActivityProgress p, long nowMs, long playerId) {
        List<RewardStatus> list = new ArrayList<>(); // 输出列表
        for (RewardTierPayload tier : cfg.rewardTiers) { // 逐项处理集合元素
            boolean claimed = p.claimed.contains(tier.index); // 是否已领
            boolean can = !claimed && canClaimTier(a, cfg, tier, p, nowMs) && policyAllows(a, playerId, tier, p); // 是否可领
            list.add(RewardStatus.newBuilder()
                    .setRewardIndex(tier.index)
                    .setClaimed(claimed)
                    .setCanClaim(can)
                    .build()); // 组装 RewardStatus
        } // for 结束
        return list; // 返回档位状态列表
    } // buildRewardStatuses 结束

    /**
     * 解析 activity.data JSON 为 ActivityConfigPayload；空或非法 JSON 返回空对象。
     */
    private ActivityConfigPayload parseConfig(Activity a) {
        if (a.getData() == null || a.getData().isBlank()) { // 前置查询无结果
            return new ActivityConfigPayload(); // 无配置返回默认
        } // if 无 data 结束
        try {
            return objectMapper.readValue(a.getData(), ActivityConfigPayload.class); // Jackson 反序列化
        } catch (Exception e) {
            return new ActivityConfigPayload(); // 脏 JSON 降级为空配置
        } // catch 结束
    } // parseConfig 结束

    /**
     * 根据当前时间与 start/end 计算活动三态。
     */
    private int computeStatus(long nowMs, ActivityConfigPayload cfg) {
        if (nowMs < cfg.startTime) {
            return STATUS_NOT_STARTED; // 未开始
        } // if 早于开始 结束
        if (nowMs > cfg.endTime) {
            return STATUS_ENDED; // 已结束
        } // if 晚于结束 结束
        return STATUS_IN_PROGRESS; // 进行中
    } // computeStatus 结束

    /**
     * 计算活动内第几天（1~7），未开始返回 0。
     */
    private int activityDayIndex(long nowMs, long startMs) {
        if (nowMs < startMs) {
            return 0; // 活动未开始
        } // if 未开始 结束
        return (int) Math.min(7L, (nowMs - startMs) / DAY_MS + 1); // 第几天，上限 7
    } // activityDayIndex 结束

    /**
     * 判断档位达标条件是否满足（首充金额或签到天数）。
     */
    private boolean tierConditionMet(Activity a, ActivityConfigPayload cfg, RewardTierPayload tier, PlayerActivityProgress p, long nowMs) {
        if (!ActivityConditionEvaluator.allMet(cfg.conditions, p)) {
            return false;
        }
        if (tier.targetRecharge != null) {
            return p.rechargeAmount >= tier.targetRecharge;
        }
        if (tier.signDay != null) {
            int day = activityDayIndex(nowMs, cfg.startTime);
            return day >= tier.signDay;
        }
        if (tier.requiredStage != null) {
            return p.currentStage >= tier.requiredStage;
        }
        return true;
    }

    /**
     * 综合三态、已领状态与达标条件，判断档位是否可领取。
     */
    private boolean canClaimTier(Activity a, ActivityConfigPayload cfg, RewardTierPayload tier, PlayerActivityProgress p, long nowMs) {
        int st = computeStatus(nowMs, cfg); // 活动三态
        if (st == STATUS_NOT_STARTED) {
            return false; // 未开始不可领
        } // if 未开始 结束
        if (p.claimed.contains(tier.index)) {
            return false; // 已领取
        } // if 已领 结束
        return tierConditionMet(a, cfg, tier, p, nowMs); // 条件是否达标
    } // canClaimTier 结束

    /**
     * 构建详情页 detailData JSON（充值额、目标额、活动天数、已签天等）。
     */
    private String buildDetailJson(Activity a, ActivityConfigPayload cfg, PlayerActivityProgress p, long nowMs) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("recharge_amount", p.rechargeAmount);
            int target = cfg.rewardTiers.stream()
                    .map(t -> t.targetRecharge)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(0);
            root.put("target_amount", target);
            root.put("activity_day", activityDayIndex(nowMs, cfg.startTime));
            root.put("token_amount", p.tokenAmount);
            root.put("battle_wins", p.battleWins);
            root.put("current_stage", p.currentStage);
            root.set("signed_days", objectMapper.valueToTree(p.signDays));
            root.put("type", a.getType() == null ? 0 : a.getType());
            root.put("config_version", cfg.configVersion);
            root.put("description", cfg.description != null ? cfg.description : "");
            root.put("gameplay", cfg.gameplay != null ? cfg.gameplay : "");
            root.put("rules", cfg.rules != null ? cfg.rules : "");
            root.put("reward_method", cfg.rewardMethod);
            root.put("shop_id", cfg.shopId);
            if (cfg.token != null) {
                root.set("token", objectMapper.valueToTree(cfg.token));
            }
            if (cfg.costLimit != null) {
                root.set("cost_limit", objectMapper.valueToTree(cfg.costLimit));
            }
            if (cfg.stages != null && !cfg.stages.isEmpty()) {
                root.set("stages", objectMapper.valueToTree(cfg.stages));
            }
            if (cfg.shopProducts != null && !cfg.shopProducts.isEmpty()) {
                root.set("shop_products", objectMapper.valueToTree(cfg.shopProducts));
            }
            if (cfg.uiResources != null) {
                root.set("ui_resources", objectMapper.valueToTree(cfg.uiResources));
            }
            if (cfg.displayText != null) {
                root.set("display_text", objectMapper.valueToTree(cfg.displayText));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 组装活动列表成功/失败响应 ProtocolMessage。
     */
    private ProtocolMessage listRsp(int retcode, List<ActivityBriefInfo> activities) {
        var b = GetActivityListScRsp.newBuilder()
                .setRetcode(retcode)
                .setLoading(false); // 非 loading 响应
        activities.forEach(b::addActivities); // 追加各活动简要信息
        return new ProtocolMessage(MessageId.GET_ACTIVITY_LIST_SC_RSP, b.build().toByteArray()); // 802 响应
    } // listRsp 结束

    /**
     * 组装列表 loading 占位响应（retcode=OK 且 loading=true）。
     */
    private ProtocolMessage listLoadingRsp() {
        var b = GetActivityListScRsp.newBuilder()
                .setRetcode(ActivityRetCode.OK)
                .setLoading(true); // 数据预热中
        return new ProtocolMessage(MessageId.GET_ACTIVITY_LIST_SC_RSP, b.build().toByteArray()); // 802 loading
    } // listLoadingRsp 结束

    /**
     * 组装活动详情响应，含 detailData 与 reward 状态列表。
     */
    private ProtocolMessage detailRsp(int retcode, long activityId, int type, int status, String detail, List<RewardStatus> rewards) {
        var b = GetActivityDetailScRsp.newBuilder()
                .setRetcode(retcode)
                .setActivityId(activityId)
                .setType(type)
                .setStatus(status)
                .setDetailData(detail != null ? detail : "")
                .setLoading(false); // 非 loading
        rewards.forEach(b::addRewardStatus); // 各档状态供 UI 展示
        return new ProtocolMessage(MessageId.GET_ACTIVITY_DETAIL_SC_RSP, b.build().toByteArray()); // 807 响应
    } // detailRsp 结束

    /**
     * 组装详情 loading 占位响应（空 detail + loading=true）。
     */
    private ProtocolMessage detailLoadingRsp(long activityId) {
        var b = GetActivityDetailScRsp.newBuilder()
                .setRetcode(ActivityRetCode.OK)
                .setActivityId(activityId)
                .setType(0)
                .setStatus(STATUS_NOT_STARTED) // 占位状态
                .setDetailData("{}") // 空 JSON 占位
                .setLoading(true); // 预热中
        return new ProtocolMessage(MessageId.GET_ACTIVITY_DETAIL_SC_RSP, b.build().toByteArray()); // 807 loading
    } // detailLoadingRsp 结束

    /**
     * 组装领奖响应，含 retcode、发放道具与 claimedCount。
     */
    private ProtocolMessage claimRsp(int retcode, long activityId, int rewardIndex, List<ItemReward> items, int claimedCount) {
        var b = ClaimActivityRewardScRsp.newBuilder()
                .setRetcode(retcode)
                .setActivityId(activityId)
                .setRewardIndex(rewardIndex)
                .setClaimedCount(claimedCount); // 本次领取档位数
        items.forEach(b::addRewardItems); // 本次发放的道具列表
        return new ProtocolMessage(MessageId.CLAIM_ACTIVITY_REWARD_SC_RSP, b.build().toByteArray()); // 804 响应
    } // claimRsp 结束
} // class ActivityService 结束