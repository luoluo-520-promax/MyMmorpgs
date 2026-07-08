/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerDataAsyncPreloadService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：选角后异步预加载背包/技能/活动，可选推送 DataReady 通知到客户端。
 * 4) 变更建议：game.player-preload.enabled 与 BagService 预加载门控需配置一致。
 * 5) 风险提示：CompletableFuture.runAsync 使用公共线程池，高并发选角需评估线程与 DB 压力。
 */
package cn.itcast.demo.mymmorpg.service; // 选角后异步预加载背包/技能/活动，可选推送 DataReady 通知到客户端

import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataPreloadPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId; // BAG/SKILL/ACTIVITY_DATA_READY_SC_NOTIFY 消息号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // loadXxxNow 返回的统一 msgId+payload 封装
import cn.itcast.demo.mymmorpg.protocol.protobuf.ActivityDataReadyScNotify; // 活动预加载完成服务端推送（无 retcode）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BagDataReadyScNotify; // 背包预加载完成推送
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListScRsp; // 复用活动列表 ScRsp 字段组装 Notify
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetBagInfoScRsp; // 复用背包 ScRsp 字段组装 Notify
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetPlayerSkillsScRsp; // 复用技能列表 ScRsp 字段组装 Notify
import cn.itcast.demo.mymmorpg.protocol.protobuf.SkillDataReadyScNotify; // 技能预加载完成推送
import org.slf4j.Logger; // 预加载失败 warn 日志
import org.slf4j.LoggerFactory; // Logger 工厂
import org.springframework.beans.factory.annotation.Value; // game.player-preload.* 配置注入
import org.springframework.stereotype.Service; // 声明业务 Bean，选角 Handler 调用 onPlayerSelected 触发预加载

import java.util.concurrent.CompletableFuture; // 后台线程执行 doLoad，不阻塞选角响应

/**
 * 玩家数据异步预加载协调器：选角成功后后台加载背包/技能/活动，减少客户端首屏等待。
 * 可选 pushEnabled 时主动推送 *DataReadyScNotify，客户端无需再发 GetXxxCsReq。
 */
@Service // 选角后协调 BAG/SKILL/ACTIVITY 异步预加载，可选推送 DataReady Notify
public class PlayerDataAsyncPreloadService implements PlayerDataPreloadPort {

    private static final Logger log = LoggerFactory.getLogger(PlayerDataAsyncPreloadService.class); // 预加载失败 warn

    /** 加载状态与 tryStart 去重，防止重复提交异步任务 */
    private final PlayerDataLoadStateService stateService; // 加载状态与 tryStart 去重，防止重复提交异步任务

    /** 背包业务，loadBagNow 强制查库/缓存并返回 GetBagInfoScRsp */
    private final BagService bagService; // 背包业务，loadBagNow 强制查库/缓存并返回 GetBagInfoScRsp

    /** 技能业务，loadSkillsNow 查 player_skill + skill_config */
    private final SkillService skillService; // 技能业务，loadSkillsNow 查 player_skill + skill_config

    /** 活动业务，loadActivityListNow 查活动配置与玩家进度 */
    private final ActivityService activityService; // 活动业务，loadActivityListNow 查活动配置与玩家进度

    /** 下行推送，将 DataReady Notify 经 Netty/WS 发给在线客户端 */
    private final PlayerPushRegistry playerPushRegistry; // 下行推送，将 DataReady Notify 经 Netty/WS 发给在线客户端

    /** game.player-preload.enabled：false 时整个预加载链路短路 */
    private final boolean enabled; // game.player-preload.enabled：false 时整个预加载链路短路

    /** game.player-preload.push-enabled：true 时加载完成后主动推送 Notify */
    private final boolean pushEnabled; // game.player-preload.push-enabled：true 时加载完成后主动推送 Notify

    /** game.player-preload.dedupe-window-ms：tryStart 去重窗口，防连点选角重复 runAsync */
    private final long dedupeWindowMs; // game.player-preload.dedupe-window-ms：tryStart 去重窗口，防连点选角重复 runAsync

    /**
     * 构造器：状态服务、三类业务服务、推送注册表与预加载配置。
     */
    public PlayerDataAsyncPreloadService( // 构造器：状态服务、三类业务服务、推送注册表与预加载配置
            PlayerDataLoadStateService stateService, // ready/loading 门控与 dedupe
            BagService bagService, // loadBagNow 查背包
            SkillService skillService, // loadSkillsNow 查技能
            ActivityService activityService, // loadActivityListNow 查活动
            PlayerPushRegistry playerPushRegistry, // DataReady Notify 下行
            @Value("${game.player-preload.enabled:false}") boolean enabled, // 选角后是否后台预加载 BAG/SKILL/ACTIVITY
            @Value("${game.player-preload.push-enabled:true}") boolean pushEnabled, // 预加载完成后是否主动推送 *DataReadyScNotify
            @Value("${game.player-preload.dedupe-window-ms:2000}") long dedupeWindowMs) { // tryStart 去重窗口毫秒，防连点重复提交
        this.stateService = stateService; // tryStart/markReady/markFailed
        this.bagService = bagService; // preloadBag 数据源
        this.skillService = skillService; // preloadSkill 数据源
        this.activityService = activityService; // preloadActivity 数据源
        this.playerPushRegistry = playerPushRegistry; // BAG/SKILL/ACTIVITY_DATA_READY 推送
        this.enabled = enabled; // false 时 trigger 短路
        this.pushEnabled = pushEnabled; // false 时仅 markReady 不推送
        this.dedupeWindowMs = dedupeWindowMs; // dedupe 窗口传给 tryStart
    }

    /**
     * 选角成功入口：重置 BAG/SKILL/ACTIVITY 加载状态并触发三类后台预加载。
     *
     * @param playerId 已选角色 ID
     */
    public void onPlayerSelected(long playerId) { // 选角成功入口：重置 BAG/SKILL/ACTIVITY 加载状态并触发三类后台预加载
        if (!enabled || playerId <= 0) { // 预加载关闭或 playerId 非法
            return; // 不触发后台加载
        }
        stateService.resetForPlayer(playerId); // 清 ready/loading，允许新选角周期
        triggerAll(playerId); // 依次 trigger BAG/SKILL/ACTIVITY
    }

    /**
     * 依次触发背包、技能、活动三类预加载（各走独立 tryStart 去重）。
     */
    public void triggerAll(long playerId) { // 依次触发背包、技能、活动三类预加载（各走独立 tryStart 去重）
        trigger(playerId, PlayerDataLoadPort.DataType.BAG); // 背包预加载
        trigger(playerId, PlayerDataLoadPort.DataType.SKILL); // 技能预加载
        trigger(playerId, PlayerDataLoadPort.DataType.ACTIVITY); // 活动预加载
    }

    /**
     * 触发单一类型预加载：tryStart 成功后提交 CompletableFuture.runAsync。
     *
     * @param playerId 角色 ID
     * @param type     BAG / SKILL / ACTIVITY
     */
    public void trigger(long playerId, PlayerDataLoadPort.DataType type) { // 触发单一类型预加载：tryStart 成功后提交 CompletableFuture.runAsync
        if (!enabled || playerId <= 0) { // 预加载关闭或 playerId 非法
            return; // 不提交异步任务
        }
        if (!stateService.tryStart(playerId, type, dedupeWindowMs)) { // 已就绪/进行中/dedupe 窗口内
            return; // 跳过重复提交
        }
        CompletableFuture.runAsync(() -> doLoad(playerId, type)); // ForkJoinPool.commonPool() 后台 IO
    }

    /**
     * 供 BagService 等查询预加载总开关，与 game.player-preload.enabled 一致。
     */
    public boolean isEnabled() { // 供 BagService 等查询预加载总开关，与 game.player-preload.enabled 一致
        return enabled; // 与 YAML game.player-preload.enabled 一致
    }

    /**
     * 实际加载逻辑：按类型调用 preloadXxx，成功 markReady，异常 markFailed。
     */
    private void doLoad(long playerId, PlayerDataLoadPort.DataType type) { // 实际加载逻辑：按类型调用 preloadXxx，成功 markReady，异常 markFailed
        try { // 捕获预加载异常，失败时 markFailed 不阻断选角主流程
            switch (type) { // 按 DataType 分发至 preloadBag/preloadSkill/preloadActivity
                case BAG -> preloadBag(playerId); // 查库组背包 ScRsp
                case SKILL -> preloadSkill(playerId); // 查 player_skill 列表
                case ACTIVITY -> preloadActivity(playerId); // 查活动列表与进度
            }
            stateService.markReady(playerId, type); // loading=false, ready=true
        } catch (Exception e) { // DB/Protobuf/推送任一环节失败均走 markFailed
            stateService.markFailed(playerId, type); // loading=false, ready=false
            log.warn("player preload failed playerId={} type={} err={}", playerId, type, e.toString()); // 失败不阻断，同步 GetXxx 可兜底
        }
    }

    /**
     * 预加载背包：loadBagNow 查库，可选推送 BAG_DATA_READY_SC_NOTIFY。
     */
    private void preloadBag(long playerId) throws Exception { // 预加载背包：loadBagNow 查库，可选推送 BAG_DATA_READY_SC_NOTIFY
        ProtocolMessage message = bagService.loadBagNow(playerId); // 强制查库/缓存
        if (!pushEnabled) { // 不主动推送
            return; // 仅 markReady，客户端自行 GetBagInfo
        }
        GetBagInfoScRsp rsp = GetBagInfoScRsp.parseFrom(message.payload()); // 解码 GetBagInfoScRsp
        byte[] payload = BagDataReadyScNotify.newBuilder() // 预加载背包：loadBagNow 查库，可选推送 BAG_DATA_READY_SC_NOTIFY
                .setCapacity(rsp.getCapacity()) // 背包容量
                .setUsedSlots(rsp.getUsedSlots()) // 已用格数
                .addAllItems(rsp.getItemsList()) // 道具列表
                .build() // 完成 BagDataReadyScNotify Protobuf
                .toByteArray(); // DataReady Notify 无 retcode
        playerPushRegistry.send(playerId, MessageId.BAG_DATA_READY_SC_NOTIFY, payload); // 主动推送背包 UI 数据
    }

    /**
     * 预加载技能列表：loadSkillsNow 查 player_skill，可选推送 SKILL_DATA_READY_SC_NOTIFY。
     */
    private void preloadSkill(long playerId) throws Exception { // 预加载技能列表：loadSkillsNow 查 player_skill，可选推送 SKILL_DATA_READY_SC_NOTIFY
        ProtocolMessage message = skillService.loadSkillsNow(playerId); // 查 player_skill + skill_config
        if (!pushEnabled) { // 不主动推送
            return; // 仅 markReady
        }
        GetPlayerSkillsScRsp rsp = GetPlayerSkillsScRsp.parseFrom(message.payload()); // 解码技能列表 ScRsp
        byte[] payload = SkillDataReadyScNotify.newBuilder() // 预加载技能列表：loadSkillsNow 查 player_skill，可选推送 SKILL_DATA_READY_SC_NOTIFY
                .addAllSkills(rsp.getSkillsList()) // 已学技能列表
                .build() // 完成 SkillDataReadyScNotify Protobuf
                .toByteArray(); // 预加载技能列表：loadSkillsNow 查 player_skill，可选推送 SKILL_DATA_READY_SC_NOTIFY
        playerPushRegistry.send(playerId, MessageId.SKILL_DATA_READY_SC_NOTIFY, payload); // 主动推送技能 UI 数据
    }

    /**
     * 预加载活动列表：loadActivityListNow 查配置与进度，可选推送 ACTIVITY_DATA_READY_SC_NOTIFY。
     */
    private void preloadActivity(long playerId) throws Exception { // 预加载活动列表：loadActivityListNow 查配置与进度，可选推送 ACTIVITY_DATA_READY_SC_NOTIFY
        ProtocolMessage message = activityService.loadActivityListNow(playerId); // 查活动配置与玩家进度
        if (!pushEnabled) { // 不主动推送
            return; // 仅 markReady
        }
        GetActivityListScRsp rsp = GetActivityListScRsp.parseFrom(message.payload()); // 解码活动列表 ScRsp
        byte[] payload = ActivityDataReadyScNotify.newBuilder() // 预加载活动列表：loadActivityListNow 查配置与进度，可选推送 ACTIVITY_DATA_READY_SC_NOTIFY
                .addAllActivities(rsp.getActivitiesList()) // 进行中活动列表
                .build() // 完成 ActivityDataReadyScNotify Protobuf
                .toByteArray(); // 预加载活动列表：loadActivityListNow 查配置与进度，可选推送 ACTIVITY_DATA_READY_SC_NOTIFY
        playerPushRegistry.send(playerId, MessageId.ACTIVITY_DATA_READY_SC_NOTIFY, payload); // 主动推送活动 UI 数据
    }
}
